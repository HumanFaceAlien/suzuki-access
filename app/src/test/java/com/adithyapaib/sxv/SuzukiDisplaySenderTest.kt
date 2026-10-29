package com.adithyapaib.sxv

import com.adithyapaib.sxv.ble.SuzukiDisplaySender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuzukiDisplaySenderTest {
    @Test
    fun buildsExpectedDotMatrixPacketShape() {
        val packet = SuzukiDisplaySender.buildDisplayPacket("HELLO")

        assertEquals(30, packet.size)
        assertEquals(0xA5.toByte(), packet[0])
        assertEquals('5'.code.toByte(), packet[1])
        assertEquals(0xFF.toByte(), packet[2])
        assertEquals('N'.code.toByte(), packet[25])
        assertEquals(0xFF.toByte(), packet[26])
        assertEquals(0xFF.toByte(), packet[27])
        assertEquals(0x7F.toByte(), packet[29])

        val textField = packet.copyOfRange(3, 25).toString(Charsets.US_ASCII)
        assertTrue(textField.startsWith("HELLO"))

        // Checksum covers bytes 1..27 only; byte 0 (0xA5) is excluded.
        assertEquals(com.adithyapaib.sxv.ble.SuzukiChecksum.compute(packet), packet[28])
    }

    @Test
    fun sanitizesAndTruncatesVisibleText() {
        val packet = SuzukiDisplaySender.buildDisplayPacket("HELLO\nWORLD-12345678901234567890")
        val textField = packet.copyOfRange(3, 25).toString(Charsets.US_ASCII)

        assertEquals(22, textField.length)
        assertTrue(textField.startsWith("HELLO WORLD-1234567890"))
    }

    @Test
    fun composesPrimaryAndSecondaryFieldsIntoSingleVisiblePayload() {
        val packet = SuzukiDisplaySender.buildDisplayPacket("HELLO", "SXV")
        val textField = packet.copyOfRange(3, 25).toString(Charsets.US_ASCII)

        assertTrue(textField.startsWith("HELLO | SXV"))
    }

    @Test
    fun secondaryFieldCanBeSentAlone() {
        val packet = SuzukiDisplaySender.buildDisplayPacket("", "RIDE SAFE")
        val textField = packet.copyOfRange(3, 25).toString(Charsets.US_ASCII)

        assertTrue(textField.startsWith("RIDE SAFE"))
    }

    @Test
    fun hexFormatterIsStable() {
        val hex = SuzukiDisplaySender.toHex(byteArrayOf(0x01, 0x0F, 0x7F))
        assertEquals("01 0F 7F", hex)
    }
}



