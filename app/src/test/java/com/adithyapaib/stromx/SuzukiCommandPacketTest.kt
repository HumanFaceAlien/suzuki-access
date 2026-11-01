package com.adithyapaib.stromx

import com.adithyapaib.stromx.ble.SuzukiChecksum
import com.adithyapaib.stromx.ble.SuzukiCommandPacket
import org.junit.Assert.assertEquals
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
        val packet = SuzukiCommandPacket.buildIdentityPacket("StromX")

        assertEquals(30, packet.size)
        assertEquals(0xA5.toByte(), packet[0])
        assertEquals('6'.code.toByte(), packet[1])
        val nameField = packet.copyOfRange(2, 22).toString(Charsets.US_ASCII)
        assertEquals("StromX", nameField.trimEnd { it == 0.toChar() })
        assertTrue(nameField.drop("StromX".length).all { it == 0.toChar() })
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
    fun buildsNavigationFrameLayout() {
        val packet = SuzukiCommandPacket.buildNavigationPacket(
            maneuver = 4,
            distanceToManeuver = "0450",
            distanceToManeuverUnit = 'M',
            eta = "001842",
            remainingDistance = "05.3",
            remainingDistanceUnit = 'K',
        )

        assertEquals(30, packet.size)
        assertEquals(0xA5.toByte(), packet[0])
        assertEquals('1'.code.toByte(), packet[1])
        assertEquals(4, packet[2].toInt())
        assertEquals(0xFF.toByte(), packet[3])
        assertEquals("0450M001842", packet.copyOfRange(4, 15).toString(Charsets.US_ASCII))
        for (index in 15..17) assertEquals(0xFF.toByte(), packet[index])
        assertEquals("05.3K", packet.copyOfRange(18, 23).toString(Charsets.US_ASCII))
        assertEquals('1'.code.toByte(), packet[23])
        assertEquals('1'.code.toByte(), packet[24])
        for (index in 25..27) assertEquals(0xFF.toByte(), packet[index])
        assertEquals(SuzukiChecksum.compute(packet), packet[28])
        assertEquals(0x7F.toByte(), packet[29])
    }

    @Test
    fun navigationEndFrameStopsNavigation() {
        val packet = SuzukiCommandPacket.buildNavigationEndPacket()

        assertEquals(SuzukiCommandPacket.NO_MANEUVER, packet[2].toInt())
        assertEquals('0'.code.toByte(), packet[23])
        assertEquals('0'.code.toByte(), packet[24])
        assertEquals(SuzukiChecksum.compute(packet), packet[28])
    }

    @Test
    fun formatsDistancesForTheClusterField() {
        assertEquals("0450" to 'M', SuzukiCommandPacket.formatDistance(450))
        assertEquals("01.2" to 'K', SuzukiCommandPacket.formatDistance(1_200))
        assertEquals("12.5" to 'K', SuzukiCommandPacket.formatDistance(12_500))
        assertEquals("99.9" to 'K', SuzukiCommandPacket.formatDistance(99_949))
        assertEquals("0150" to 'K', SuzukiCommandPacket.formatDistance(150_000))
        assertEquals("9999" to 'K', SuzukiCommandPacket.formatDistance(20_000_000))
        assertEquals("    " to 'M', SuzukiCommandPacket.formatDistance(null))
    }

    @Test
    fun formatsEtaIn24And12HourForms() {
        val evening = java.time.LocalTime.of(18, 42)
        assertEquals("001842", SuzukiCommandPacket.formatEta(evening, twelveHour = false))
        assertEquals("0642PM", SuzukiCommandPacket.formatEta(evening, twelveHour = true))
        assertEquals("1205AM", SuzukiCommandPacket.formatEta(java.time.LocalTime.of(0, 5), twelveHour = true))
        assertEquals("      ", SuzukiCommandPacket.formatEta(null, twelveHour = false))
    }
}
