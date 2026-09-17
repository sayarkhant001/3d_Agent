package com.threeDLedger

import com.threeDLedger.logic.NumberGenerator
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testNumberGeneratorPermutations() {
        // 3 distinct digits: 6 permutations
        val p108 = NumberGenerator.permutations("108")
        assertEquals(6, p108.toSet().size)
        assertTrue(p108.contains("108"))
        assertTrue(p108.contains("801"))

        // 2 identical digits: 3 permutations
        val p212 = NumberGenerator.permutations("212")
        assertEquals(3, p212.toSet().size)
        assertTrue(p212.containsAll(listOf("212", "122", "221")))

        // 3 identical digits: 1 permutation
        val p222 = NumberGenerator.permutations("222")
        assertEquals(1, p222.toSet().size)
        assertEquals("222", p222.first())
    }

    @Test
    fun testUnifiedTuwtCalculation() {
        fun calculateTuwt(winningNumber: String): Set<String> {
            val allPerms = NumberGenerator.permutations(winningNumber).toSet() - setOf(winningNumber)
            val numInt = winningNumber.toIntOrNull() ?: 0
            val minus1 = String.format("%03d", if (numInt == 0) 999 else numInt - 1)
            val plus1 = String.format("%03d", if (numInt == 999) 0 else numInt + 1)
            val near = setOf(minus1, plus1) - setOf(winningNumber)
            return allPerms + near
        }

        // 108: 5 permutations + 2 near misses = 7 tut numbers
        val tut108 = calculateTuwt("108")
        assertEquals(7, tut108.size)
        assertFalse(tut108.contains("108"))
        assertTrue(tut108.contains("107"))
        assertTrue(tut108.contains("109"))
        assertTrue(tut108.contains("018"))

        // 212: 2 permutations + 2 near misses = 4 tut numbers
        val tut212 = calculateTuwt("212")
        assertEquals(4, tut212.size)
        assertFalse(tut212.contains("212"))
        assertTrue(tut212.contains("122"))
        assertTrue(tut212.contains("221"))
        assertTrue(tut212.contains("211"))
        assertTrue(tut212.contains("213"))

        // 222: 0 permutations + 2 near misses = 2 tut numbers
        val tut222 = calculateTuwt("222")
        assertEquals(2, tut222.size)
        assertFalse(tut222.contains("222"))
        assertTrue(tut222.contains("221"))
        assertTrue(tut222.contains("223"))

        // Cyclic wrap-around
        val tut000 = calculateTuwt("000")
        assertTrue(tut000.contains("999"))
        assertTrue(tut000.contains("001"))

        val tut999 = calculateTuwt("999")
        assertTrue(tut999.contains("998"))
        assertTrue(tut999.contains("000"))
    }

    @Test
    fun testNumberedVoucherLineRegexStripping() {
        val stripRegex = Regex("""^\s*\d+[\.\)\-:]\s*""")

        val line1 = "1. 108 = 50"
        val stripped1 = line1.replaceFirst(stripRegex, "").trim()
        assertEquals("108 = 50", stripped1)

        val line2 = " 25) 245 = 100 "
        val stripped2 = line2.replaceFirst(stripRegex, "").trim()
        assertEquals("245 = 100", stripped2)

        val line3 = "3- 456-50"
        val stripped3 = line3.replaceFirst(stripRegex, "").trim()
        assertEquals("456-50", stripped3)
    }

    @Test
    fun testTriplesGenerator() {
        val tri = NumberGenerator.tri()
        assertEquals(10, tri.size)
        assertEquals("000", tri.first())
        assertEquals("999", tri.last())
    }
}
