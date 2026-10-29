package com.adithyapaib.sxv.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import java.util.UUID

/**
 * Builds and writes the 30-byte dot-matrix display packet used by the Suzuki
 * instrument panel (command '5', `"?5"` template).
 *
 * Checksums are delegated to [SuzukiChecksum], which sums bytes **1..27**.
 * Earlier revisions of this class summed bytes 0..27, which folded the `0xA5`
 * header into the sum and added `0xA5` to every frame; see [SuzukiChecksum].
 */
object SuzukiDisplaySender {
    const val MAX_VISIBLE_TEXT_LENGTH = 22
    private const val SECONDARY_FIELD_SEPARATOR = " | "
    private const val PACKET_SIZE = 30
    private const val HEADER_INDEX = 0
    private const val COMMAND_INDEX = 1
    private const val MESSAGE_FLAG_INDEX = 2
    private const val TEXT_START_INDEX = 3
    private const val TEXT_END_EXCLUSIVE = 25
    private const val MODE_INDEX = 25
    private const val TRAILER_FLAG_ONE_INDEX = 26
    private const val TRAILER_FLAG_TWO_INDEX = 27
    private const val CHECKSUM_INDEX = 28
    private const val TERMINATOR_INDEX = 29
    private const val HEADER_BYTE: Byte = 0xA5.toByte()
    private const val TERMINATOR_BYTE: Byte = 0x7F.toByte()
    private const val COMMAND = '5'
    private const val MODE = 'N'
    private const val MESSAGE_FLAG: Byte = 0xFF.toByte()

    val serviceUuid: UUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb")
    val writeCharacteristicUuid: UUID = UUID.fromString("0000fff1-0000-1000-8000-00805f9b34fb")

    fun buildDisplayPacket(text: String?): ByteArray {
        return buildDotMatrixPacket(primaryText = text.orEmpty(), secondaryText = null)
    }

    fun buildDisplayPacket(primaryText: String?, secondaryText: String?): ByteArray {
        return buildDotMatrixPacket(primaryText.orEmpty(), secondaryText)
    }

    fun buildDotMatrixPacket(
        primaryText: String,
        secondaryText: String?,
        checksumMode: SuzukiChecksum.Mode = SuzukiChecksum.Mode.PLAIN_SUM,
    ): ByteArray {
        val composedText = composeVisibleText(primaryText, secondaryText)
        val payloadText = padRight(composedText, MAX_VISIBLE_TEXT_LENGTH, '\u0000')
        val textBytes = payloadText.toByteArray(Charsets.US_ASCII)

        return ByteArray(PACKET_SIZE).apply {
            this[HEADER_INDEX] = HEADER_BYTE
            this[COMMAND_INDEX] = COMMAND.code.toByte()
            this[MESSAGE_FLAG_INDEX] = MESSAGE_FLAG
            textBytes.copyInto(this, destinationOffset = TEXT_START_INDEX, endIndex = minOf(textBytes.size, TEXT_END_EXCLUSIVE - TEXT_START_INDEX))
            this[MODE_INDEX] = MODE.code.toByte()
            this[TRAILER_FLAG_ONE_INDEX] = MESSAGE_FLAG
            this[TRAILER_FLAG_TWO_INDEX] = MESSAGE_FLAG
            this[CHECKSUM_INDEX] = SuzukiChecksum.compute(this, checksumMode)
            this[TERMINATOR_INDEX] = TERMINATOR_BYTE
        }
    }

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

    fun sanitizeAscii(text: String): String = buildString(text.length) {
        text.forEach { character ->
            append(
                if (character.code in 32..126) {
                    character
                } else {
                    ' '
                }
            )
        }
    }

    fun toHex(data: ByteArray?): String {
        if (data == null) return ""
        return data.joinToString(separator = " ") { byte -> "%02X".format(byte.toInt() and 0xFF) }
    }

    private fun composeVisibleText(primaryText: String, secondaryText: String?): String {
        val primary = sanitizeAscii(primaryText).trim()
        val secondary = sanitizeAscii(secondaryText.orEmpty()).trim()
        val combined = when {
            primary.isEmpty() -> secondary
            secondary.isEmpty() -> primary
            else -> "$primary$SECONDARY_FIELD_SEPARATOR$secondary"
        }
        return combined.take(MAX_VISIBLE_TEXT_LENGTH)
    }

    private fun padRight(value: String, length: Int, padChar: Char): String {
        if (value.length >= length) return value.substring(0, length)
        return value + padChar.toString().repeat(length - value.length)
    }
}




