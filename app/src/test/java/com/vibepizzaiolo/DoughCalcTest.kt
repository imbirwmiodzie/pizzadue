package com.vibepizzaiolo

import com.vibepizzaiolo.dough.DoughCalc
import org.junit.Assert.assertEquals
import org.junit.Test

class DoughCalcTest {
    @Test fun fourBallsOf250AtDefaults() {
        val r = DoughCalc.calc(4, 250.0, 70.0, 4.0, 0.5)
        assertEquals("573", DoughCalc.formatGrams(r.flour))
        assertEquals("401", DoughCalc.formatGrams(r.water))
        assertEquals("22.9", DoughCalc.formatGrams(r.salt))
        assertEquals("2.9", DoughCalc.formatGrams(r.yeast))
        assertEquals(1000.0, r.total, 1e-9)
    }

    @Test fun matchesTheOriginalRatio10to7() {
        // 10 flour : 7 water : 0.4 salt : 0.05 yeast
        val r = DoughCalc.calc(1, 17.45, 70.0, 4.0, 0.5)
        assertEquals(10.0, r.flour, 1e-9)
        assertEquals(7.0, r.water, 1e-9)
        assertEquals(0.4, r.salt, 1e-9)
        assertEquals(0.05, r.yeast, 1e-9)
    }

    @Test fun hydration65() {
        val r = DoughCalc.calc(4, 250.0, 65.0, 4.0, 0.5)
        assertEquals("590", DoughCalc.formatGrams(r.flour))
        assertEquals("383", DoughCalc.formatGrams(r.water))
    }
}
