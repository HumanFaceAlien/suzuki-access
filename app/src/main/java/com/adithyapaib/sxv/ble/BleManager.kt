package com.adithyapaib.sxv.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID

/**
 * Owns scanning, GATT discovery, and notification subscription for the cluster.
 *
 * Mirrors the official Suzuki app's connection sequence, which is what makes
 * the cluster usable without first pairing through the stock app:
 *  1. Scan by advertised name (SA/SB/SC prefix, 12 chars) instead of relying
 *     on a hard-coded MAC that may have changed.
 *  2. Connect with autoConnect=true (the stock app uses autoConnect=true).
 *  3. Request an MTU large enough for the 30-byte cluster frame (stock app's
 *     FastBle defaults to 23, which truncates the frame; we request 247).
 *  4. After service discovery, perform the stock app's "wake" read of the
 *     4th GATT service's 2nd characteristic (MyBleService.a()), which is
 *     what activates the cluster's notification stream.
 *  5. Enable FFF2 notifications.
 */
class BleManager(
    context: Context,
    private val viewModel: BleViewModel
) {
    private val appContext = context.applicationContext
    private val bluetoothManager = appContext.getSystemService(android.bluetooth.BluetoothManager::class.java)
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val scanner: android.bluetooth.le.BluetoothLeScanner? = adapter?.bluetoothLeScanner
    private val handler = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null
    private var shouldReconnect = false
    private var retryAttempt = 0
    private var scanning = false
    private var servicesDiscoveryStarted = false
    private var mtuCallbackReceived = false

    @SuppressLint("MissingPermission")
    fun connect() {
        val bluetoothAdapter = adapter
        when {
            bluetoothAdapter == null -> viewModel.onConnectionError("Bluetooth is not available")
            !bluetoothAdapter.isEnabled -> viewModel.onConnectionError("Bluetooth is turned off")
            gatt != null -> viewModel.trace("Connect ignored: a GATT session is already active")
            else -> {
                shouldReconnect = true
                retryAttempt = 0
                disconnect()
                shouldReconnect = true
                viewModel.onConnecting()
                startScan()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (scanner == null) {
            viewModel.trace("No LE scanner; falling back to direct connect by MAC")
            connectDirect()
            return
        }
        scanning = true
        viewModel.trace("Scanning for cluster (name contains SA/SB/SC, 12 chars)...")
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (se: SecurityException) {
            scanning = false
            viewModel.trace("Scan permission denied: ${se.message}; falling back to direct connect")
            connectDirect()
            return
        } catch (ise: IllegalStateException) {
            scanning = false
            viewModel.trace("Scanner unavailable: ${ise.message}; falling back to direct connect")
            connectDirect()
            return
        }
        handler.postDelayed({
            if (scanning) {
                viewModel.trace("Scan timed out; falling back to direct connect by MAC")
                stopScan()
                connectDirect()
            }
        }, SCAN_TIMEOUT_MS)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectDirect() {
        try {
            val device = adapter?.getRemoteDevice(TARGET_DEVICE_ADDRESS)
            if (device == null) {
                failConnection("Bluetooth adapter unavailable")
                return
            }
            viewModel.trace("Connecting directly to ${device.address} (${device.name}) autoConnect=true")
            gatt = device.connectGatt(appContext, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) failConnection("connectGatt returned null")
        } catch (exception: IllegalArgumentException) {
            failConnection("Invalid target Bluetooth address: ${exception.message}")
        } catch (securityException: SecurityException) {
            failConnection("Bluetooth permission denied: ${securityException.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        try {
            viewModel.trace("Connecting to ${device.address} (${device.name}) autoConnect=true")
            gatt = device.connectGatt(appContext, true, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) failConnection("connectGatt returned null")
        } catch (securityException: SecurityException) {
            failConnection("Bluetooth permission denied: ${securityException.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!scanning) return
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: ""
            if (name.length == 12 && (name.contains("SA") || name.contains("SB") || name.contains("SC"))) {
                viewModel.trace("Found cluster: $name @ ${device.address}")
                stopScan()
                handler.removeCallbacksAndMessages(null)
                connectToDevice(device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            if (!scanning) return
            scanning = false
            viewModel.trace("Scan failed (errorCode=$errorCode); falling back to direct connect")
            connectDirect()
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        shouldReconnect = false
        handler.removeCallbacksAndMessages(null)
        stopScan()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    fun close() {
        disconnect()
    }

    @SuppressLint("MissingPermission")
    fun sendDisplayText(text: String): Boolean {
        return sendDisplayText(text = text, secondaryText = null)
    }

    @SuppressLint("MissingPermission")
    fun sendDisplayText(text: String, secondaryText: String?): Boolean {
        val activeGatt = gatt
        if (activeGatt == null) {
            viewModel.trace("Display send skipped: no active GATT session")
            return false
        }

        val channelMap = viewModel.currentClusterChannelMap() ?: viewModel.resolveClusterChannelMap(activeGatt)
        if (channelMap == null) {
            viewModel.trace("Display send skipped: FFF1 write characteristic unavailable")
            return false
        }

        val packet = SuzukiDisplaySender.buildDisplayPacket(text, secondaryText)
        val visibleCharacters = listOf(text, secondaryText.orEmpty())
            .filter { it.isNotBlank() }
            .joinToString(separator = " | ")
            .length
        viewModel.trace("TX dot-matrix ($visibleCharacters chars): ${SuzukiDisplaySender.toHex(packet)}")
        val sent = SuzukiDisplaySender.sendPacket(activeGatt, channelMap.writeCharacteristic, packet)
        if (!sent) {
            viewModel.trace("Display send failed: cluster write returned an error")
        }
        return sent
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            viewModel.trace("Connection state status=$status state=$newState")
            if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                retryAttempt = 0
                handler.removeCallbacksAndMessages(null)
                viewModel.onConnected()
                servicesDiscoveryStarted = false
                mtuCallbackReceived = false
                viewModel.trace("Requesting MTU 247")
                val mtuOk = gatt.requestMtu(247)
                viewModel.trace("requestMtu(247) returned $mtuOk")
                // Fallback in case onMtuChanged is never delivered.
                handler.postDelayed({
                    if (!servicesDiscoveryStarted) {
                        viewModel.trace("MTU callback not received; discovering services directly")
                        servicesDiscoveryStarted = true
                        gatt.discoverServices()
                    }
                }, MTU_FALLBACK_MS)
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                stopScan()
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    viewModel.onDisconnected()
                } else {
                    failConnection("GATT disconnected with status $status")
                }
                gatt.close()
                this@BleManager.gatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            viewModel.trace("MTU changed: mtu=$mtu status=$status")
            mtuCallbackReceived = true
            if (!servicesDiscoveryStarted) {
                servicesDiscoveryStarted = true
                handler.removeCallbacksAndMessages(null)
                viewModel.trace("Discovering GATT services")
                gatt.discoverServices()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            viewModel.trace("Services discovered status=$status (count=${gatt.services.size})")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                failConnection("Service discovery failed: $status")
                return
            }

            // Replicate the stock app's wake read: MyBleService.a() reads the
            // 4th service's 2nd characteristic right after connecting. This is
            // what activates the cluster's notification stream.
            performWakeRead(gatt)

            val channelMap = viewModel.resolveClusterChannelMap(gatt)
            if (channelMap == null) {
                failConnection("Required FFF0/FFF1/FFF2 BLE UUIDs were not found")
                return
            }

            viewModel.trace("Found FFF0 service with FFF1 write and FFF2 notify characteristics")
            if (!viewModel.enableNotifications(gatt, channelMap.notifyCharacteristic)) {
                failConnection("Could not enable FFF2 notifications")
            } else {
                viewModel.trace("Waiting for FFF2 notifications")
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            viewModel.trace("Characteristic read ${characteristic.uuid}: status=$status")
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            viewModel.trace("Characteristic read ${characteristic.uuid}: status=$status (${value.size} bytes)")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            if (viewModel.isNotificationCharacteristic(characteristic)) {
                viewModel.onNotification(value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptor.uuid == CCCD_UUID) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    viewModel.trace("CCCD write confirmed; notifications enabled")
                } else {
                    failConnection("CCCD write failed: $status")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun performWakeRead(gatt: BluetoothGatt) {
        try {
            val services = gatt.services
            if (services.size > 3) {
                val svc = services[3]
                val chars = svc.characteristics
                if (chars.size > 1) {
                    val c = chars[1]
                    viewModel.trace("Wake-read ${svc.uuid}/${c.uuid}")
                    gatt.readCharacteristic(c)
                } else {
                    viewModel.trace("Wake-read skipped: service[3] has no 2nd characteristic")
                }
            } else {
                viewModel.trace("Wake-read skipped: only ${services.size} services")
            }
        } catch (e: Exception) {
            viewModel.trace("Wake-read failed: ${e.message}")
        }
    }

    private fun failConnection(message: String) {
        viewModel.onConnectionError(message)
        scheduleRetry()
    }

    private fun scheduleRetry() {
        if (!shouldReconnect || retryAttempt >= MAX_RETRIES) return
        retryAttempt++
        val delay = RETRY_DELAY_MS * retryAttempt
        viewModel.trace("Retry $retryAttempt/$MAX_RETRIES in ${delay}ms")
        handler.postDelayed({ retryConnectionIfNeeded() }, delay)
    }

    @SuppressLint("MissingPermission")
    private fun retryConnectionIfNeeded() {
        if (!shouldReconnect) return
        gatt?.close()
        gatt = null
        viewModel.onConnecting()
        startScan()
    }

    private companion object {
        const val TARGET_DEVICE_ADDRESS = "XX:XX:XX:XX:XX:XX"
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1_500L
        const val SCAN_TIMEOUT_MS = 6_000L
        const val MTU_FALLBACK_MS = 2_500L
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
