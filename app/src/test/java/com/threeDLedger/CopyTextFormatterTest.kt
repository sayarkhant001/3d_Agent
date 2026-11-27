package com.threeDLedger

import com.threeDLedger.logic.CopyTextFormatter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyTextFormatterTest {

    private fun assertNoCurrencyUnits(text: String) {
        val forbidden = listOf("Ks", "KS", "ks", "MMK", "mmk", "ကျပ်")
        forbidden.forEach { token ->
            assertFalse("Unexpected currency token '$token' in:\n$text", text.contains(token))
        }
    }

    @Test
    fun voucherCopyStripsCurrencyAndFormatsRowsCleanly() {
        val text = CopyTextFormatter.formatVoucherCopy(
            voucherId = 12,
            batchNumber = 7,
            dateTime = "2026-11-27 14:30",
            customerName = "ဦးမောင်",
            remark = "",
            bets = listOf("123" to 5000, "007" to 1500),
            totalAmount = 6500,
            footerText = "09-123456 Ks MMK ကျပ်"
        )

        assertNoCurrencyUnits(text)
        assertTrue(text.contains("3D ဘောင်ချာ"))
        assertTrue(text.contains("ဘောင်ချာအမှတ်-12  အကြိမ် : 7"))
        assertTrue(text.contains("123 = 5000"))
        assertTrue(text.contains("007 = 1500"))
        assertTrue(text.contains("ထိုးကြေးငွေ = 6500"))
        assertTrue(text.contains("09-123456"))
        assertTrue(text.contains("ကျေးဇူးတင်ပါသည်"))
        assertFalse(text.contains("=>>"))
    }

    @Test
    fun numberListCopyUsesNumberEqualsAmountAndFooter() {
        val text = CopyTextFormatter.formatNumberListCopy(
            title = "လျှံကွက် (တင်ရန်)",
            batchNumber = 5,
            items = listOf("456" to 10000, "999" to 2500),
            totalAmount = 12500,
            footerText = "ဆက်သွယ်ရန် 09-777888 MMK",
            serial = 3,
            dineName = "ဒိုင်ကြီး",
            dateTime = "2026-11-27 15:00"
        )

        assertNoCurrencyUnits(text)
        assertTrue(text.contains("လျှံကွက် (တင်ရန်)"))
        assertTrue(text.contains("ဒိုင် : ဒိုင်ကြီး"))
        assertTrue(text.contains("ဘောင်ချာ : #3"))
        assertTrue(text.contains("456 = 10000"))
        assertTrue(text.contains("999 = 2500"))
        assertTrue(text.contains("စုစုပေါင်း = 12500"))
        assertTrue(text.contains("ဆက်သွယ်ရန် 09-777888"))
    }

    @Test
    fun settlementCopyStripsCurrencyAndPreservesHeaderFooter() {
        val text = CopyTextFormatter.formatSettlementCopy(
            CopyTextFormatter.SettlementCopyData(
                title = "ကော်မရှင်စား ရှင်းတမ်း",
                subtitleLines = listOf("အမည် : ဒေါ်လှ", "အကြိမ် : 9", "ပေါက်ဂဏန်း : 321"),
                summaryLines = listOf(
                    "စုစုပေါင်း" to "50,000 Ks",
                    "ကော်မရှင်" to "7,500 ကျပ်"
                ),
                detailRows = listOf("321" to 2000, "123" to 500),
                resultLabel = "ပေးရန်ကျန်",
                resultAmount = "42,500 MMK",
                footerText = "3D စာရင်း"
            )
        )

        assertNoCurrencyUnits(text)
        assertTrue(text.contains("ကော်မရှင်စား ရှင်းတမ်း"))
        assertTrue(text.contains("စုစုပေါင်း = 50,000"))
        assertTrue(text.contains("ကော်မရှင် = 7,500"))
        assertTrue(text.contains("321 = 2000"))
        assertTrue(text.contains("123 = 500"))
        assertTrue(text.contains("ပေးရန်ကျန် = 42,500"))
        assertTrue(text.contains("3D စာရင်း"))
    }

    @Test
    fun winningHistoryCopyFormatsWithoutCurrency() {
        val text = CopyTextFormatter.formatWinningHistoryCopy(
            drawDateFormatted = "16 Nov 2026",
            drawDate = "2026-11-16",
            winningNumber = "000",
            firstPrize6D = "123000",
            tutNumbers = listOf("001", "999")
        )

        assertNoCurrencyUnits(text)
        assertTrue(text.contains("3D ပေါက်ဂဏန်း မှတ်တမ်း"))
        assertTrue(text.contains("000 = 1"))
        assertTrue(text.contains("တွတ် : 001, 999"))
    }
}
