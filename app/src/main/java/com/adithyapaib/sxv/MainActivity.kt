package com.adithyapaib.sxv

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.adithyapaib.sxv.ble.BleViewModel
import com.adithyapaib.sxv.garage.GarageViewModel
import com.adithyapaib.sxv.settings.SettingsViewModel
import com.adithyapaib.sxv.ui.SxvApp
import com.adithyapaib.sxv.ui.theme.SXVTheme

class MainActivity : ComponentActivity() {
    private val bleViewModel: BleViewModel by viewModels()
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val garageViewModel: GarageViewModel by viewModels()

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED &&
                intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR) == BluetoothAdapter.STATE_ON &&
                hasBluetoothPermissions()
            ) {
                bleViewModel.trace("Bluetooth adapter is ON; reconnecting")
                bleViewModel.connect()
            }
        }
    }

    private val bluetoothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            bleViewModel.trace("Bluetooth permissions granted")
            bleViewModel.connect()
        } else {
            bleViewModel.onConnectionError("Bluetooth permissions are required")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        bleViewModel.attachBle(this)
        setContent {
            SXVTheme {
                SxvApp(viewModel = bleViewModel, settings = settingsViewModel, garage = garageViewModel)
            }
        }

        if (hasBluetoothPermissions()) {
            bleViewModel.connect()
        } else {
            bluetoothPermissionLauncher.launch(BLUETOOTH_PERMISSIONS)
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(bluetoothStateReceiver)
        super.onStop()
    }

    private fun hasBluetoothPermissions(): Boolean = BLUETOOTH_PERMISSIONS.all { permission ->
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        val BLUETOOTH_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }
}
