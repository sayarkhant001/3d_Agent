package com.threeDLedger.logic

/**
 * Canonical Myanmar 3D lottery Tut (တွတ်) and settlement math helpers.
 * Single source of truth used across LedgerScreen, WinnerScreen, CommissionerResultScreen,
 * OverflowScreen, ExportHistoryScreen, WinningHistoryScreen, and MainViewModel.
 */
object TutCalculator {

    data class TutResult(
        val permutations: List<String>,
        val nearMisses: List<String>,
        val allTut: Set<String>
    )

    /**
     * Computes all Tut numbers for a 3-digit [winningNumber]:
     * - Permutations (အပြန်): unique digit rearrangements excluding the exact winning number
     * - Near misses (ကပ်သီး): cyclic -1 and +1 (with 000 <-> 999 wrap-around), excluding exact & duplicates
     */
    fun calculate(winningNumber: String): TutResult {
        if (winningNumber.length != 3 || !winningNumber.all { it.isDigit() }) {
            return TutResult(emptyList(), emptyList(), emptySet())
        }
        val perms = (NumberGenerator.permutations(winningNumber).toSet() - setOf(winningNumber)).sorted()
        val numInt = winningNumber.toIntOrNull() ?: return TutResult(perms, emptyList(), perms.toSet())
        val minus1 = String.format("%03d", if (numInt == 0) 999 else numInt - 1)
        val plus1 = String.format("%03d", if (numInt == 999) 0 else numInt + 1)
        val near = (setOf(minus1, plus1) - setOf(winningNumber) - perms.toSet()).sorted()
        val allTut = (perms + near).toSet()
        return TutResult(permutations = perms, nearMisses = near, allTut = allTut)
    }

    /** Returns true if a customer name or voucher remark designates overflow/upper-agent artifacts. */
    fun isOverflowMarker(text: String): Boolean =
        text.contains("တင်ကွက်") ||
            text.contains("overflow", ignoreCase = true) ||
            text.contains("upper", ignoreCase = true) ||
            text.contains("အထက်ဒိုင်")

    /** Normalizes commission rate stored either as percentage (e.g. 15.0) or fraction (0.15). */
    fun normalizeCommissionFraction(rawRate: Double): Double =
        if (rawRate > 1.0) rawRate / 100.0 else rawRate.coerceAtLeast(0.0)
}
