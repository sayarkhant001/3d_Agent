package com.threeDLedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.ui.theme.*

// ── Number category helper ─────────────────────────────────────────────────────
private enum class NumCat(val label: String, val color: Color, val bgColor: Color) {
    EXACT("ဒဲ့ (ပေါက်)", WinExactRed, WinExactBg),
    TUWT("တွတ်", WinPermGold, WinPermBg),
    NONE("", Color.Transparent, Color.Transparent)
}

private fun categorize(number: String, winning: String): NumCat {
    if (winning.length != 3) return NumCat.NONE
    if (number == winning) return NumCat.EXACT
    val allPerms = com.threeDLedger.logic.NumberGenerator.permutations(winning).toSet() - setOf(winning)
    val winInt = winning.toIntOrNull() ?: return NumCat.NONE
    val minus1 = String.format("%03d", if (winInt == 0) 999 else winInt - 1)
    val plus1  = String.format("%03d", if (winInt == 999) 0 else winInt + 1)
    val near   = setOf(minus1, plus1) - setOf(winning)
    val tuwtSet = allPerms + near
    if (number in tuwtSet) return NumCat.TUWT
    return NumCat.NONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToResult: () -> Unit = {}
) {
    val ledgerExposures by viewModel.ledgerExposures.collectAsStateWithLifecycle()
    val currentBatch    by viewModel.currentBatch.collectAsStateWithLifecycle()
    val savedWinner     by viewModel.winningNumber.collectAsStateWithLifecycle()

    // All bet numbers sorted ascending
    val allExposures = ledgerExposures
        .filter { it.totalBetAmount > 0 }
        .sortedBy { it.number.toIntOrNull() ?: 0 }
    val totalAll = allExposures.sumOf { it.totalBetAmount }

    // Multipliers for payout calculation
    val exactMult by viewModel.savedExactMult.collectAsStateWithLifecycle()
    val tuwtMult  by viewModel.savedPermMult.collectAsStateWithLifecycle()

    // After mode — only active when WinnerScreen has declared a winning number
    val isAfterMode = savedWinner.length == 3

    // After-mode rows: ONLY winning number and TUT numbers! Other numbers disappear!
    val relevantRows: List<Pair<LedgerExposure, NumCat>> =
        if (isAfterMode) {
            val exposureMap = allExposures.associateBy { it.number }
            val allPerms = com.threeDLedger.logic.NumberGenerator.permutations(savedWinner).toSet() - setOf(savedWinner)
            val winInt = savedWinner.toIntOrNull() ?: 0
            val minus1 = String.format("%03d", if (winInt == 0) 999 else winInt - 1)
            val plus1  = String.format("%03d", if (winInt == 999) 0 else winInt + 1)
            val near   = setOf(minus1, plus1) - setOf(savedWinner)
            val tuwtSet = allPerms + near

            val exactExp = exposureMap[savedWinner] ?: LedgerExposure(savedWinner, 0, 0, 0, 0)
            val exactRow = exactExp to NumCat.EXACT

            val tuwtRows = tuwtSet.sorted().map { num ->
                (exposureMap[num] ?: LedgerExposure(num, 0, 0, 0, 0)) to NumCat.TUWT
            }

            listOf(exactRow) + tuwtRows
        } else emptyList()

    val exactWonBets = relevantRows.filter { it.second == NumCat.EXACT }.sumOf { it.first.totalBetAmount }
    val tuwtWonBets  = relevantRows.filter { it.second == NumCat.TUWT }.sumOf { it.first.totalBetAmount }
    val totalWonBets = exactWonBets + tuwtWonBets

    val exactPayout = exactWonBets * exactMult
    val tuwtPayout  = tuwtWonBets * tuwtMult
    val totalPayout = exactPayout + tuwtPayout
    val netBalance  = totalAll - totalPayout

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ဂဏန်းများ စာရင်း", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(if (isAfterMode) "ပေါက်သီး / တွတ် တိုက်စစ်ချက်" else "ထိုးထားသော ဂဏန်းများ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                actions = {
                    if (isAfterMode) {
                        TextButton(
                            onClick = onNavigateToResult,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ရှင်းတမ်း", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EmeraldPrimary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(EmeraldSoftBg)
        ) {
            // ── Sub-header: batch number + winning number status ───────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "အကြိမ် :",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "$currentBatch",
                            color = EmeraldPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    if (isAfterMode) {
                        val exactWonCount = relevantRows.count { it.second == NumCat.EXACT }
                        val tuwtWonCount  = relevantRows.count { it.second == NumCat.TUWT }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = WinExactBg,
                                border = androidx.compose.foundation.BorderStroke(1.dp, WinExactRed.copy(alpha = 0.3f)),
                                modifier = Modifier.clickable(onClick = onNavigateToResult)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("ပေါက်: ", color = WinExactRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        savedWinner,
                                        color = WinExactRed,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (exactWonCount > 0 || tuwtWonCount > 0) EmeraldLight else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    if (exactWonCount > 0 || tuwtWonCount > 0) "ဒဲ့ $exactWonCount | တွတ် $tuwtWonCount"
                                    else "ပေါက်သီး မရှိပါ",
                                    color = if (exactWonCount > 0 || tuwtWonCount > 0) EmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "ပေါက်ဂဏန်း မသတ်မှတ်ရသေးပါ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // ── Table header ──────────────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = EmeraldLight
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isAfterMode) "ဂဏန်း နှင့် အမျိုးအစား" else "စဉ်   ဂဏန်း",
                        color = EmeraldDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        "ထိုးငွေ ပမာဏ",
                        color = EmeraldDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }

            // ── Table Rows ──────────────────────────────────────────────────
            if (!isAfterMode) {
                // BEFORE MODE — show all bet numbers
                if (allExposures.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ListAlt, null, tint = EmeraldPrimary.copy(alpha = 0.35f), modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "ထိုးမှု မရှိသေးပါ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "ဘောင်ချာထည့်သွင်းပါက ဤနေရာတွင် ပေါ်လာပါမည်",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(allExposures) { idx, exposure ->
                            val isEven = idx % 2 == 0
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isEven) Color.White else EmeraldSoftBg)
                                    .clickable(onClick = onNavigateToResult)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Index
                                Text(
                                    "${idx + 1}.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.width(36.dp)
                                )
                                // Number
                                Text(
                                    exposure.number,
                                    color = EmeraldPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp,
                                    modifier = Modifier.weight(1.2f)
                                )
                                // Amount
                                Text(
                                    "%,d".format(exposure.totalBetAmount),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Ks",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            HorizontalDivider(color = CardBorderSubtle, thickness = 0.5.dp)
                        }
                    }
                }
            } else {
                // AFTER MODE — all bet numbers with winning numbers (ဒဲ့, တွတ်) highlighted at top
                if (relevantRows.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ListAlt, null, tint = EmeraldPrimary.copy(alpha = 0.35f), modifier = Modifier.size(54.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "ထိုးမှု မရှိသေးပါ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(relevantRows) { idx, (exposure, cat) ->
                            val isEven = idx % 2 == 0
                            val rowBg = when (cat) {
                                NumCat.EXACT -> WinExactBg.copy(alpha = 0.45f)
                                NumCat.TUWT  -> Color(0xFFFFF8E1)
                                else         -> if (isEven) Color.White else EmeraldSoftBg
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .clickable(onClick = onNavigateToResult)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Index & Number & Category badge
                                Row(
                                    modifier = Modifier.weight(1.2f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${idx + 1}.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(36.dp)
                                    )
                                    Text(
                                        exposure.number,
                                        color = when (cat) {
                                            NumCat.EXACT -> WinExactRed
                                            NumCat.TUWT  -> Color(0xFFB45309)
                                            else         -> EmeraldPrimary
                                        },
                                        fontWeight = if (cat != NumCat.NONE) FontWeight.Black else FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 2.sp
                                    )
                                    if (cat != NumCat.NONE) {
                                        Spacer(Modifier.width(10.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = cat.bgColor
                                        ) {
                                            Text(
                                                cat.label,
                                                color = cat.color,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                // Amount
                                Text(
                                    "%,d".format(exposure.totalBetAmount),
                                    color = if (cat == NumCat.EXACT) WinExactRed else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Ks",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            HorizontalDivider(color = CardBorderSubtle, thickness = 0.5.dp)
                        }
                    }
                }
            }

            // ── Footer — Under bar with totals, winning payouts & net balance ─
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = EmeraldPrimary,
                shadowElevation = 8.dp
            ) {
                if (isAfterMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Row 1: Total Bets & Won Bets
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("စုစုပေါင်း ထိုးကြေး : ", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                Text("%,d Ks".format(totalAll), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("ပေါက်ထိုးကြေး : ", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                Text("%,d Ks".format(totalWonBets), color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f), thickness = 0.5.dp)

                        // Row 2: Total Payout & Net Profit/Loss
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("စုစုပေါင်း လျော်ကြေး : ", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                Text("%,.0f Ks".format(totalPayout), color = if (totalPayout > 0) Color(0xFFFF8A80) else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (netBalance >= 0) "အသားတင် အမြတ် : " else "အသားတင် အရှုံး : ", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                                Text(
                                    "${if (netBalance >= 0) "+" else ""}${"%,.0f Ks".format(netBalance)}",
                                    color = if (netBalance >= 0) Color(0xFF69F0AE) else Color(0xFFFF5252),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "စုစုပေါင်း ထိုးကြေး",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "%,d".format(totalAll),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "Ks",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
