package com.threeDLedger

import com.threeDLedger.ui.LineParseResult
import com.threeDLedger.ui.parsePastedLine
import com.threeDLedger.ui.validateAndParseLine
import com.threeDLedger.ui.validatePastedText
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

    @Test
    fun testDotSeparatedNumbersMustNotSkipFirstNumber() {
        // User reported: 123.345.678=1000 was skipping 123
        val res = parsePastedLine("123.345.678=1000")
        assertEquals(3, res.size)
        assertEquals("123" to 1000, res[0])
        assertEquals("345" to 1000, res[1])
        assertEquals("678" to 1000, res[2])

        // Dot-separated with spaces: 123. 345. 678 = 1000
        val resSpaces = parsePastedLine("123. 345. 678 = 1000")
        assertEquals(3, resSpaces.size)
        assertEquals("123" to 1000, resSpaces[0])
        assertEquals("345" to 1000, resSpaces[1])
        assertEquals("678" to 1000, resSpaces[2])

        // Dot-separated with 4 numbers: 723.372.245.309 = 2000
        val res4 = parsePastedLine("723.372.245.309 = 2000")
        assertEquals(4, res4.size)
        assertEquals("723" to 2000, res4[0])
        assertEquals("372" to 2000, res4[1])
        assertEquals("245" to 2000, res4[2])
        assertEquals("309" to 2000, res4[3])
    }

    @Test
    fun testDirectAndPermutationDualAmounts() {
        // User reported: 862=5000/8000
        val res = parsePastedLine("862=5000/8000")
        assertEquals(6, res.size)
        assertEquals("862" to 5000, res[0])
        val perms = res.drop(1).map { it.first }.toSet()
        assertEquals(setOf("826", "682", "628", "286", "268"), perms)
        assertTrue(res.drop(1).all { it.second == 8000 })

        // With spaces: 862 = 5000 / 8000
        val resSpaced = parsePastedLine("862 = 5000 / 8000")
        assertEquals(6, resSpaced.size)
        assertEquals("862" to 5000, resSpaced[0])
        assertTrue(resSpaced.drop(1).all { it.second == 8000 })
    }

    @Test
    fun testDeclineLessThan3DigitNumbers() {
        // Direct bet with 2 digits: 25=1000
        val r1 = validateAndParseLine("25=1000", 1)
        assertTrue(r1 is LineParseResult.Error)
        assertEquals("ဂဏန်း ၃ လုံး မပြည့်ပါ", (r1 as LineParseResult.Error).error.reason)
        assertEquals(listOf("25"), r1.error.invalidTokens)

        // Single digit: 7=500
        val r2 = validateAndParseLine("7=500", 2)
        assertTrue(r2 is LineParseResult.Error)
        assertEquals("ဂဏန်း ၃ လုံး မပြည့်ပါ", (r2 as LineParseResult.Error).error.reason)
        assertEquals(listOf("7"), r2.error.invalidTokens)

        // Hyphen-separated with one 2-digit number: 123-45=1000
        val r3 = validateAndParseLine("123-45=1000", 3)
        assertTrue(r3 is LineParseResult.Error)
        assertEquals("ဂဏန်း ၃ လုံး မပြည့်ပါ", (r3 as LineParseResult.Error).error.reason)
        assertEquals(listOf("45"), r3.error.invalidTokens)

        // Dot-separated with one 2-digit number: 123.45.678=1000
        val r4 = validateAndParseLine("123.45.678=1000", 4)
        assertTrue(r4 is LineParseResult.Error)
        assertEquals(listOf("45"), (r4 as LineParseResult.Error).error.invalidTokens)

        // With list number prefix: 1. 25=1000
        val r5 = validateAndParseLine("1. 25=1000", 5)
        assertTrue(r5 is LineParseResult.Error)
        assertEquals(listOf("25"), (r5 as LineParseResult.Error).error.invalidTokens)

        // With R / round: 25R=1000 or 25/5000
        val r6 = validateAndParseLine("25R=1000", 6)
        assertTrue(r6 is LineParseResult.Error)
        assertEquals(listOf("25"), (r6 as LineParseResult.Error).error.invalidTokens)

        val r7 = validateAndParseLine("25/5000", 7)
        assertTrue(r7 is LineParseResult.Error)
        assertEquals(listOf("25"), (r7 as LineParseResult.Error).error.invalidTokens)
    }

    @Test
    fun testDeclineMoreThan3DigitNumbers() {
        val r1 = validateAndParseLine("1234=1000", 1)
        assertTrue(r1 is LineParseResult.Error)
        assertEquals("ဂဏန်း ၃ လုံးထက် ပိုနေပါသည်", (r1 as LineParseResult.Error).error.reason)
        assertEquals(listOf("1234"), r1.error.invalidTokens)

        val r2 = validateAndParseLine("123-4567=1000", 2)
        assertTrue(r2 is LineParseResult.Error)
        assertEquals(listOf("4567"), (r2 as LineParseResult.Error).error.invalidTokens)
    }

    @Test
    fun testDeclineWrongFormatLines() {
        // Missing amount
        val r1 = validateAndParseLine("123=", 1)
        assertTrue(r1 is LineParseResult.Error)

        val r2 = validateAndParseLine("123", 2)
        assertTrue(r2 is LineParseResult.Error)

        // Missing betting number
        val r3 = validateAndParseLine("=1000", 3)
        assertTrue(r3 is LineParseResult.Error)

        // Non-digits in bet numbers
        val r4 = validateAndParseLine("abc=1000", 4)
        assertTrue(r4 is LineParseResult.Error)
        assertEquals("ပုံစံမမှန်ပါ", (r4 as LineParseResult.Error).error.reason)

        // Zero amount
        val r5 = validateAndParseLine("123=0", 5)
        assertTrue(r5 is LineParseResult.Error)
    }

    @Test
    fun testDeclineWholePasteIfAnyLineHasError() {
        val pastedText = """
            1. 723-372-245-309 = 2000
            2. 446=1000
            3. 25=3000
            4. 907=4000
            5. 110=5000
        """.trimIndent()

        val result = validatePastedText(pastedText)
        assertFalse("Entire paste must be declined when line 3 has less than 3 digits", result.isValid)
        assertTrue("Valid bets must be empty when declined", result.validBets.isEmpty())
        assertEquals(1, result.errors.size)
        assertEquals(3, result.errors[0].lineNumber)
        assertEquals("ဂဏန်း ၃ လုံး မပြည့်ပါ", result.errors[0].reason)
        assertEquals(listOf("25"), result.errors[0].invalidTokens)
    }

    @Test
    fun testAcceptValidPasteWithMetadataLines() {
        val pastedText = """
            ----------------
            3D VOUCHER
            Date: 22/09/2026
            Time: 14:30:00
            ----------------
            123 = 1000
            456 = 2000
            ----------------
            Total = 3000
            ----------------
        """.trimIndent()

        val result = validatePastedText(pastedText)
        assertTrue(result.isValid)
        assertEquals(0, result.errors.size)
        assertEquals(2, result.validBets.size)
        assertEquals("123" to 1000, result.validBets[0])
        assertEquals("456" to 2000, result.validBets[1])
    }

    @Test
    fun testPreventMistakenAmountsAndNumbers() {
        // 1. Without explicit delimiter or currency suffix, a list of 3-digit numbers MUST NOT assume the last number is an amount!
        val r1 = validateAndParseLine("123 456 789", 1)
        assertTrue("Lines with only 3D numbers and no explicit amount delimiter must fail", r1 is LineParseResult.Error)
        assertTrue((r1 as LineParseResult.Error).error.reason.contains("ထိုးကြေး မပါရှိပါ"))

        val r2 = validateAndParseLine("123, 456, 789", 2)
        assertTrue(r2 is LineParseResult.Error)

        val r3 = validateAndParseLine("123-456-789", 3)
        assertTrue(r3 is LineParseResult.Error)

        // 2. Explicit prefix amount with 'ဖိုး' or 'ks'
        val rPrefix = validateAndParseLine("1000ဖိုး 123 456", 4)
        assertTrue(rPrefix is LineParseResult.Success)
        val betsPrefix = (rPrefix as LineParseResult.Success).bets
        assertEquals(2, betsPrefix.size)
        assertEquals("123" to 1000, betsPrefix[0])
        assertEquals("456" to 1000, betsPrefix[1])

        // 3. Amount on left side of '='
        val rLeftAmount = validateAndParseLine("1000 = 123 456", 5)
        assertTrue(rLeftAmount is LineParseResult.Success)
        val betsLeft = (rLeftAmount as LineParseResult.Success).bets
        assertEquals(2, betsLeft.size)
        assertEquals("123" to 1000, betsLeft[0])
        assertEquals("456" to 1000, betsLeft[1])

        // 4. Amount on right side of '='
        val rRightAmount = validateAndParseLine("123 456 = 1000", 6)
        assertTrue(rRightAmount is LineParseResult.Success)
        val betsRight = (rRightAmount as LineParseResult.Success).bets
        assertEquals(2, betsRight.size)
        assertEquals("123" to 1000, betsRight[0])
        assertEquals("456" to 1000, betsRight[1])

        // 5. 3-digit amount with explicit 'Ks' or 'ကျပ်'
        val rCurrency = validateAndParseLine("123 456 500 Ks", 7)
        assertTrue(rCurrency is LineParseResult.Success)
        val betsCurrency = (rCurrency as LineParseResult.Success).bets
        assertEquals(2, betsCurrency.size)
        assertEquals("123" to 500, betsCurrency[0])
        assertEquals("456" to 500, betsCurrency[1])

        // 6. 4-digit amount without delimiter (cannot be 3D number)
        val r4Digit = validateAndParseLine("123 456 1000", 8)
        assertTrue(r4Digit is LineParseResult.Success)
        val bets4Digit = (r4Digit as LineParseResult.Success).bets
        assertEquals(2, bets4Digit.size)
        assertEquals("123" to 1000, bets4Digit[0])
        assertEquals("456" to 1000, bets4Digit[1])
    }

    @Test
    fun testQuickBetMyanmarNumbersSupport() {
        // 1. Standard Myanmar digits with '='
        val r1 = validateAndParseLine("၁၂၃ = ၁၀၀၀", 1)
        assertTrue(r1 is LineParseResult.Success)
        val bets1 = (r1 as LineParseResult.Success).bets
        assertEquals(1, bets1.size)
        assertEquals("123" to 1000, bets1[0])

        // 2. Myanmar numbers with 'R' for round (same rule as English numbers)
        val rRound = validateAndParseLine("၁၂၃R = ၁၀၀၀", 2)
        assertTrue(rRound is LineParseResult.Success)
        val betsRound = (rRound as LineParseResult.Success).bets
        assertEquals(6, betsRound.size) // 123 + 5 permutations
        assertTrue(betsRound.any { it.first == "123" && it.second == 1000 })
        assertTrue(betsRound.any { it.first == "321" && it.second == 1000 })

        // 3. Myanmar zero digit '၀' (U+1040)
        val rZero = validateAndParseLine("၀၁၂ = ၅၀၀", 3)
        assertTrue(rZero is LineParseResult.Success)
        val betsZero = (rZero as LineParseResult.Success).bets
        assertEquals(1, betsZero.size)
        assertEquals("012" to 500, betsZero[0])

        // 4. Myanmar comma delimiter '၊'
        val rComma = validateAndParseLine("၁၂၃၊ ၄၅၆ = ၁၀၀၀", 4)
        assertTrue(rComma is LineParseResult.Success)
        val betsComma = (rComma as LineParseResult.Success).bets
        assertEquals(2, betsComma.size)
        assertEquals("123" to 1000, betsComma[0])
        assertEquals("456" to 1000, betsComma[1])

        // 5. Myanmar numbers with compound amount: "၁၂၃ = ၅၀၀၀R၁၀၀၀"
        val rCompound = validateAndParseLine("၁၂၃ = ၅၀၀၀R၁၀၀၀", 5)
        assertTrue(rCompound is LineParseResult.Success)
        val betsCompound = (rCompound as LineParseResult.Success).bets
        assertEquals(6, betsCompound.size)
        assertEquals("123" to 5000, betsCompound[0])
        assertTrue(betsCompound.drop(1).all { it.second == 1000 })

        // 6. Strict enforcement: less than 3 digits in Myanmar numbers must be declined
        val rLess = validateAndParseLine("၁၂ = ၁၀၀၀", 6)
        assertTrue("Less than 3 digits must be declined", rLess is LineParseResult.Error)

        // 7. Strict enforcement: more than 3 digits in Myanmar numbers must be declined
        val rMore = validateAndParseLine("၁၂၃၄ = ၁၀၀၀", 7)
        assertTrue("More than 3 digits must be declined", rMore is LineParseResult.Error)

        // 8. Myanmar numbers with thousand separator commas in amount
        val rCommaAmt = validateAndParseLine("၁၂၃ = ၁၀,၀၀၀", 8)
        assertTrue(rCommaAmt is LineParseResult.Success)
        val betsCommaAmt = (rCommaAmt as LineParseResult.Success).bets
        assertEquals(listOf("123" to 10000), betsCommaAmt)

        // 9. "No need myanmar letters just numbers": Myanmar letters in bet are rejected
        val rLetters = validateAndParseLine("၁၂၃ ပတ်လည် = ၁၀၀၀", 9)
        assertTrue("Myanmar letters must be rejected as invalid format", rLetters is LineParseResult.Error)

        val rDirectLetters = validateAndParseLine("၁၂၃ ဒဲ့ = ၁၀၀၀", 10)
        assertTrue("Myanmar letters must be rejected as invalid format", rDirectLetters is LineParseResult.Error)

        // 10. Whole paste with Myanmar numbers and standard operators
        val pasteBlock = """
            ၁။ ၁၂၃ = ၁၀၀၀
            ၂။ ၄၅၆R = ၁၀၀၀
            ၃။ ၀၁၂ = ၅,၀၀၀
        """.trimIndent()
        val pasteRes = validatePastedText(pasteBlock)
        assertTrue("Paste result must be valid", pasteRes.isValid)
        assertTrue("Must parse valid bets", pasteRes.validBets.isNotEmpty())
        assertEquals(0, pasteRes.errors.size)
    }

    @Test
    fun testSeventeenLinesPastedBetsExactFromUser() {
        val pasted = """
            723-372-245-309 = 2000
            446=1000
            235-615 = 3000
            907=4000
            110=5000
            149-223=1000
            807=2000
            813-724-648-247-369 = 5000
            472=10000
            577=1500
            205-108= 2000
            764-220 = 3000
            456=5000r1000
            185-217-378-549 = 10000
            895=2500
            464r10000
            ၆၅၆r၁၀၀၀
        """.trimIndent()

        // Test line 16 individually
        val l16 = parsePastedLine("464r10000")
        assertEquals(3, l16.size)
        assertEquals(setOf("464", "446", "644"), l16.map { it.first }.toSet())
        assertTrue(l16.all { it.second == 10000 })

        // Test line 17 individually (Myanmar numerals)
        val l17 = parsePastedLine("၆၅၆r၁၀၀၀")
        assertEquals(3, l17.size)
        assertEquals(setOf("656", "665", "566"), l17.map { it.first }.toSet())
        assertTrue(l17.all { it.second == 1000 })

        // Test the entire 17-line block
        val result = validatePastedText(pasted)
        assertTrue("Paste must be valid", result.isValid)
        assertEquals(0, result.errors.size)
        assertEquals(40, result.validBets.size)
        assertEquals(160000, result.validBets.sumOf { it.second })
    }

    @Test
    fun testRoundNumberWithoutEqualsSignVariations() {
        // 123r1000 -> 6 perms
        val l1 = parsePastedLine("123r1000")
        assertEquals(6, l1.size)
        assertEquals(setOf("123", "132", "213", "231", "312", "321"), l1.map { it.first }.toSet())
        assertTrue(l1.all { it.second == 1000 })

        // 111r500 -> 1 bet
        val l2 = parsePastedLine("111r500")
        assertEquals(1, l2.size)
        assertEquals("111" to 500, l2[0])

        // Space before/after r: 464 R 10000
        val l3 = parsePastedLine("464 R 10000")
        assertEquals(3, l3.size)
        assertTrue(l3.all { it.second == 10000 })

        // Myanmar with spaces: ၆၅၆ R ၁၀၀၀
        val l4 = parsePastedLine("၆၅၆ R ၁၀၀၀")
        assertEquals(3, l4.size)
        assertTrue(l4.all { it.second == 1000 })
    }
}


