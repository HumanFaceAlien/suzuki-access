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

    private val _displayInput = MutableStateFlow("")
    val displayInput: StateFlow<String> = _displayInput.asStateFlow()

    private val _displaySecondaryInput = MutableStateFlow("")
    val displaySecondaryInput: StateFlow<String> = _displaySecondaryInput.asStateFlow()

    private val _isSendingDisplayText = MutableStateFlow(false)
    val isSendingDisplayText: StateFlow<Boolean> = _isSendingDisplayText.asStateFlow()

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
        notifications.tryEmit(bytes.copyOf())
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
        _logs.value = (_logs.value + "$timestamp  $message").takeLast(200)
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



