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
import android.os.SystemClock
import com.adithyapaib.sxv.settings.PairedBike
import com.adithyapaib.sxv.settings.SettingsViewModel
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
 *  6. Write the identity frame ('6') to FFF1. Without it the cluster accepts
 *     the subscription but sends nothing, and drops the link after about a
 *     minute; this is what the official app does when it connects. It carries
 *     the rider name from Settings, which the cluster shows as its welcome
 *     greeting. Resent up to [MAX_IDENTITY_ATTEMPTS] times if no telemetry
 *     follows.
 *
 * Pairing: until a bike is remembered, the scan collects every cluster-like
 * device for [SCAN_TIMEOUT_MS] and connects to the strongest signal, which is
 * the bike the phone is sitting on even on a group ride. The first one that
 * streams telemetry is saved ([BleViewModel.rememberBike]) and from then on
 * only that bike is connected to; Settings > Forget bike starts over. Other
 * Suzuki clusters (e.g. an Access scooter, `SAS…`) also match the name
 * pattern, so any device without FFF0 is skipped for the rest of the session.
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
    private var writeInFlightSince = 0L
    private var notificationsConfirmed = false
    private var telemetrySeen = false
    private var identityAttempts = 0
    private var identityWritePending = false
    private var quietWritePending = false
    private val rejectedAddresses = mutableSetOf<String>()
    private var scanCandidate: BluetoothDevice? = null
    private var scanCandidateRssi = Int.MIN_VALUE
    private val scanSeen = mutableSetOf<String>()
    private val identityWatchdog = Runnable {
        val active = gatt ?: return@Runnable
        if (telemetrySeen) return@Runnable
        if (identityAttempts < MAX_IDENTITY_ATTEMPTS) {
            viewModel.trace("No telemetry ${IDENTITY_TIMEOUT_MS / 1000}s after identity; resending")
            sendIdentity(active)
        } else {
            viewModel.trace("No telemetry after $identityAttempts identity writes")
        }
    }
    private val verboseReadQueue = ArrayDeque<BluetoothGattCharacteristic>()
    private var verboseReadInFlight: BluetoothGattCharacteristic? = null
    private val verboseReadTimeout = Runnable {
        val stuck = verboseReadInFlight ?: return@Runnable
        viewModel.vtrace("Sweep read ${stuck.uuid} timed out; skipping")
        gatt?.let { readNextVerbose(it) }
    }
    private val rssiPoll = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            val active = gatt ?: return
            if (!viewModel.verbose.value) return
            active.readRemoteRssi()
            handler.postDelayed(this, RSSI_POLL_MS)
        }
    }

    /** Called when verbose logging is switched on; describes the link that is already up. */
    fun onVerboseEnabled() {
        val active = gatt
        if (active == null) {
            viewModel.vtrace("No active GATT session; details will be logged on the next connection")
            return
        }
        dumpGattTable(active)
        if (notificationsConfirmed) startVerboseDiagnostics(active)
    }

    @SuppressLint("MissingPermission")
    private fun dumpGattTable(gatt: BluetoothGatt) {
        if (!viewModel.verbose.value) return
        val device = gatt.device
        viewModel.vtrace("Device name=${device.name} address=${device.address} bondState=${device.bondState} (10=none,11=bonding,12=bonded)")
        for (service in gatt.services) {
            viewModel.vtrace("SERVICE ${service.uuid} instance=${service.instanceId}")
            for (characteristic in service.characteristics) {
                viewModel.vtrace(
                    "  CHAR ${characteristic.uuid} props=0x%02X [%s] instance=%d"
                        .format(characteristic.properties, propertyNames(characteristic.properties), characteristic.instanceId)
                )
                for (descriptor in characteristic.descriptors) {
                    viewModel.vtrace("    DESC ${descriptor.uuid}")
                }
            }
        }
    }

    private fun propertyNames(properties: Int): String {
        val names = listOf(
            BluetoothGattCharacteristic.PROPERTY_BROADCAST to "BROADCAST",
            BluetoothGattCharacteristic.PROPERTY_READ to "READ",
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE to "WRITE_NO_RESP",
            BluetoothGattCharacteristic.PROPERTY_WRITE to "WRITE",
            BluetoothGattCharacteristic.PROPERTY_NOTIFY to "NOTIFY",
            BluetoothGattCharacteristic.PROPERTY_INDICATE to "INDICATE",
            BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE to "SIGNED_WRITE",
            BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS to "EXTENDED"
        )
        return names.filter { (flag, _) -> properties and flag != 0 }.joinToString(" ") { it.second }
    }

    /** Reads every readable characteristic once (Device Information etc.) and starts RSSI polling. */
    @SuppressLint("MissingPermission")
    private fun startVerboseDiagnostics(gatt: BluetoothGatt) {
        if (!viewModel.verbose.value) return
        verboseReadQueue.clear()
        gatt.services.forEach { service ->
            service.characteristics
                .filter { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 }
                .forEach { verboseReadQueue.addLast(it) }
        }
        viewModel.vtrace("Reading ${verboseReadQueue.size} readable characteristics one at a time")
        readNextVerbose(gatt)
        handler.removeCallbacks(rssiPoll)
        handler.post(rssiPoll)
    }

    @SuppressLint("MissingPermission")
    private fun readNextVerbose(gatt: BluetoothGatt) {
        handler.removeCallbacks(verboseReadTimeout)
        while (true) {
            val next = verboseReadQueue.removeFirstOrNull()
            if (next == null) {
                if (verboseReadInFlight != null) viewModel.vtrace("Characteristic sweep complete")
                verboseReadInFlight = null
                return
            }
            if (gatt.readCharacteristic(next)) {
                verboseReadInFlight = next
                handler.postDelayed(verboseReadTimeout, VERBOSE_READ_TIMEOUT_MS)
                return
            }
            viewModel.vtrace("Read of ${next.uuid} was not started")
        }
    }

    private fun describeValue(value: ByteArray): String {
        val hex = value.joinToString("-") { "%02X".format(it.toInt() and 0xFF) }
        val printable = value.all { it in 0x20..0x7E }
        return if (printable && value.isNotEmpty()) "$hex \"${String(value, Charsets.US_ASCII)}\"" else hex
    }

    /** Called by the ViewModel for every valid telemetry frame. */
    @SuppressLint("MissingPermission")
    fun onTelemetryFrame() {
        if (!telemetrySeen) {
            viewModel.trace("Telemetry stream started")
            gatt?.device?.let { viewModel.rememberBike(PairedBike(it.address, it.name.orEmpty())) }
        }
        telemetrySeen = true
        retryAttempt = 0
        handler.removeCallbacks(identityWatchdog)
    }

    @SuppressLint("MissingPermission")
    private fun sendIdentity(gatt: BluetoothGatt) {
        val channelMap = viewModel.currentClusterChannelMap() ?: return
        identityAttempts++
        val packet = SuzukiCommandPacket.buildIdentityPacket(SettingsViewModel.identityName(appContext))
        viewModel.trace("TX identity (attempt $identityAttempts/$MAX_IDENTITY_ATTEMPTS): ${ClusterWriter.toHex(packet)}")
        identityWritePending = ClusterWriter.sendPacket(gatt, channelMap.writeCharacteristic, packet)
        if (identityWritePending) {
            writeInFlightSince = SystemClock.elapsedRealtime()
        } else {
            viewModel.trace("Identity write was not started")
            if (identityAttempts == 1) startVerboseDiagnostics(gatt)
        }
        handler.removeCallbacks(identityWatchdog)
        handler.postDelayed(identityWatchdog, IDENTITY_TIMEOUT_MS)
    }

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
            connectWithoutScan("No LE scanner")
            return
        }
        scanning = true
        scanCandidate = null
        scanCandidateRssi = Int.MIN_VALUE
        scanSeen.clear()
        val bike = viewModel.pairedBike.value
        viewModel.trace(
            if (bike != null) "Scanning for paired bike ${bike.name} @ ${bike.address}..."
            else "Not paired yet; scanning ${SCAN_TIMEOUT_MS / 1000}s for the nearest SA/SB/SC cluster..."
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        try {
            scanner.startScan(null, settings, scanCallback)
        } catch (se: SecurityException) {
            scanning = false
            connectWithoutScan("Scan permission denied: ${se.message}")
            return
        } catch (ise: IllegalStateException) {
            scanning = false
            connectWithoutScan("Scanner unavailable: ${ise.message}")
            return
        }
        handler.postDelayed({
            if (scanning) {
                stopScan()
                val candidate = scanCandidate
                scanCandidate = null
                when {
                    bike != null -> connectWithoutScan("Paired bike not seen in ${SCAN_TIMEOUT_MS / 1000}s")
                    candidate != null -> {
                        viewModel.trace("Strongest cluster: ${candidate.name} @ ${candidate.address} ($scanCandidateRssi dBm)")
                        connectToDevice(candidate)
                    }
                    else -> failConnection("No Suzuki cluster found; switch the ignition on and try again")
                }
            }
        }, SCAN_TIMEOUT_MS)
    }

    /**
     * When scanning is unavailable or the paired bike was not seen, waits for it
     * with an autoConnect GATT connection. Before pairing there is no address to
     * wait for, so this fails instead.
     */
    private fun connectWithoutScan(reason: String) {
        val bike = viewModel.pairedBike.value
        if (bike == null) {
            failConnection("$reason; no paired bike to connect to directly")
        } else {
            viewModel.trace("$reason; connecting directly to ${bike.address}")
            connectDirect(bike.address)
        }
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
    private fun connectDirect(address: String) {
        try {
            val device = adapter?.getRemoteDevice(address)
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
            val bike = viewModel.pairedBike.value
            if (bike != null) {
                // Paired: every other cluster is someone else's bike.
                if (device.address.equals(bike.address, ignoreCase = true)) {
                    viewModel.trace("Found paired bike: $name @ ${device.address}")
                    stopScan()
                    handler.removeCallbacksAndMessages(null)
                    connectToDevice(device)
                }
                return
            }
            val looksLikeCluster = name.length == 12 &&
                (name.contains("SA") || name.contains("SB") || name.contains("SC"))
            if (!looksLikeCluster || device.address in rejectedAddresses) return
            // Keep scanning the whole window; the strongest signal wins on timeout.
            if (scanSeen.add(device.address)) {
                viewModel.trace("Seen cluster $name @ ${device.address} (${result.rssi} dBm)")
            }
            if (result.rssi > scanCandidateRssi || device.address == scanCandidate?.address) {
                scanCandidate = device
                scanCandidateRssi = result.rssi
            }
        }

        override fun onScanFailed(errorCode: Int) {
            if (!scanning) return
            scanning = false
            connectWithoutScan("Scan failed (errorCode=$errorCode)")
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

    /**
     * Writes a prebuilt frame (navigation) to FFF1. Android allows one outstanding
     * GATT write, and the identity handshake uses the same slot, so this returns
     * false, without logging, while a write is in flight; the caller resends on
     * its next tick. Only failures reach the log.
     */
    @SuppressLint("MissingPermission")
    fun sendFrame(packet: ByteArray): Boolean {
        val activeGatt = gatt ?: return false
        val channelMap = viewModel.currentClusterChannelMap() ?: return false
        val now = SystemClock.elapsedRealtime()
        if (writeInFlightSince != 0L && now - writeInFlightSince < WRITE_TIMEOUT_MS) return false
        val sent = ClusterWriter.sendPacket(activeGatt, channelMap.writeCharacteristic, packet)
        if (sent) {
            writeInFlightSince = now
            quietWritePending = true
        }
        return sent
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            viewModel.trace("Connection state status=$status state=$newState")
            if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                // retryAttempt is reset by the first telemetry frame, not here: a link
                // that connects but never streams must still count toward MAX_RETRIES.
                telemetrySeen = false
                identityAttempts = 0
                identityWritePending = false
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
                writeInFlightSince = 0L
                notificationsConfirmed = false
                verboseReadQueue.clear()
                verboseReadInFlight = null
                handler.removeCallbacks(rssiPoll)
                handler.removeCallbacks(verboseReadTimeout)
                handler.removeCallbacks(identityWatchdog)
                identityWritePending = false
                quietWritePending = false
                viewModel.vtrace(
                    "Disconnect status=$status (8=supervision timeout, 19=peer terminated, 22=local terminated, 133=generic error)"
                )
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

            dumpGattTable(gatt)

            // Replicate the stock app's wake read: MyBleService.a() reads the
            // 4th service's 2nd characteristic right after connecting. This is
            // what activates the cluster's notification stream.
            performWakeRead(gatt)

            val channelMap = viewModel.resolveClusterChannelMap(gatt)
            if (channelMap == null) {
                rejectedAddresses += gatt.device.address
                viewModel.trace("Skipping ${gatt.device.address} for this session: no FFF0 service, not the bike")
                failConnection("Required FFF0/FFF1/FFF2 BLE UUIDs were not found")
                return
            }

            viewModel.trace("Found FFF0 service with FFF1 write and FFF2 notify characteristics")
            viewModel.trace(
                "FFF1 properties=0x%02X (WRITE=0x08, WRITE_NO_RESPONSE=0x04) bondState=%d (10=none,11=bonding,12=bonded)"
                    .format(channelMap.writeCharacteristic.properties, gatt.device.bondState)
            )
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
            viewModel.vtrace("READ ${characteristic.uuid} status=$status value=${describeValue(value)}")
            if (characteristic.uuid == verboseReadInFlight?.uuid) readNextVerbose(gatt)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            viewModel.vtrace("RSSI $rssi dBm (status=$status)")
        }

        override fun onPhyUpdate(gatt: BluetoothGatt, txPhy: Int, rxPhy: Int, status: Int) {
            viewModel.vtrace("PHY update tx=$txPhy rx=$rxPhy (1=1M, 2=2M, 3=coded) status=$status")
        }

        override fun onServiceChanged(gatt: BluetoothGatt) {
            viewModel.vtrace("Service Changed indication received")
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

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            writeInFlightSince = 0L
            if (identityWritePending) {
                identityWritePending = false
                viewModel.trace("Identity write completed status=$status")
                // The sweep's reads would collide with the identity write, so it starts after.
                if (identityAttempts == 1) startVerboseDiagnostics(gatt)
            } else if (quietWritePending) {
                quietWritePending = false
                if (status != BluetoothGatt.GATT_SUCCESS) viewModel.trace("Navigation write failed status=$status")
            } else {
                viewModel.trace("Cluster write completed status=$status")
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
                    notificationsConfirmed = true
                    sendIdentity(gatt)
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
        const val MAX_IDENTITY_ATTEMPTS = 3
        const val IDENTITY_TIMEOUT_MS = 8_000L
        const val MAX_RETRIES = 3
        const val RETRY_DELAY_MS = 1_500L
        const val SCAN_TIMEOUT_MS = 6_000L
        const val MTU_FALLBACK_MS = 2_500L
        const val WRITE_TIMEOUT_MS = 2_000L
        const val RSSI_POLL_MS = 5_000L
        const val VERBOSE_READ_TIMEOUT_MS = 3_000L
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
