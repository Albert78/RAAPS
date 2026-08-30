package de.dh.raaps.common.model.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BgTypeTest {

    @Test
    fun testBgValuePrecision() {
        val val1 = BgValue.fromMgDl(100.0)
        val delta = BgDelta.fromMgDl(0.05)
        val result = val1 + delta
        
        assertEquals(100.05, result.mgdl, 0.001)
        assertEquals(100, result.mgdlInt)
        assertEquals(10005, result.scaled.toInt())
    }

    @Test
    fun testBgValueRounding() {
        val val1 = BgValue.fromMgDl(100.05)
        assertEquals(100, val1.mgdlInt)
        
        val val2 = BgValue.fromMgDl(100.55)
        assertEquals(101, val2.mgdlInt)
    }

    @Test
    fun testBgDeltaPrecision() {
        val d1 = BgDelta.fromMgDl(0.05)
        val d2 = BgDelta.fromMgDl(0.05)
        val result = d1 + d2
        
        assertEquals(0.10, result.mgdl, 0.001)
        assertEquals(10, result.scaled.toInt())
    }

    @Test
    fun testBgDeltaNegative() {
        val d1 = BgDelta.fromMgDl(-1.0)
        val d2 = BgDelta.fromMgDl(0.05)
        val result = d1 + d2
        
        assertEquals(-0.95, result.mgdl, 0.001)
        assertEquals(-95, result.scaled.toInt())
    }
}