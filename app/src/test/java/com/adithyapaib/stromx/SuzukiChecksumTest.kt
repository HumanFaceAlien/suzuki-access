package com.adithyapaib.stromx

import com.adithyapaib.stromx.ble.SuzukiChecksum
import org.junit.Assert.assertEquals
import org.junit.Test

class SuzukiChecksumTest {

    @Test
    fun sumsBytesOneToTwentySevenAndIgnoresTheHeader() {
        val packet = ByteArray(30).apply {
            this[0] = 0xA5.toByte()   // must NOT contribute
            for (i in 1..27) this[i] = 1
            this[28] = 0x7F.toByte()
            this[29] = 0x7F.toByte()
        }

        assertEquals(27, SuzukiChecksum.compute(packet).toInt() and 0xFF)
    }

    @Test
    fun headerByteIsNotFoldedIntoTheSum() {
        val a = ByteArray(30).apply { for (i in 1..27) this[i] = 1 }
        val b = ByteArray(30).apply { this[0] = 0x42; for (i in 1..27) this[i] = 1 }

        // The previous implementation summed 0..27, so this pair differed by 0xA5.
        assertEquals(
            SuzukiChecksum.compute(a).toInt() and 0xFF,
            SuzukiChecksum.compute(b).toInt() and 0xFF
        )
    }

    @Test
    fun reducesModTwoHundredFiftySix() {
        val packet = ByteArray(30).apply { for (i in 1..27) this[i] = 20 }  // 27 * 20 = 540

        assertEquals(540 and 0xFF, SuzukiChecksum.compute(packet).toInt() and 0xFF)
    }

    @Test
    fun onesComplementIsTwoHundredFiftyFiveMinusThePlainSum() {
        val packet = ByteArray(30).apply { for (i in 1..27) this[i] = 1 }

        val plain = SuzukiChecksum.compute(packet, SuzukiChecksum.Mode.PLAIN_SUM).toInt() and 0xFF
        val complement =
            SuzukiChecksum.compute(packet, SuzukiChecksum.Mode.ONES_COMPLEMENT).toInt() and 0xFF

        assertEquals(255 - plain, complement)
    }

    @Test
    fun onesComplementStaysInRangeWhenThePlainSumWraps() {
        val packet = ByteArray(30).apply { for (i in 1..27) this[i] = 0xFF.toByte() }  // 27 * 255 = 6885

        val plain = SuzukiChecksum.compute(packet, SuzukiChecksum.Mode.PLAIN_SUM).toInt() and 0xFF
        val complement =
            SuzukiChecksum.compute(packet, SuzukiChecksum.Mode.ONES_COMPLEMENT).toInt() and 0xFF

        assertEquals(6885 and 0xFF, plain)
        assertEquals(255 - plain, complement)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPacketsTooShortToChecksum() {
        SuzukiChecksum.compute(ByteArray(27))
    }
}
