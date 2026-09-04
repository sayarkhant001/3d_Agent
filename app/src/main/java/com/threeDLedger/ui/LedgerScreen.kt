package com.threeDLedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Number category helper ────────────────────────────────────────────────────
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

// ── Colours ───────────────────────────────────────────────────────────────────
private val Orange      = Color(0xFFF57C00)
private val OrangeLight = Color(0xFFFFA726)
private val OrangeRow   = Color(0xFFFFE0B2)
private val RedExact    = Color(0xFFD32F2F)
private val RedExactBg  = Color(0xFFFFCDD2)
private val YellowPerm  = Color(0xFFF9A825)
private val YellowPermBg= Color(0xFFFFF9C4)
private val TealNear    = Color(0xFF00695C)
private val TealNearBg  = Color(0xFFB2DFDB)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val ledgerExposures by viewModel.ledgerExposures.collectAsStateWithLifecycle()
    val currentBatch    by viewModel.currentBatch.collectAsStateWithLifecycle()
    val savedWinner     by viewModel.winningNumber.collectAsStateWithLifecycle()

    // 0 = Before, 1 = After
    var selectedTab by remember { mutableIntStateOf(if (savedWinner.length == 3) 1 else 0) }

    // Sort ascending (least → highest)
    val allExposures = ledgerExposures
        .filter { it.totalBetAmount > 0 }
        .sortedBy { it.number.toIntOrNull() ?: 0 }
    val totalAll = allExposures.sumOf { it.totalBetAmount }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("ဂဏန်းများ", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Orange)
                )
                // Tab row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = OrangeLight,
                    contentColor = Color.White,
                    indicator = { tabs ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabs[selectedTab]),
                            color = Color.White
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "မပြည့်မီ  (Before)",
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "ပြည့်ပြီ  (After)",
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White
                            )
                        }
                    )
                }
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
            BeforeView(
                modifier = Modifier.padding(padding),
                exposures = allExposures,
                currentBatch = currentBatch,
                totalAll = totalAll
            )
        } else {
            AfterView(
                modifier = Modifier.padding(padding),
                exposures = allExposures,
                currentBatch = currentBatch,
                totalAll = totalAll,
                savedWinner = savedWinner,
                onSaveWinner = { viewModel.saveWinningNumber(it) }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODE 1 — Before (clean ascending list)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun BeforeView(
    modifier: Modifier,
    exposures: List<LedgerExposure>,
    currentBatch: Int,
    totalAll: Int
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Batch sub-header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Orange)
                .padding(bottom = 10.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "အကြိမ် : $currentBatch",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Table header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Orange)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                "ဂဏန်းများ",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                "ပမာဏ",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }

        // Rows
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, Orange)
        ) {
            items(exposures) { exposure ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        exposure.number,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Orange,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "%,d".format(exposure.totalBetAmount),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
                Divider(color = Orange.copy(alpha = 0.25f), thickness = 0.5.dp)
            }
        }

        // Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Orange)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "စုစုပေါင်း",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "%,d".format(totalAll),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MODE 2 — After (orange table + winner highlights)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AfterView(
    modifier: Modifier,
    exposures: List<LedgerExposure>,
    currentBatch: Int,
    totalAll: Int,
    savedWinner: String,
    onSaveWinner: (String) -> Unit
) {
    var winInput by remember(savedWinner) { mutableStateOf(savedWinner) }
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier = modifier.fillMaxSize()) {

        // ── Winner input sub-header ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Orange)
                .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Batch + declared winner chip
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    "အကြိမ် :  $currentBatch",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (savedWinner.length == 3) {
                    Spacer(Modifier.width(16.dp))
                    Box(
                        modifier = Modifier
                            .background(RedExact, RoundedCornerShape(6.dp))
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                    ) {
                        Text(
                            savedWinner,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Input row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = winInput,
                    onValueChange = { v ->
                        winInput = v.filter { it.isDigit() }.take(3)
                    },
                    placeholder = {
                        Text(
                            "ပေါက်ဂဏန်း ထည့်ပါ…",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        if (winInput.length == 3) { onSaveWinner(winInput); keyboard?.hide() }
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                        cursorColor = Color.White
                    ),
                    modifier = Modifier.weight(1f).height(52.dp),
                    textStyle = LocalTextStyle.current.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = Color.White
                    )
                )
                // Confirm
                Button(
                    onClick = {
                        if (winInput.length == 3) { onSaveWinner(winInput); keyboard?.hide() }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (winInput.length == 3) Color.White else Color.White.copy(alpha = 0.3f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Default.Check, "OK", tint = Orange)
                    Spacer(Modifier.width(4.dp))
                    Text("OK", color = Orange, fontWeight = FontWeight.Bold)
                }
                // Clear
                if (savedWinner.isNotEmpty()) {
                    IconButton(
                        onClick = { winInput = ""; onSaveWinner("") },
                        modifier = Modifier
                            .size(52.dp)
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Close, "Clear", tint = Color.White)
                    }
                }
            }
        }

        // ── Legend ────────────────────────────────────────────────────────────
        if (savedWinner.length == 3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(RedExact,    "တိုက်ရိုက် (Exact)")
                LegendDot(YellowPerm,  "လှည့်ပတ် (Perm)")
                LegendDot(TealNear,    "တွတ် ±1 (Near)")
            }
        }

        // ── Table header ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Orange)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                "ဂဏန်းများ",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Text(
                "ပမာဏ",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
        }

        // ── Rows ──────────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, Orange)
        ) {
            items(exposures) { exposure ->
                val cat = if (savedWinner.length == 3) categorize(exposure.number, savedWinner)
                          else NumCat.NONE

                val (rowBg, numColor, divColor) = when (cat) {
                    NumCat.EXACT       -> Triple(RedExactBg,   RedExact,   RedExact.copy(alpha = 0.3f))
                    NumCat.PERMUTATION -> Triple(YellowPermBg, YellowPerm, YellowPerm.copy(alpha = 0.4f))
                    NumCat.NEAR        -> Triple(TealNearBg,   TealNear,   TealNear.copy(alpha = 0.3f))
                    NumCat.NONE        -> Triple(Color.Transparent, Orange, Orange.copy(alpha = 0.25f))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .padding(horizontal = 16.dp, vertical = if (cat == NumCat.EXACT) 14.dp else 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        exposure.number,
                        fontWeight = if (cat != NumCat.NONE) FontWeight.ExtraBold else FontWeight.Bold,
                        fontSize = if (cat == NumCat.EXACT) 20.sp else 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = numColor,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "%,d".format(exposure.totalBetAmount),
                        fontWeight = if (cat != NumCat.NONE) FontWeight.Bold else FontWeight.Normal,
                        fontSize = if (cat == NumCat.EXACT) 20.sp else 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = numColor,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                }
                Divider(color = divColor, thickness = if (cat != NumCat.NONE) 1.dp else 0.5.dp)
            }
        }

        // ── Footer ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Orange)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "စုစုပေါင်း",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                "%,d".format(totalAll),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.End
            )
        }
    }
}

// ── Small helper composable ───────────────────────────────────────────────────
@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(11.dp)
                .background(color, RoundedCornerShape(50))
        )
        Text(label, fontSize = 11.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}
