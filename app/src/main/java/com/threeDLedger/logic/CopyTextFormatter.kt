package com.threeDLedger.logic

/**
 * Unified plain-text slip formatter for all Copy and Share buttons.
 *
 * Rules enforced consistently across the app:
 * - Never includes currency words/suffixes ("Ks", "ကျပ်", "MMK")
 * - Formats all bet/number rows strictly as `number = amount` (no index prefix, no commas in row amounts)
 * - Always wraps rows with clear Header and Footer sections
 */
object CopyTextFormatter {

    data class SettlementCopyData(
        val title: String,
        val subtitleLines: List<String> = emptyList(),
        val summaryLines: List<Pair<String, String>> = emptyList(),
        val detailRows: List<Pair<String, Int>> = emptyList(),
        val resultLabel: String? = null,
        val resultAmount: String? = null,
        val footerText: String = ""
    )

    private fun stripCurrencyTokens(raw: String): String =
        raw.replace(Regex("""\s*(?:Ks|KS|ks|MMK|mmk|ကျပ်)"""), "").trim()

    fun formatVoucherCopy(
        voucherId: Int,
        batchNumber: Int,
        dateTime: String,
        customerName: String,
        remark: String = "",
        bets: List<Pair<String, Int>>,
        totalAmount: Int,
        footerText: String = ""
    ): String = buildString {
        val cleanRemark = if (remark.isNotBlank()) " ($remark)" else ""
        appendLine("========================")
        appendLine("      3D ဘောင်ချာ      ")
        appendLine("========================")
        appendLine(" ဘောင်ချာအမှတ်-$voucherId  အကြိမ် : $batchNumber")
        appendLine(" ရက်စွဲ : $dateTime")
        appendLine(" ထိုးသူ : $customerName$cleanRemark")
        appendLine("------------------------")
        bets.forEach { (num, amt) ->
            appendLine("${num.trim().padStart(3)} = $amt")
        }
        appendLine("------------------------")
        appendLine(" ထိုးကြေးငွေ = $totalAmount")
        appendFooterSection(this, footerText, includeThankYou = true)
    }

    fun formatNumberListCopy(
        title: String,
        batchNumber: Int,
        items: List<Pair<String, Int>>,
        totalAmount: Int,
        footerText: String = "",
        serial: Int? = null,
        dineName: String = "",
        dateTime: String = "",
        winningNumber: String = "",
        agentName: String = "",
        totalLabel: String = "စုစုပေါင်း"
    ): String = buildString {
        appendLine("========================")
        appendLine("   $title")
        appendLine("========================")
        if (agentName.isNotBlank()) appendLine(" ထိုးသူ : $agentName")
        if (dineName.isNotBlank()) appendLine(" ဒိုင် : $dineName")
        if (serial != null) appendLine(" ဘောင်ချာ : #$serial")
        appendLine(" အကြိမ် : $batchNumber${if (winningNumber.length == 3) " (ထွက်: $winningNumber)" else ""}")
        if (dateTime.isNotBlank()) appendLine(" အချိန် : $dateTime")
        appendLine("------------------------")
        items.forEach { (num, amt) ->
            appendLine("${num.trim().padStart(3)} = $amt")
        }
        appendLine("------------------------")
        appendLine(" $totalLabel = $totalAmount")
        appendFooterSection(this, footerText, includeThankYou = false)
    }

    fun formatSettlementCopy(data: SettlementCopyData): String = buildString {
        appendLine("========================")
        appendLine("   ${data.title}")
        appendLine("========================")
        data.subtitleLines.filter { it.isNotBlank() }.forEach { appendLine(stripCurrencyTokens(it)) }
        if (data.summaryLines.isNotEmpty()) {
            appendLine("------------------------")
            data.summaryLines.forEach { (label, value) ->
                appendLine("${stripCurrencyTokens(label)} = ${stripCurrencyTokens(value)}")
            }
        }
        if (data.detailRows.isNotEmpty()) {
            appendLine("------------------------")
            data.detailRows.forEach { (num, amt) ->
                appendLine("${num.trim().padStart(3)} = $amt")
            }
        }
        if (!data.resultLabel.isNullOrBlank() && !data.resultAmount.isNullOrBlank()) {
            appendLine("------------------------")
            appendLine("${stripCurrencyTokens(data.resultLabel)} = ${stripCurrencyTokens(data.resultAmount)}")
        }
        appendFooterSection(this, data.footerText, includeThankYou = false)
    }

    fun formatWinningHistoryCopy(
        drawDateFormatted: String,
        drawDate: String,
        winningNumber: String,
        firstPrize6D: String,
        tutNumbers: List<String>
    ): String = buildString {
        appendLine("========================")
        appendLine("   3D ပေါက်ဂဏန်း မှတ်တမ်း")
        appendLine("========================")
        appendLine(" ရက်စွဲ : $drawDateFormatted ($drawDate)")
        appendLine(" ပထမဆု : $firstPrize6D")
        appendLine("------------------------")
        appendLine("$winningNumber = 1")
        if (tutNumbers.isNotEmpty()) {
            appendLine(" တွတ် : ${tutNumbers.joinToString(", ")}")
        }
        appendLine("========================")
    }

    private fun appendFooterSection(sb: StringBuilder, footerText: String, includeThankYou: Boolean) {
        val cleanFooter = stripCurrencyTokens(footerText)
        if (cleanFooter.isNotBlank()) {
            sb.appendLine(" $cleanFooter")
        }
        sb.appendLine("========================")
        if (includeThankYou) {
            sb.appendLine("      ကျေးဇူးတင်ပါသည်      ")
            sb.appendLine("========================")
        }
    }
}
