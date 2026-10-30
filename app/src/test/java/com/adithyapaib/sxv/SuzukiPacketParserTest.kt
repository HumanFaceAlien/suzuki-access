package com.adithyapaib.sxv

import com.adithyapaib.sxv.ble.SuzukiChecksum
import com.adithyapaib.sxv.ble.SuzukiPacketParser
import com.adithyapaib.sxv.ble.VStromTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SuzukiPacketParserTest {

    /**
     * Layout of [asciiBlock] (24 chars = bytes 1..24):
     * `[0]='7' [1..3] speed [4..9] odometer [10..15] tripA [16..21] tripB [22] gear 'N' [23] fuel`
     */
    private fun packetFor(
        asciiBlock: String,
        command: Char = '7',
        corruptChecksum: Boolean = false,
    ): ByteArray {
        require(asciiBlock.length == 24) { "ascii block must be 24 chars, was ${asciiBlock.length}" }
        val packet = ByteArray(30).apply {
            asciiBlock.toByteArray(Charsets.US_ASCII).copyInto(this, 1)
            this[0] = 0xA5.toByte()
            this[1] = command.code.toByte()   // after the copy, so it is not overwritten
            this[29] = 0x7F.toByte()
        }
        packet[28] = if (corruptChecksum) 0 else SuzukiChecksum.compute(packet).toByte()
        return packet
    }

    /** Parses a raw 30-byte frame written as dash-separated hex, exactly as captured over the air. */
    private fun parseHexFrame(hex: String): VStromTelemetry? =
        SuzukiPacketParser.parse(
            hex.split('-').map { it.toInt(16).toByte() }.toByteArray()
        )

    @Test
    fun parsesAtTheOffsetsUsedByTheApk() {
        // bytes[5..10]="012345"  bytes[11..16]="000500"  bytes[17..22]="000600"  bytes[24]='4'
        val telemetry = SuzukiPacketParser.parse(
            packetFor("7000" + "012345" + "000500" + "000600" + "X" + "4")
        )

        assertEquals(12345, telemetry?.odometer)
        assertEquals(50.0, telemetry?.tripA ?: -1.0, 0.0)
        assertEquals(60.0, telemetry?.tripB ?: -1.0, 0.0)
        assertEquals(4, telemetry?.fuelBars)
        assertEquals(true, telemetry?.isFuelValid)
    }

    @Test
    fun readsAllSixDigitsOfTheOdometer() {
        // Regression: the old parser read bytes[7..10] (4 digits) and would report 3456.
        val telemetry = SuzukiPacketParser.parse(
            packetFor("7000" + "123456" + "000000" + "000000" + "X" + "6")
        )

        assertEquals(123456, telemetry?.odometer)
    }

    @Test
    fun stripsLeadingZerosWithoutUnderflowingOnAllZero() {
        val telemetry = SuzukiPacketParser.parse(
            packetFor("7000" + "000000" + "000000" + "000000" + "X" + "1")
        )

        assertEquals(0, telemetry?.odometer)
    }

    @Test
    fun readsFuelFromByteTwentyFourNotTwentyThree() {
        // Regression: byte[23] is unused by the APK; the gauge lives at byte[24].
        val telemetry = SuzukiPacketParser.parse(
            packetFor("7000" + "000001" + "000000" + "000000" + "4" + "6")
        )

        assertEquals(6, telemetry?.fuelBars)
    }

    @Test
    fun parsesSpeedFromBytesTwoToFour() {
        val telemetry = SuzukiPacketParser.parse(
            packetFor("7" + "123" + "000001" + "000000" + "000000" + "X" + "2")
        )

        assertEquals(123, telemetry?.speed)
    }

    @Test
    fun parsesRealParkedFrame() {
        // Captured over the air from a V-Strom SX cluster (nRF Connect, 2026-07-10 21:14).
        val telemetry = parseHexFrame(
            "A5-37-30-30-30-30-30-38-38-38-35-30-30-38-36-34-30-30-30-32-31-36-31-4E-34-28-FF-FF-08-7F"
        )

        assertEquals(0, telemetry?.speed)
        assertEquals(8885, telemetry?.odometer)
        assertEquals(864.0, telemetry?.tripA ?: -1.0, 0.0)
        assertEquals(216.1, telemetry?.tripB ?: -1.0, 0.0)
        assertEquals(4, telemetry?.fuelBars)
        assertEquals(true, telemetry?.isFuelValid)
    }

    @Test
    fun parsesRealMovingFrame() {
        // Same ride, at the observed peak speed of 33 km/h.
        val telemetry = parseHexFrame(
            "A5-37-30-33-33-30-30-38-38-38-34-30-30-38-36-33-32-30-30-32-31-35-33-4E-34-57-FF-FF-3E-7F"
        )

        assertEquals(33, telemetry?.speed)
        assertEquals(8884, telemetry?.odometer)
        assertEquals(863.2, telemetry?.tripA ?: -1.0, 0.0)
        assertEquals(215.3, telemetry?.tripB ?: -1.0, 0.0)
        assertEquals(4, telemetry?.fuelBars)
    }

    @Test
    fun invalidFuelIndicatorReturnsSafeInvalidFuelState() {
        val telemetry = SuzukiPacketParser.parse(
            packetFor("7000" + "000001" + "000000" + "000000" + "X" + "Z")
        )

        assertEquals(0, telemetry?.fuelBars)
        assertEquals(-1.0, telemetry?.fuelLiters ?: 0.0, 0.0)
        assertFalse(telemetry?.isFuelValid ?: true)
    }

    @Test
    fun rejectsCorruptedChecksum() {
        val telemetry = SuzukiPacketParser.parse(
            packetFor("7000" + "012345" + "000500" + "000600" + "X" + "4", corruptChecksum = true)
        )

        assertNull(telemetry)
    }

    @Test
    fun rejectsWrongCommandByte() {
        // '5' is the phone->cluster display command, not the inbound cluster command.
        assertNull(
            SuzukiPacketParser.parse(
                packetFor("7000" + "012345" + "000500" + "000600" + "X" + "4", command = '5')
            )
        )
    }

    @Test
    fun rejectsMalformedLengthHeaderAndTerminator() {
        assertNull(SuzukiPacketParser.parse(ByteArray(29)))
        assertNull(SuzukiPacketParser.parse(ByteArray(31)))
        assertNull(SuzukiPacketParser.parse(ByteArray(30)))

        val badHeader = packetFor("7000" + "012345" + "000500" + "000600" + "X" + "4")
        badHeader[0] = 0x00
        assertNull(SuzukiPacketParser.parse(badHeader))

        val badTerminator = packetFor("7000" + "012345" + "000500" + "000600" + "X" + "4")
        badTerminator[29] = 0x00
        assertNull(SuzukiPacketParser.parse(badTerminator))
    }

    @Test
    fun readsEngineTemperatureFromByte25() {
        // Real frames from a ride log: byte[25] 0x2D (45) while warming, 0x52 (82) once hot.
        val warming = parseHexFrame("A5-37-30-31-36-30-31-32-33-33-34-30-30-33-39-35-30-30-30-31-33-35-39-4E-35-2D-FF-FF-0C-7F")
        val hot = parseHexFrame("A5-37-30-30-30-30-31-32-33-33-37-30-30-33-39-37-34-30-30-31-33-38-33-4E-35-52-FF-FF-30-7F")

        assertEquals(45, warming?.engineTempC)
        assertEquals(82, hot?.engineTempC)
    }

    @Test
    fun ignoresImplausibleTemperatureBytes() {
        val frame = packetFor("7000" + "012345" + "000500" + "000600" + "X" + "4")
        frame[25] = 0xFF.toByte()
        frame[28] = SuzukiChecksum.compute(frame).toByte()

        assertNull(SuzukiPacketParser.parse(frame)?.engineTempC)
    }

    @Test
    fun rejectsNonDigitMileageFields() {
        assertNull(SuzukiPacketParser.parse(packetFor("7000" + "0123X5" + "000500" + "000600" + "X" + "4")))
        assertNull(SuzukiPacketParser.parse(packetFor("7000" + "012345" + "000X00" + "000600" + "X" + "4")))
        assertNull(SuzukiPacketParser.parse(packetFor("7000" + "012345" + "000500" + "00060X" + "X" + "4")))
    }
}
