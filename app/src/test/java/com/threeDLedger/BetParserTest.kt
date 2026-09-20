package com.threeDLedger

import com.threeDLedger.ui.parsePastedLine
import org.junit.Assert.*
import org.junit.Test

class BetParserTest {

    @Test
    fun testUserReported15LinesWithoutIndex() {
        // Line 1: 723-372-245-309 = 2000 (must NOT skip 723 or 245!)
        val l1 = parsePastedLine("723-372-245-309 = 2000")
        assertEquals(4, l1.size)
        assertEquals("723" to 2000, l1[0])
        assertEquals("372" to 2000, l1[1])
        assertEquals("245" to 2000, l1[2])
        assertEquals("309" to 2000, l1[3])

        // Line 2: 446=1000
        val l2 = parsePastedLine("446=1000")
        assertEquals(1, l2.size)
        assertEquals("446" to 1000, l2[0])

        // Line 3: 235-615 = 3000 (must NOT skip 235!)
        val l3 = parsePastedLine("235-615 = 3000")
        assertEquals(2, l3.size)
        assertEquals("235" to 3000, l3[0])
        assertEquals("615" to 3000, l3[1])

        // Line 4: 907=4000
        val l4 = parsePastedLine("907=4000")
        assertEquals(listOf("907" to 4000), l4)

        // Line 5: 110=5000
        val l5 = parsePastedLine("110=5000")
        assertEquals(listOf("110" to 5000), l5)

        // Line 6: 149-223=1000 (must NOT skip 149!)
        val l6 = parsePastedLine("149-223=1000")
        assertEquals(2, l6.size)
        assertEquals("149" to 1000, l6[0])
        assertEquals("223" to 1000, l6[1])

        // Line 7: 807=2000
        val l7 = parsePastedLine("807=2000")
        assertEquals(listOf("807" to 2000), l7)

        // Line 8: 813-724-648-247-369 = 5000 (must NOT skip 813!)
        val l8 = parsePastedLine("813-724-648-247-369 = 5000")
        assertEquals(5, l8.size)
        assertEquals("813" to 5000, l8[0])
        assertEquals("724" to 5000, l8[1])
        assertEquals("648" to 5000, l8[2])
        assertEquals("247" to 5000, l8[3])
        assertEquals("369" to 5000, l8[4])

        // Line 9: 472=10000
        val l9 = parsePastedLine("472=10000")
        assertEquals(listOf("472" to 10000), l9)

        // Line 10: 577=1500
        val l10 = parsePastedLine("577=1500")
        assertEquals(listOf("577" to 1500), l10)

        // Line 11: 205-108= 2000 (must NOT skip 205!)
        val l11 = parsePastedLine("205-108= 2000")
        assertEquals(2, l11.size)
        assertEquals("205" to 2000, l11[0])
        assertEquals("108" to 2000, l11[1])

        // Line 12: 764-220 = 3000 (must NOT skip 764!)
        val l12 = parsePastedLine("764-220 = 3000")
        assertEquals(2, l12.size)
        assertEquals("764" to 3000, l12[0])
        assertEquals("220" to 3000, l12[1])

        // Line 13: 456=5000r1000 (456 direct at 5000, perms at 1000)
        val l13 = parsePastedLine("456=5000r1000")
        assertEquals(6, l13.size)
        assertEquals("456" to 5000, l13[0])
        val perms = l13.drop(1).map { it.first }.toSet()
        assertEquals(setOf("465", "546", "564", "645", "654"), perms)
        assertTrue(l13.drop(1).all { it.second == 1000 })

        // Line 14: 185-217-378-549 = 10000 (must NOT skip 185!)
        val l14 = parsePastedLine("185-217-378-549 = 10000")
        assertEquals(4, l14.size)
        assertEquals("185" to 10000, l14[0])
        assertEquals("217" to 10000, l14[1])
        assertEquals("378" to 10000, l14[2])
        assertEquals("549" to 10000, l14[3])

        // Line 15: 895=2500
        val l15 = parsePastedLine("895=2500")
        assertEquals(listOf("895" to 2500), l15)
    }

    @Test
    fun testTwoNumbersHyphenFormat() {
        // Specifically tested from user prompt: "245-309 = 2000 it skips 245"
        val res = parsePastedLine("245-309 = 2000")
        assertEquals(2, res.size)
        assertEquals("245" to 2000, res[0])
        assertEquals("309" to 2000, res[1])
    }

    @Test
    fun testListNumeralsDistinguishedFromBettingNumbers() {
        // Format 1: "1. ", "2. ", "10. "
        val res1 = parsePastedLine("1. 723-372-245-309 = 2000")
        assertEquals(4, res1.size)
        assertEquals("723" to 2000, res1[0])
        assertEquals("372" to 2000, res1[1])
        assertEquals("245" to 2000, res1[2])
        assertEquals("309" to 2000, res1[3])

        val res2 = parsePastedLine("2. 446=1000")
        assertEquals(listOf("446" to 1000), res2)

        val res10 = parsePastedLine("10. 577=1500")
        assertEquals(listOf("577" to 1500), res10)

        // Format 2: "1) ", "2) "
        val resParen = parsePastedLine("1) 235-615 = 3000")
        assertEquals(listOf("235" to 3000, "615" to 3000), resParen)

        // Format 3: "(1) ", "[1] "
        val resBracket = parsePastedLine("(1) 110=5000")
        assertEquals(listOf("110" to 5000), resBracket)

        val resSqBracket = parsePastedLine("[3] 149-223=1000")
        assertEquals(listOf("149" to 1000, "223" to 1000), resSqBracket)

        // Format 4: "1: "
        val resColon = parsePastedLine("1: 807=2000")
        assertEquals(listOf("807" to 2000), resColon)

        // Format 5: "1 - " (single digit followed by dash and space)
        val resDash = parsePastedLine("1 - 245-309 = 2000")
        assertEquals(listOf("245" to 2000, "309" to 2000), resDash)

        // Format 6: Myanmar numerals "၁။ "
        val resMm = parsePastedLine("၁။ ၄၄၆=၁၀၀၀")
        assertEquals(listOf("446" to 1000), resMm)
    }
}
