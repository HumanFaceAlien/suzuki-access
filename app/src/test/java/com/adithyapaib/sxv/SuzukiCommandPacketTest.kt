package com.adithyapaib.sxv

import com.adithyapaib.sxv.ble.SuzukiChecksum
import com.adithyapaib.sxv.ble.SuzukiCommandPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuzukiCommandPacketTest {

    @Test
    fun buildsNotificationTextFrameShape() {
        val packet = SuzukiCommandPacket.buildNotificationTextPacket("MISSING CALL", 3)

        assertEquals(30, packet.size)
        assertEquals(0xA5.toByte(), packet[0])
        assertEquals('4'.code.toByte(), packet[1])
        assertEquals(0xFF.toByte(), packet[2])
        assertEquals(3, packet[3].toInt())
        assertEquals('N'.code.toByte(), packet[24])
        assertEquals(0xFF.toByte(), packet[25])
        assertEquals(0xFF.toByte(), packet[26])
        assertEquals(0xFF.toByte(), packet[27])
        assertEquals(0x7F.toByte(), packet[29])
        assertEquals(SuzukiChecksum.compute(packet), packet[28])
    }

    @Test
    fun truncatesAndPadsNotificationTextToTwentyChars() {
        val long = SuzukiCommandPacket.buildNotificationTextPacket("A".repeat(40))
        assertEquals(
            "A".repeat(SuzukiCommandPacket.MAX_VISIBLE_TEXT_LENGTH),
            long.copyOfRange(4, 24).toString(Charsets.US_ASCII)
        )

        val short = SuzukiCommandPacket.buildNotificationTextPacket("HI")
        val field = short.copyOfRange(4, 24).toString(Charsets.US_ASCII)
        assertTrue(field.startsWith("HI"))
    }

    @Test
    fun sanitizesNonPrintableCharactersInNotificationText() {
        val packet = SuzukiCommandPacket.buildNotificationTextPacket("A\u0000\nB")
        val field = packet.copyOfRange(4, 24).toString(Charsets.US_ASCII)

        assertTrue(field.startsWith("A  B"))
    }

    @Test
    fun buildsIdentityFrameShape() {
        val packet = SuzukiCommandPacket.buildIdentityPacket("SXV")

        assertEquals(30, packet.size)
        assertEquals(0xA5.toByte(), packet[0])
        assertEquals('6'.code.toByte(), packet[1])
        assertEquals("SXV", packet.copyOfRange(2, 5).toString(Charsets.US_ASCII))
        for (index in 5..21) assertEquals(0.toByte(), packet[index])
        for (index in 22..26) assertEquals(0xFF.toByte(), packet[index])
        assertEquals('R'.code.toByte(), packet[27])
        assertEquals(SuzukiChecksum.compute(packet), packet[28])
        assertEquals(0x7F.toByte(), packet[29])
    }

    @Test
    fun identityFlagAndNameTruncation() {
        val packet = SuzukiCommandPacket.buildIdentityPacket("A".repeat(30), reconnect = false)

        assertEquals("A".repeat(20), packet.copyOfRange(2, 22).toString(Charsets.US_ASCII))
        assertEquals('F'.code.toByte(), packet[27])
    }

    @Test
    fun buildsNavigationFrameWithDefaultWidths() {
        val packet = SuzukiCommandPacket.buildNavigationPacket(
            maneuverCode = 0x2E,      // '.'
            gpsStatus = '1',
            gpsStatus2 = '0',
        )

        checkNotNull(packet) { "default navigation template must assemble to 30 bytes" }
        assertEquals(30, packet.size)
        assertEquals(0xA5.toByte(), packet[0])
        assertEquals('1'.code.toByte(), packet[1])
        assertEquals(0x2E, packet[2].toInt())
        assertEquals(0xFF.toByte(), packet[3])
        assertEquals(0xFF.toByte(), packet[15])
        assertEquals(0xFF.toByte(), packet[16])
        assertEquals(0xFF.toByte(), packet[17])
        assertEquals(0xFF.toByte(), packet[25])
        assertEquals(0xFF.toByte(), packet[26])
        assertEquals(0xFF.toByte(), packet[27])
        assertEquals(0x7F.toByte(), packet[29])
        assertEquals(SuzukiChecksum.compute(packet), packet[28])
    }

    @Test
    fun gpsStatusLandsAtTheTemplateTail() {
        val packet = SuzukiCommandPacket.buildNavigationPacket(
            maneuverCode = 0x30,
            gpsStatus = '1',
            gpsStatus2 = '0',
        )

        checkNotNull(packet)
        assertEquals('1'.code.toByte(), packet[23])
        assertEquals('0'.code.toByte(), packet[24])
    }

    @Test
    fun tftEditionSetsByteNineWhenFieldNStartsWithZero() {
        val tft = SuzukiCommandPacket.buildNavigationPacket(
            maneuverCode = 0x2E,
            gpsStatus = '1',
            gpsStatus2 = '0',
            fieldN = "010",
            isTftEdition = true,
        )
        checkNotNull(tft)
        assertEquals(0x20, tft[9].toInt())

        val nonTft = SuzukiCommandPacket.buildNavigationPacket(
            maneuverCode = 0x2E,
            gpsStatus = '1',
            gpsStatus2 = '0',
            fieldN = "010",
            isTftEdition = false,
        )
        checkNotNull(nonTft)
        assertEquals('0'.code.toByte(), nonTft[9])

        val wrongStart = SuzukiCommandPacket.buildNavigationPacket(
            maneuverCode = 0x2E,
            gpsStatus = '1',
            gpsStatus2 = '0',
            fieldN = "510",
            isTftEdition = true,
        )
        checkNotNull(wrongStart)
        assertEquals('0'.code.toByte(), wrongStart[9])
    }

    @Test
    fun returnsNullRatherThanAnUnderOrOverSizedNavigationFrame() {
        assertNull(
            SuzukiCommandPacket.buildNavigationPacket(
                maneuverCode = 0x2E,
                gpsStatus = '1',
                gpsStatus2 = '0',
                fieldN = "01",   // one char short of the template's assumed width
            )
        )
        assertNull(
            SuzukiCommandPacket.buildNavigationPacket(
                maneuverCode = 0x2E,
                gpsStatus = '1',
                gpsStatus2 = '0',
                fieldN = "0101",   // one char long
            )
        )
    }
}
