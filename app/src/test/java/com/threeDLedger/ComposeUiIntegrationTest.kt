package com.threeDLedger

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.threeDLedger.data.Customer
import com.threeDLedger.ui.AgentSettlement
import com.threeDLedger.ui.AgentSettlementCard
import com.threeDLedger.ui.KeypadButton
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ComposeUiIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testKeypadButtonClicks() {
        var clicked = false
        composeTestRule.setContent {
            KeypadButton(
                text = "ထွိုင်",
                bgColor = Color(0xFF059669),
                onClick = { clicked = true }
            )
        }

        composeTestRule.onNodeWithText("ထွိုင်").assertIsDisplayed()
        composeTestRule.onNodeWithText("ထွိုင်").performClick()
        assertTrue("Keypad button click triggered", clicked)
    }

    @Test
    fun testAgentSettlementCardReceivable() {
        val customer = Customer(id = 1, name = "ဦးကျော်", commissionRate = 0.15)

        // Scenario 1: House receives from agent (balance > 0)
        val receivableSettlement = AgentSettlement(
            customer = customer,
            totalBet = 100_000L,
            commission = 15_000L,
            netAfterComm = 85_000L,
            exactBetAmt = 0L,
            exactPayout = 0L,
            tuwtBetAmt = 0L,
            tuwtPayout = 0L,
            totalPayout = 0L,
            balance = 85_000L,
            paidAmount = 0L,
            remaining = 85_000L
        )

        var tapped = false
        composeTestRule.setContent {
            AgentSettlementCard(
                settlement = receivableSettlement,
                onTapDetail = { tapped = true },
                onEditPaid = {}
            )
        }

        composeTestRule.onNodeWithText("ဦးကျော်").assertIsDisplayed()
        composeTestRule.onNodeWithText("ကျန်ငွေ (ရရန်)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("ကြွေးကျန် (ရရန်)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("ရောင်းကြေး").performClick()
        assertTrue("Card click triggered", tapped)
    }

    @Test
    fun testAgentSettlementCardPayable() {
        val customer = Customer(id = 2, name = "ဒေါ်လှ", commissionRate = 0.15)

        // Scenario 2: Agent won big, House owes agent (balance < 0)
        val payableSettlement = AgentSettlement(
            customer = customer,
            totalBet = 10_000L,
            commission = 1_500L,
            netAfterComm = 8_500L,
            exactBetAmt = 1_000L,
            exactPayout = 600_000L,
            tuwtBetAmt = 0L,
            tuwtPayout = 0L,
            totalPayout = 600_000L,
            balance = -591_500L,
            paidAmount = 0L,
            remaining = -591_500L
        )

        composeTestRule.setContent {
            AgentSettlementCard(
                settlement = payableSettlement,
                onTapDetail = {},
                onEditPaid = {}
            )
        }

        composeTestRule.onNodeWithText("ဒေါ်လှ").assertIsDisplayed()
        composeTestRule.onNodeWithText("ကျန်ငွေ (ပေးရန်)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("ကြွေးကျန် (ပေးရန်)", substring = true).assertIsDisplayed()
    }

    @Test
    fun testAgentSettlementCardTutAndExactClick() {
        val customer = Customer(id = 3, name = "KZT", commissionRate = 0.25)
        val tutSettlement = AgentSettlement(
            customer = customer,
            totalBet = 191_000L,
            commission = 47_750L,
            netAfterComm = 143_250L,
            exactBetAmt = 1_000L,
            exactPayout = 600_000L,
            tuwtBetAmt = 15_000L,
            tuwtPayout = 150_000L,
            totalPayout = 750_000L,
            balance = -606_750L,
            paidAmount = 0L,
            remaining = -606_750L,
            tuwtDetails = listOf(
                com.threeDLedger.ui.TutWinDetail("641", 3000L, 30_000L),
                com.threeDLedger.ui.TutWinDetail("604", 1000L, 10_000L)
            ),
            exactDetails = listOf(
                com.threeDLedger.ui.TutWinDetail("123", 1000L, 600_000L)
            )
        )

        var tutClicked = false
        var exactClicked = false

        composeTestRule.setContent {
            AgentSettlementCard(
                settlement = tutSettlement,
                onTapDetail = {},
                onEditPaid = {},
                onTapTut = { tutClicked = true },
                onTapExact = { exactClicked = true }
            )
        }

        composeTestRule.onNodeWithText("တွတ် (လျော်)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("တွတ် (လျော်)", substring = true).performClick()
        assertTrue("onTapTut should be triggered", tutClicked)

        composeTestRule.onNodeWithText("ဒဲ့ (ပေါက်)", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("ဒဲ့ (ပေါက်)", substring = true).performClick()
        assertTrue("onTapExact should be triggered", exactClicked)
    }

    @Test
    fun testWinBreakdownDialogDisplaysItems() {
        val details = listOf(
            com.threeDLedger.ui.TutWinDetail("641", 3000L, 30_000L),
            com.threeDLedger.ui.TutWinDetail("604", 1000L, 10_000L)
        )
        val dummyIcon = androidx.compose.ui.graphics.vector.ImageVector.Builder(
            name = "dummy",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).build()
        var dismissed = false
        composeTestRule.setContent {
            com.threeDLedger.ui.WinBreakdownDialog(
                title = "တွတ် (လျော်) အသေးစိတ် - KZT",
                icon = dummyIcon,
                iconTint = Color(0xFFD97706),
                agentName = "KZT",
                batchNumber = 17,
                winningNumber = "640",
                multiplier = 10.0,
                details = details,
                totalBet = 4000L,
                totalPayout = 40_000L,
                onDismiss = { dismissed = true }
            )
        }

        composeTestRule.onNodeWithText("တွတ် (လျော်) အသေးစိတ် - KZT").assertIsDisplayed()
        composeTestRule.onNodeWithText("641").assertIsDisplayed()
        composeTestRule.onNodeWithText("604").assertIsDisplayed()
        composeTestRule.onNodeWithText("ကောင်းပြီ").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun testExportHistoryBottomBarDisplaysTotalSent() {
        composeTestRule.setContent {
            com.threeDLedger.ui.ExportHistoryBottomBar(
                totalAmount = 16_000L,
                totalVouchers = 1,
                totalNumbers = 6,
                selectedBatch = 15
            )
        }

        composeTestRule.onNodeWithText("အကြိမ် (15) တင်ငွေ စုစုပေါင်း", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("ဘောင်ချာ (1) စောင် • (6) ဂဏန်း", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("16,000 Ks").assertIsDisplayed()
    }
}
