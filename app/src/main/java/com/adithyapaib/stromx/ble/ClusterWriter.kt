package com.adithyapaib.stromx.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic

/** Writes 30-byte frames to the cluster's FFF1 characteristic and formats them for the log. */
object ClusterWriter {
    private const val PACKET_SIZE = 30

    @SuppressLint("MissingPermission")
    fun sendPacket(
        gatt: BluetoothGatt,
        writeCharacteristic: BluetoothGattCharacteristic,
        packet: ByteArray
    ): Boolean {
        if (packet.size != PACKET_SIZE) return false

        val status = gatt.writeCharacteristic(
            writeCharacteristic,
            packet,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        )
        return status == BluetoothGatt.GATT_SUCCESS
    }

    fun toHex(data: ByteArray?): String {
        if (data == null) return ""
        return data.joinToString(separator = " ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
    }
}
