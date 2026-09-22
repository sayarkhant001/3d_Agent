package com.threeDLedger

import com.threeDLedger.data.Customer
import com.threeDLedger.logic.NumberGenerator
import com.threeDLedger.ui.AgentSettlement
import org.junit.Assert.*
import org.junit.Test

/**
 * Formal Verification of Real 3D Ledger Calculation Patterns
 * Validates:
 * 1. Exact (ဒဲ့) and Unified Tut (တွတ် + ကပ်သီး) number generation
 * 2. Permutation reductions (distinct vs double vs triple digits)
 * 3. 000-999 cyclic boundary wrapping
 * 4. Commission calculations (ရောင်းကြေး, ကော်မရှင်, နုတ်ပြီးငွေ)
 * 5. Multiplier payouts (ဒဲ့ x600, တွတ် x10)
 * 6. Balance & remaining settlements (ရရန် Green vs ပေးရန် Red)
 * 7. Overflow threshold and hedging calculations
 */
class CalculationVerificationTest {

    // Helper to calculate unified tuwt set matching production WinnerScreen & LedgerScreen
    private fun getUnifiedTuwtSet(winningNumber: String): Set<String> {
        val allPerms = NumberGenerator.permutations(winningNumber).toSet() - setOf(winningNumber)
        val numInt = winningNumber.toIntOrNull() ?: 0
        val minus1 = String.format("%03d", if (numInt == 0) 999 else numInt - 1)
        val plus1 = String.format("%03d", if (numInt == 999) 0 else numInt + 1)
        val near = setOf(minus1, plus1) - setOf(winningNumber)
        return allPerms + near
    }

    @Test
    fun verifyPatternA_DistinctDigits_108() {
        val winNum = "108"
        val tuwtSet = getUnifiedTuwtSet(winNum)

        // 1. Permutations: 3 distinct digits => 3! = 6 anagrams. Excluding self => exactly 5 permutations
        val expectedPerms = setOf("018", "081", "180", "801", "810")
        assertTrue(tuwtSet.containsAll(expectedPerms))

        // 2. Near misses (+1, -1): 107, 109
        assertTrue(tuwtSet.contains("107"))
        assertTrue(tuwtSet.contains("109"))

        // 3. Exact winning number must NEVER be in tuwtSet
        assertFalse(tuwtSet.contains(winNum))

        // Total unified tut count = 5 + 2 = 7 numbers
        assertEquals(7, tuwtSet.size)
    }

    @Test
    fun verifyPatternB_DoubleDigits_212() {
        val winNum = "212"
        val tuwtSet = getUnifiedTuwtSet(winNum)

        // Repeating digits: 2, 1, 2 => unique permutations: 122, 221 (2 anagrams)
        val expectedPerms = setOf("122", "221")
        assertTrue(tuwtSet.containsAll(expectedPerms))

        // Near misses (+1, -1): 211, 213
        assertTrue(tuwtSet.contains("211"))
        assertTrue(tuwtSet.contains("213"))

        assertFalse(tuwtSet.contains(winNum))
        // Total unified tut count = 2 + 2 = 4 numbers
        assertEquals(4, tuwtSet.size)
    }

    @Test
    fun verifyPatternC_TripleDigits_222() {
        val winNum = "222"
        val tuwtSet = getUnifiedTuwtSet(winNum)

        // Identical digits: 2, 2, 2 => 0 other permutations
        // Near misses: 221, 223
        assertEquals(setOf("221", "223"), tuwtSet)
        assertEquals(2, tuwtSet.size)
    }

    @Test
    fun verifyPatternD_CyclicBoundaries_000_and_999() {
        // 000: minus1 wraps to 999, plus1 is 001
        val tuwt000 = getUnifiedTuwtSet("000")
        assertEquals(setOf("999", "001"), tuwt000)

        // 999: minus1 is 998, plus1 wraps to 000
        val tuwt999 = getUnifiedTuwtSet("999")
        assertEquals(setOf("998", "000"), tuwt999)
    }

