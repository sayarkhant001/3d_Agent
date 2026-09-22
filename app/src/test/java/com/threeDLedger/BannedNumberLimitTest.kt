package com.threeDLedger

import com.threeDLedger.data.BannedLimitRemoval
import com.threeDLedger.data.BannedNumber
import com.threeDLedger.data.Bet
import org.junit.Assert.*
import org.junit.Test

class BannedNumberLimitTest {

    /**
     * Pure algorithmic test reproducing the exact logic in MainViewModel.validateAndFilterBetsWithBannedLimits
     */
    private fun validateAndFilterBets(
        incomingBets: List<Bet>,
        bannedNumbers: List<BannedNumber>,
        dbTotals: Map<String, Int> = emptyMap(),
        pendingSessionAmounts: Map<String, Int> = emptyMap()
    ): Pair<List<Bet>, List<BannedLimitRemoval>> {
        val bannedMap = bannedNumbers.associateBy { it.number }
        if (bannedMap.isEmpty()) {
            return Pair(incomingBets, emptyList())
        }

        val accumulatedAmounts = dbTotals.toMutableMap()
        pendingSessionAmounts.forEach { (num, amt) ->
            accumulatedAmounts[num] = (accumulatedAmounts[num] ?: 0) + amt
        }

        val validBets = mutableListOf<Bet>()
        val removals = mutableListOf<BannedLimitRemoval>()

        for (bet in incomingBets) {
            val banned = bannedMap[bet.number]
            if (banned == null) {
                validBets.add(bet)
                accumulatedAmounts[bet.number] = (accumulatedAmounts[bet.number] ?: 0) + bet.amount
                continue
            }

            val limit = banned.amountLimit
            val currentAccum = accumulatedAmounts[bet.number] ?: 0

            if (limit <= 0) {
                // Completely Banned
                removals.add(
                    BannedLimitRemoval(
                        number = bet.number,
                        attemptedAmount = bet.amount,
                        limitAmount = 0,
                        currentBetTotal = currentAccum,
                        acceptedAmount = 0,
                        removedAmount = bet.amount,
                        reason = "လုံးဝပိတ်ထားသော ဂဏန်းဖြစ်ပါသည်"
                    )
                )
            } else {
                val remainingAllowed = (limit - currentAccum).coerceAtLeast(0)
                if (remainingAllowed <= 0) {
                    // Limit already reached or exceeded
                    removals.add(
                        BannedLimitRemoval(
                            number = bet.number,
                            attemptedAmount = bet.amount,
                            limitAmount = limit,
                            currentBetTotal = currentAccum,
                            acceptedAmount = 0,
                            removedAmount = bet.amount,
                            reason = "ကန့်သတ်ငွေ %,d ကျပ် ပြည့်သွားပါသည် (လက်ရှိ: %,d ကျပ်)".format(limit, currentAccum)
                        )
                    )
                } else if (bet.amount <= remainingAllowed) {
                    validBets.add(bet)
                    accumulatedAmounts[bet.number] = currentAccum + bet.amount
                } else {
                    val excess = bet.amount - remainingAllowed
                    validBets.add(bet.copy(amount = remainingAllowed))
                    accumulatedAmounts[bet.number] = currentAccum + remainingAllowed
                    removals.add(
                        BannedLimitRemoval(
                            number = bet.number,
                            attemptedAmount = bet.amount,
                            limitAmount = limit,
                            currentBetTotal = currentAccum,
                            acceptedAmount = remainingAllowed,
                            removedAmount = excess,
                            reason = "ကန့်သတ်ငွေ %,d ကျပ် ပြည့်ရန် %,d ကျပ်သာ လက်ခံပြီး ပိုငွေ %,d ကျပ် ဖယ်ထုတ်လိုက်ပါသည်".format(
                                limit, remainingAllowed, excess
                            )
                        )
                    )
                }
            }
        }

        return Pair(validBets, removals)
    }

