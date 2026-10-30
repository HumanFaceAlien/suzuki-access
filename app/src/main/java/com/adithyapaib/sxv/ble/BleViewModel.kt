package com.adithyapaib.sxv.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class BleConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class BleViewModel(application: android.app.Application) : AndroidViewModel(application) {
    private companion object {
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        val SERVICE_UUID: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
        val WRITE_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")
        val NOTIFICATION_CHARACTERISTIC_UUID: UUID = UUID.fromString("0000fff2-0000-1000-8000-00805f9b34fb")
        const val TARGET_DEVICE_ADDRESS = "XX:XX:XX:XX:XX:XX"
        const val LOG_LIMIT = 200
        const val VERBOSE_LOG_LIMIT = 5_000
    }

    data class ClusterChannelMap(
        val service: BluetoothGattService,
        val notifyCharacteristic: BluetoothGattCharacteristic,
        val writeCharacteristic: BluetoothGattCharacteristic
    )

    private val _connectionState = MutableStateFlow(BleConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BleConnectionState> = _connectionState.asStateFlow()

    private val _telemetry = MutableStateFlow<VStromTelemetry?>(null)
    val telemetry: StateFlow<VStromTelemetry?> = _telemetry.asStateFlow()

    private val notifications = MutableSharedFlow<ByteArray>(extraBufferCapacity = 16)

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    /** When on, the link logs the GATT table, every read, RSSI, link parameters and a per-field breakdown of each frame. */
    private val _verbose = MutableStateFlow(false)
    val verbose: StateFlow<Boolean> = _verbose.asStateFlow()

    private var lastFrame: ByteArray? = null
    private var lastFrameAtMs = 0L

    private val _displayInput = MutableStateFlow("")
    val displayInput: StateFlow<String> = _displayInput.asStateFlow()

    private val _displaySecondaryInput = MutableStateFlow("")
    val displaySecondaryInput: StateFlow<String> = _displaySecondaryInput.asStateFlow()

    private val _isSendingDisplayText = MutableStateFlow(false)
    val isSendingDisplayText: StateFlow<Boolean> = _isSendingDisplayText.asStateFlow()

    /** Outcome of one dot-matrix write attempt; [message] is the text that was composed for the cluster. */
    data class DisplaySendResult(val message: String, val success: Boolean)

    private val _sendResults = MutableSharedFlow<DisplaySendResult>(extraBufferCapacity = 4)
    val sendResults: SharedFlow<DisplaySendResult> = _sendResults.asSharedFlow()

    /** Wall-clock time of the last valid telemetry frame; 0 until the first one arrives. */
    private val _lastTelemetryAtMs = MutableStateFlow(0L)
    val lastTelemetryAtMs: StateFlow<Long> = _lastTelemetryAtMs.asStateFlow()

    private var bleManager: BleManager? = null
    private var clusterChannelMap: ClusterChannelMap? = null

    init {
        viewModelScope.launch {
            notifications.collect { bytes ->
                val parsed = SuzukiPacketParser.parse(bytes)
                if (parsed == null) {
                    trace("Packet rejected: expected a 30-byte A5 frame with valid mileage ASCII fields")
                    _errors.emit("Invalid telemetry packet")
                } else {
                    _telemetry.value = parsed
                    _lastTelemetryAtMs.value = System.currentTimeMillis()
                    bleManager?.onTelemetryFrame()
                    val fuelSummary = if (parsed.isFuelValid) {
                        "${parsed.fuelBars}/6 bars ${parsed.fuelLiters}L"
                    } else {
                        "sensor syncing"
                    }
                    trace("Telemetry parsed: odometer=${parsed.odometer}, tripA=${parsed.tripA}, tripB=${parsed.tripB}, fuel=$fuelSummary")
                }
            }
        }
    }

    /** Call this from the GATT callback when a notification characteristic changes. */
    fun onNotification(bytes: ByteArray) {
        trace("RX ${bytes.size} bytes: ${bytes.toHexString()}")
        if (_verbose.value) traceFrameBreakdown(bytes)
        notifications.tryEmit(bytes.copyOf())
    }

    fun setVerbose(enabled: Boolean) {
        if (_verbose.value == enabled) return
        _verbose.value = enabled
        trace("Verbose BLE logging ${if (enabled) "ON" else "OFF"}")
        if (enabled) bleManager?.onVerboseEnabled()
    }

    /** Logs [message] only while verbose logging is on. */
    fun vtrace(message: String) {
        if (_verbose.value) trace("[v] $message")
    }

    /** Plain-text dump of the whole retained log, for sharing off the device. */
    fun logText(): String = _logs.value.joinToString("\n")

    private fun traceFrameBreakdown(bytes: ByteArray) {
        val now = android.os.SystemClock.elapsedRealtime()
        val previous = lastFrame
        val gap = if (lastFrameAtMs == 0L) "first" else "+${now - lastFrameAtMs}ms"
        lastFrame = bytes.copyOf()
        lastFrameAtMs = now
        if (bytes.size != 30) {
            vtrace("FRAME len=${bytes.size} (expected 30) $gap")
            return
        }
        val changed = if (previous != null && previous.size == bytes.size) {
            bytes.indices.filter { bytes[it] != previous[it] }
        } else {
            emptyList()
        }
        fun u(index: Int) = bytes[index].toInt() and 0xFF
        fun ascii(from: Int, length: Int) = String(bytes, from, length, Charsets.ISO_8859_1)
        val calculated = (1..27).sumOf { u(it) } and 0xFF
        vtrace(
            "FRAME $gap hdr=%02X type='%s' speed=%s odo=%s tripA=%s tripB=%s sep=%s fuel=%s b25=%d(0x%02X) b26-27=%02X%02X ck=%02X calc=%02X%s end=%02X changed=%s"
                .format(
                    u(0), ascii(1, 1), ascii(2, 3), ascii(5, 6), ascii(11, 6), ascii(17, 6),
                    ascii(23, 1), ascii(24, 1), u(25), u(25), u(26), u(27),
                    u(28), calculated, if (calculated == u(28)) "" else " CHECKSUM-MISMATCH", u(29), changed
                )
        )
    }

    fun attachBle(context: Context) {
        if (bleManager == null) bleManager = BleManager(context.applicationContext, this)
    }

    fun connect() {
        bleManager?.connect() ?: onConnectionError("BLE manager is not attached")
    }

    fun disconnect() {
        bleManager?.disconnect()
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    fun updateDisplayInput(value: String) {
        _displayInput.value = value
    }

    fun updateDisplaySecondaryInput(value: String) {
        _displaySecondaryInput.value = value
    }

    fun trace(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
            .format(java.util.Date())
        Log.d("SXV-BLE", message)
        _logs.value = (_logs.value + "$timestamp  $message").takeLast(if (_verbose.value) VERBOSE_LOG_LIMIT else LOG_LIMIT)
    }

    fun onConnecting() {
        trace("Connecting to $TARGET_DEVICE_ADDRESS")
        _connectionState.value = BleConnectionState.CONNECTING
    }

    fun onConnected() {
        trace("GATT connected")
        _connectionState.value = BleConnectionState.CONNECTED
    }

    fun onDisconnected() {
        clusterChannelMap = null
        trace("GATT disconnected")
        _connectionState.value = BleConnectionState.DISCONNECTED
    }

    fun onConnectionError(message: String) {
        clusterChannelMap = null
        _isSendingDisplayText.value = false
        trace("ERROR: $message")
        _connectionState.value = BleConnectionState.ERROR
        _errors.tryEmit(message)
    }

    fun currentClusterChannelMap(): ClusterChannelMap? = clusterChannelMap

    fun sendDisplayText() {
        val message = _displayInput.value
        val secondaryMessage = _displaySecondaryInput.value
        if (message.isBlank() && secondaryMessage.isBlank()) {
            trace("Display send ignored: message is empty")
            _errors.tryEmit("Enter at least one dot-matrix field before sending")
            return
        }

        val manager = bleManager
        if (manager == null) {
            onConnectionError("BLE manager is not attached")
            return
        }

        if (_connectionState.value != BleConnectionState.CONNECTED) {
            trace("Display send ignored: cluster is not connected")
            _errors.tryEmit("Connect to the bike before sending display text")
            return
        }

        _isSendingDisplayText.value = true
        val sent = manager.sendDisplayText(message, secondaryMessage)
        _isSendingDisplayText.value = false
        val composed = listOf(message.trim(), secondaryMessage.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" | ")
            .take(SuzukiDisplaySender.MAX_VISIBLE_TEXT_LENGTH)
        _sendResults.tryEmit(DisplaySendResult(composed, sent))
        if (sent) {
            trace("Dot-matrix message queued successfully")
        } else {
            _errors.tryEmit("Display write failed")
        }
    }

    fun resolveClusterChannelMap(gatt: BluetoothGatt): ClusterChannelMap? {
        val service = gatt.services.firstOrNull { it.uuid == SERVICE_UUID }
        if (service == null) {
            trace("Required service missing: $SERVICE_UUID")
            return null
        }

        val notifyCharacteristic = service.characteristics.firstOrNull {
            it.uuid == NOTIFICATION_CHARACTERISTIC_UUID
        }
        val writeCharacteristic = service.characteristics.firstOrNull {
            it.uuid == WRITE_CHARACTERISTIC_UUID
        }

        if (notifyCharacteristic == null || writeCharacteristic == null) {
            trace(
                "Required characteristics missing: notify=$NOTIFICATION_CHARACTERISTIC_UUID write=$WRITE_CHARACTERISTIC_UUID"
            )
            return null
        }

        return ClusterChannelMap(
            service = service,
            notifyCharacteristic = notifyCharacteristic,
            writeCharacteristic = writeCharacteristic
        ).also { resolved ->
            clusterChannelMap = resolved
            trace(
                "Resolved BLE UUID map: service=${resolved.service.uuid}, notify=${resolved.notifyCharacteristic.uuid}, write=${resolved.writeCharacteristic.uuid}"
            )
        }
    }

    fun isNotificationCharacteristic(characteristic: BluetoothGattCharacteristic): Boolean =
        clusterChannelMap?.notifyCharacteristic?.uuid == characteristic.uuid ||
            characteristic.uuid == NOTIFICATION_CHARACTERISTIC_UUID

    /** Enables notifications and writes the standard Client Characteristic Configuration value. */
    @SuppressLint("MissingPermission")
    fun enableNotifications(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        val localEnabled = gatt.setCharacteristicNotification(characteristic, true)
        val cccd = characteristic.getDescriptor(CCCD_UUID)
        return if (cccd == null) {
            onConnectionError("Notification characteristic has no CCCD")
            false
        } else {
            val status = if (localEnabled) {
                gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                BluetoothGatt.GATT_FAILURE
            }
            trace("CCCD enable requested: local=$localEnabled status=$status")
            localEnabled && status == BluetoothGatt.GATT_SUCCESS
        }
    }

    override fun onCleared() {
        clusterChannelMap = null
        bleManager?.close()
        bleManager = null
        super.onCleared()
    }

    private fun ByteArray.toHexString(): String = joinToString("-") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }

}