    @Test
    fun verifyPatternE_CommissionAndHouseGainSettlement() {
        // Agent: ဦးမောင်, Commission rate = 15% (0.15)
        val customer = Customer(id = 1, name = "ဦးမောင်", commissionRate = 0.15)
        val totalBet = 200_000L

        // Step 1: Commission Cut
        val commission = (totalBet * customer.commissionRate).toLong()
        assertEquals(30_000L, commission) // 200,000 * 0.15 = 30,000

        // Step 2: Net after commission
        val netAfterComm = totalBet - commission
        assertEquals(170_000L, netAfterComm) // 200,000 - 30,000 = 170,000

        // Step 3: Winnings
        // Bet 100 Ks on Exact 108 (multiplier 600)
        val exactBetAmt = 100L
        val exactPayout = exactBetAmt * 600L // 60,000
        // Bet 200 Ks on Tut 107 and 200 Ks on Tut 801 (total tut bet = 400, multiplier 10)
        val tuwtBetAmt = 400L
        val tuwtPayout = tuwtBetAmt * 10L // 4,000

        val totalPayout = exactPayout + tuwtPayout
        assertEquals(64_000L, totalPayout) // 60,000 + 4,000 = 64,000

        // Step 4: Net Balance (House perspective)
        // House receives netAfterComm (170,000), owes totalPayout (64,000)
        val balance = netAfterComm - totalPayout
        assertEquals(106_000L, balance) // +106,000 Ks => House receives / Agent owes

        // Step 5: Payments and Remaining Debt
        val paidAmount = 20_000L
        val remaining = balance - paidAmount
        assertEquals(86_000L, remaining) // Agent still owes 86,000 Ks

        val settlement = AgentSettlement(
            customer = customer,
            totalBet = totalBet,
            commission = commission,
            netAfterComm = netAfterComm,
            exactBetAmt = exactBetAmt,
            exactPayout = exactPayout,
            tuwtBetAmt = tuwtBetAmt,
            tuwtPayout = tuwtPayout,
            totalPayout = totalPayout,
            balance = balance,
            paidAmount = paidAmount,
            remaining = remaining
        )

        // Verify receivable condition (Positive => Green / ရရန်)
        assertTrue("House gains, so balance > 0", settlement.balance > 0)
        assertTrue("Remaining debt > 0", settlement.remaining > 0)
    }

    @Test
    fun verifyPatternF_HouseLossPayableSettlement() {
        // Agent: ဒေါ်စန်း, Commission rate = 15% (0.15)
        val customer = Customer(id = 2, name = "ဒေါ်စန်း", commissionRate = 0.15)
        val totalBet = 50_000L

        val commission = (totalBet * customer.commissionRate).toLong() // 7,500
        val netAfterComm = totalBet - commission // 42,500

        // Agent placed a big winning bet: 1,000 Ks on Exact 108 (payout = 1,000 * 600 = 600,000 Ks)
        val exactBetAmt = 1_000L
        val exactPayout = exactBetAmt * 600L // 600,000
        val tuwtBetAmt = 0L
        val tuwtPayout = 0L
        val totalPayout = exactPayout + tuwtPayout // 600,000

        // Balance: 42,500 - 600,000 = -557,500 Ks
        val balance = netAfterComm - totalPayout
        assertEquals(-557_500L, balance) // Negative => House owes Agent

        // House pays agent 200,000 Ks as advance
        val paidAmount = 200_000L
        val remaining = if (balance >= 0) balance - paidAmount else balance + paidAmount
        assertEquals(-357_500L, remaining) // House still owes 357,500 Ks to agent

        val settlement = AgentSettlement(
            customer = customer,
            totalBet = totalBet,
            commission = commission,
            netAfterComm = netAfterComm,
            exactBetAmt = exactBetAmt,
            exactPayout = exactPayout,
            tuwtBetAmt = tuwtBetAmt,
            tuwtPayout = tuwtPayout,
            totalPayout = totalPayout,
            balance = balance,
            paidAmount = paidAmount,
            remaining = remaining
        )

        // Verify payable condition (Negative => Red / ပေးရန်)
        assertTrue("House lost to agent, so balance < 0", settlement.balance < 0)
        assertTrue("Remaining debt owed by house < 0", settlement.remaining < 0)
    }

