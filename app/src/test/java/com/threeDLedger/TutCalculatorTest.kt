package com.threeDLedger

import com.threeDLedger.logic.TutCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TutCalculatorTest {

    @Test
    fun calculatesPermutationsAndNearMissesForDistinctDigits() {
        val result = TutCalculator.calculate("123")
        assertEquals(listOf("132", "213", "231", "312", "321"), result.permutations)
        assertEquals(listOf("122", "124"), result.nearMisses)
        assertEquals(7, result.allTut.size)
        assertFalse(result.allTut.contains("123"))
    }

    @Test
    fun wrapsCyclicAroundZeroZeroZeroAndNineNineNine() {
        val zeroResult = TutCalculator.calculate("000")
        assertTrue(zeroResult.permutations.isEmpty())
        assertEquals(listOf("001", "999"), zeroResult.nearMisses)

        val nineResult = TutCalculator.calculate("999")
        assertTrue(nineResult.permutations.isEmpty())
        assertEquals(listOf("000", "998"), nineResult.nearMisses)
    }

    @Test
    fun invalidWinningNumberReturnsEmpty() {
        val result = TutCalculator.calculate("12")
        assertTrue(result.permutations.isEmpty())
        assertTrue(result.nearMisses.isEmpty())
        assertTrue(result.allTut.isEmpty())
    }

    @Test
    fun identifiesOverflowMarkersAndNormalizesCommission() {
        assertTrue(TutCalculator.isOverflowMarker("တင်ကွက် (ဒိုင်)"))
        assertTrue(TutCalculator.isOverflowMarker("Upper Agent"))
        assertTrue(TutCalculator.isOverflowMarker("အထက်ဒိုင် ၁"))
        assertFalse(TutCalculator.isOverflowMarker("ဦးမြင့်"))

        assertEquals(0.15, TutCalculator.normalizeCommissionFraction(15.0), 0.0001)
        assertEquals(0.15, TutCalculator.normalizeCommissionFraction(0.15), 0.0001)
        assertEquals(0.0, TutCalculator.normalizeCommissionFraction(-5.0), 0.0001)
    }
}
