package com.adithyapaib.sxv

import com.adithyapaib.sxv.ble.ClusterWriter
import org.junit.Assert.assertEquals
import org.junit.Test

class ClusterWriterTest {
    @Test
    fun hexFormatterIsStable() {
        val hex = ClusterWriter.toHex(byteArrayOf(0x01, 0x0F, 0x7F))
        assertEquals("01 0F 7F", hex)
    }
}