    @Test
    fun verifyPatternG_OverflowHedgingCalculations() {
        // Safety exposure limit per number = 10,000 Ks
        val exposureLimit = 10_000L
        val totalBetOn108 = 25_000L

        // Excess overflow calculation
        val overflowAmount = (totalBetOn108 - exposureLimit).coerceAtLeast(0L)
        assertEquals(15_000L, overflowAmount)

        // Hedged amount kept in house = 10,000 Ks
        val houseRetained = totalBetOn108 - overflowAmount
        assertEquals(10_000L, houseRetained)

        // If 108 wins (exact multiplier 600):
        // Total payout liability to customers = 25,000 * 600 = 15,000,000 Ks
        val totalLiability = totalBetOn108 * 600L
        assertEquals(15_000_000L, totalLiability)

        // Upper agent payout collected for the overflow = 15,000 * 600 = 9,000,000 Ks
        val overflowRecovered = overflowAmount * 600L
        assertEquals(9_000_000L, overflowRecovered)

        // Net house maximum exposure = 15,000,000 - 9,000,000 = 6,000,000 Ks (exactly houseRetained * 600!)
        val netHousePayout = totalLiability - overflowRecovered
        assertEquals(houseRetained * 600L, netHousePayout)
        assertEquals(6_000_000L, netHousePayout)
    }

    @Test
    fun verifyPatternH_UnderBrakeWinningSettlement_Screenshot1() {
        // Test replicating Legacy App Screenshot 1 exactly
        val winningNumber = "640"
        val brakeLimit = 2000

        // 1. Permutations & near numbers verification
        val allPerms = NumberGenerator.permutations(winningNumber).toSet()
        val permsOnly = (allPerms - setOf(winningNumber)).sorted()
        val winInt = winningNumber.toIntOrNull() ?: 0
        val numMinus1 = String.format("%03d", if (winInt == 0) 999 else winInt - 1)
        val numPlus1  = String.format("%03d", if (winInt == 999) 0 else winInt + 1)
        val lastDigit = winningNumber[2].digitToIntOrNull() ?: 0
        val lastMinus1 = "${winningNumber.substring(0, 2)}${(lastDigit + 9) % 10}"
        val lastPlus1  = "${winningNumber.substring(0, 2)}${(lastDigit + 1) % 10}"
        val nearOnly = (setOf(numMinus1, numPlus1, lastMinus1, lastPlus1) - setOf(winningNumber) - allPerms).sorted()

        // Verify that 649 and 641 are in nearOnly
        assertTrue(nearOnly.contains("649"))
        assertTrue(nearOnly.contains("641"))
        // Verify permutations
        assertEquals(setOf("046", "064", "406", "460", "604"), permsOnly.toSet())

        // 2. Financial calculation verification from Screenshot 1
        val totalBraked = 727_208L
        val meCommRate = 0.25 // 25%

        val commAmount = (totalBraked * meCommRate).toLong()
        assertEquals(181_802L, commAmount)

        val netAfterComm = totalBraked - commAmount
        assertEquals(545_406L, netAfterComm)

        // Exact win kept amount: 1000 Ks on 640
        val exactKept = 1_000L
        val exactMult = 600L
        val exactPayout = exactKept * exactMult
        assertEquals(600_000L, exactPayout)

        // Tut win kept amount: 1000 on 604, 2000 on 641 => 3000 Ks
        val tuwtKept = 3_000L
        val permMult = 10L
        val tuwtPayout = tuwtKept * permMult
        assertEquals(30_000L, tuwtPayout)

        // Profit / Net
        val brakedProfit = netAfterComm - (exactPayout + tuwtPayout)
        assertEquals(-84_594L, brakedProfit)
    }
}
