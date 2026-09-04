package com.threeDLedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

// ── Number category helper ─────────────────────────────────────────────────────
private enum class NumCat { EXACT, PERMUTATION, NEAR, NONE }

private fun categorize(number: String, winning: String): NumCat {
    if (winning.length != 3) return NumCat.NONE
    if (number == winning) return NumCat.EXACT
    val perms = com.threeDLedger.logic.NumberGenerator.permutations(winning).toSet()
    if (number in perms) return NumCat.PERMUTATION
    val winInt = winning.toIntOrNull() ?: return NumCat.NONE
    val minus1 = String.format("%03d", if (winInt == 0) 999 else winInt - 1)
    val plus1  = String.format("%03d", if (winInt == 999) 0 else winInt + 1)
    if (number == minus1 || number == plus1) return NumCat.NEAR
    return NumCat.NONE
}

// ── Colours ────────────────────────────────────────────────────────────────────
private val OrangeHeader = Color(0xFFF57C00)
private val OrangeRow    = Color(0xFFFFA000)
private val OrangeFaint  = Color(0xFFFFF3E0)
private val RedExact     = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToResult: () -> Unit = {}
) {
    val ledgerExposures by viewModel.ledgerExposures.collectAsStateWithLifecycle()
    val currentBatch    by viewModel.currentBatch.collectAsStateWithLifecycle()
    // Winning number comes exclusively from WinnerScreen via viewModel
    val savedWinner     by viewModel.winningNumber.collectAsStateWithLifecycle()

    // All bet numbers sorted ascending
    val allExposures = ledgerExposures
        .filter { it.totalBetAmount > 0 }
        .sortedBy { it.number.toIntOrNull() ?: 0 }
    val totalAll = allExposures.sumOf { it.totalBetAmount }

    // After mode — only active when WinnerScreen has declared a winning number
    val isAfterMode = savedWinner.length == 3

    // After-mode rows: EXACT → PERMUTATION → NEAR, only those actually bet
    val relevantRows: List<Pair<LedgerExposure, NumCat>> =
        if (isAfterMode) {
            allExposures
                .map { it to categorize(it.number, savedWinner) }
                .filter { (_, cat) -> cat != NumCat.NONE }
                .sortedWith(
                    compareBy(
                        { when (it.second) {
                            NumCat.EXACT       -> 0
                            NumCat.PERMUTATION -> 1
                            NumCat.NEAR        -> 2
                            else               -> 3
                        }},
                        { it.first.number.toIntOrNull() ?: 0 }
                    )
                )
        } else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("ဂဏန်းများ", color = Color.White, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = OrangeHeader)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(OrangeFaint)
        ) {
            // ── Sub-header: batch number + winning number status ───────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrangeHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "အကြိမ် :",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "$currentBatch",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.width(20.dp))
                if (isAfterMode) {
                    Text(
                        "ပေါက်ဂဏန်း :",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        savedWinner,
                        color = Color(0xFFFFE57F),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    )
                } else {
                    Text(
                        "ပေါက်ဂဏန်း မသတ်မှတ်ရသေးပါ",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp
                    )
                }
            }

            // ── Table header ──────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrangeHeader)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "ဂဏန်းများ",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
                Text(
                    "ပမာဏ",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    textAlign = TextAlign.End
                )
            }

            // ── Rows ──────────────────────────────────────────────────────────
            if (!isAfterMode) {
                // BEFORE MODE — show all bet numbers
                if (allExposures.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "ထိုးမှု မရှိသေးပါ",
                            color = OrangeHeader.copy(alpha = 0.5f),
                            fontSize = 16.sp
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(allExposures) { exposure ->
                            NumberTableRow(
                                number    = exposure.number,
                                amount    = exposure.totalBetAmount,
                                bgColor   = OrangeRow,
                                textColor = Color.White,
                                onClick   = onNavigateToResult
                            )
                            Divider(color = Color.White.copy(alpha = 0.18f), thickness = 0.5.dp)
                        }
                    }
                }
            } else {
                // AFTER MODE — only ပေါက်သီး / တွတ် numbers that were actually bet
                if (relevantRows.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "ပေါက်ဂဏန်း $savedWinner ကို မထိုးသူ မရှိပါ",
                            color = OrangeHeader.copy(alpha = 0.6f),
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(relevantRows) { (exposure, cat) ->
                            NumberTableRow(
                                number    = exposure.number,
                                amount    = exposure.totalBetAmount,
                                bgColor   = if (cat == NumCat.EXACT) RedExact else OrangeRow,
                                textColor = Color.White,
                                onClick   = onNavigateToResult
                            )
                            Divider(color = Color.White.copy(alpha = 0.18f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            // ── Footer — total of ALL bets ────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrangeHeader)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "စုစုပေါင်း",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
                Text(
                    "%,d".format(totalAll),
                    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

// ── Reusable row ──────────────────────────────────────────────────────────────
@Composable
private fun NumberTableRow(
    number   : String,
    amount   : Int,
    bgColor  : Color,
    textColor: Color,
    onClick  : () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            number,
            color      = textColor,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.weight(1f),
            textAlign  = TextAlign.Center
        )
        Text(
            "%,d".format(amount),
            color      = textColor,
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            fontFamily = FontFamily.Monospace,
            modifier   = Modifier.weight(1f).padding(end = 12.dp),
            textAlign  = TextAlign.End
        )
    }
}