    @Test
    fun testCompleteBan_amountLimitZero() {
        val banned = listOf(BannedNumber(id = 1, number = "123", amountLimit = 0))
        val incoming = listOf(Bet(voucherId = 0, number = "123", amount = 5000))

        val (valid, removals) = validateAndFilterBets(incoming, banned)

        assertTrue("No bets should be accepted for completely banned number", valid.isEmpty())
        assertEquals(1, removals.size)
        val removal = removals.first()
        assertEquals("123", removal.number)
        assertEquals(5000, removal.attemptedAmount)
        assertEquals(0, removal.acceptedAmount)
        assertEquals(5000, removal.removedAmount)
        assertEquals(0, removal.limitAmount)
    }

    @Test
    fun testAmountLimitCeiling_partialExcessAndFullBlock() {
        val limit = 10000
        val banned = listOf(BannedNumber(id = 1, number = "789", amountLimit = limit))

        // Step 1: First bet 6,000 Ks — fits within 10,000 Ks
        val step1Bets = listOf(Bet(voucherId = 0, number = "789", amount = 6000))
        val (valid1, removals1) = validateAndFilterBets(step1Bets, banned)
        assertEquals(1, valid1.size)
        assertEquals(6000, valid1.first().amount)
        assertTrue(removals1.isEmpty())

        // Step 2: Second bet 5,000 Ks with 6,000 Ks already in DB
        // Total would be 11,000 Ks (> 10,000 Ks limit)
        // 4,000 Ks accepted, 1,000 Ks auto-removed
        val step2Bets = listOf(Bet(voucherId = 0, number = "789", amount = 5000))
        val dbTotals = mapOf("789" to 6000)
        val (valid2, removals2) = validateAndFilterBets(step2Bets, banned, dbTotals = dbTotals)

        assertEquals(1, valid2.size)
        assertEquals(4000, valid2.first().amount)
        assertEquals(1, removals2.size)
        val r2 = removals2.first()
        assertEquals("789", r2.number)
        assertEquals(5000, r2.attemptedAmount)
        assertEquals(4000, r2.acceptedAmount)
        assertEquals(1000, r2.removedAmount)

        // Step 3: Third bet when number has already reached 10,000 Ks
        // Entire bet should be auto-removed
        val dbTotalsFull = mapOf("789" to 10000)
        val step3Bets = listOf(Bet(voucherId = 0, number = "789", amount = 2000))
        val (valid3, removals3) = validateAndFilterBets(step3Bets, banned, dbTotals = dbTotalsFull)

        assertTrue("Bet should be rejected when limit is fully reached", valid3.isEmpty())
        assertEquals(1, removals3.size)
        val r3 = removals3.first()
        assertEquals(2000, r3.attemptedAmount)
        assertEquals(0, r3.acceptedAmount)
        assertEquals(2000, r3.removedAmount)
    }

    @Test
    fun testMixedBets_unbanned_limited_and_banned() {
        val banned = listOf(
            BannedNumber(id = 1, number = "111", amountLimit = 0),     // completely blocked
            BannedNumber(id = 2, number = "222", amountLimit = 5000)   // limited to 5,000
        )
        val incoming = listOf(
            Bet(voucherId = 0, number = "333", amount = 10000), // unbanned
            Bet(voucherId = 0, number = "111", amount = 2000),  // blocked
            Bet(voucherId = 0, number = "222", amount = 7000)   // limited: accept 5000, remove 2000
        )

        val (valid, removals) = validateAndFilterBets(incoming, banned)

        assertEquals(2, valid.size)
        assertEquals("333", valid[0].number)
        assertEquals(10000, valid[0].amount)

        assertEquals("222", valid[1].number)
        assertEquals(5000, valid[1].amount)

        assertEquals(2, removals.size)
        val r111 = removals.find { it.number == "111" }!!
        assertEquals(2000, r111.removedAmount)

        val r222 = removals.find { it.number == "222" }!!
        assertEquals(2000, r222.removedAmount)
        assertEquals(5000, r222.acceptedAmount)
    }
}
