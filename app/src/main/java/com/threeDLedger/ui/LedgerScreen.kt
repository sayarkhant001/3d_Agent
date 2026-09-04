package com.threeDLedger.ui

import androidx.compose.foundation.background
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

// ── Colours — orange/amber matching screenshot ─────────────────────────────────
private val OrangeHeader = Color(0xFFF57C00)
private val OrangeRow    = Color(0xFFFFA000)
private val OrangeFaint  = Color(0xFFFFF3E0)
private val RedExact     = Color(0xFFD32F2F)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val ledgerExposures by viewModel.ledgerExposures.collectAsStateWithLifecycle()
    val currentBatch    by viewModel.currentBatch.collectAsStateWithLifecycle()
    val savedWinner     by viewModel.winningNumber.collectAsStateWithLifecycle()
    val keyboard        = LocalSoftwareKeyboardController.current

    // All bet numbers sorted ascending
    val allExposures = ledgerExposures
        .filter { it.totalBetAmount > 0 }
        .sortedBy { it.number.toIntOrNull() ?: 0 }
    val totalAll = allExposures.sumOf { it.totalBetAmount }

    // Winner input field state
    var winInput by remember(savedWinner) { mutableStateOf(savedWinner) }

    // After mode — only show when winner is declared (length == 3)
    val isAfterMode = savedWinner.length == 3

    // For after mode: filter + sort (EXACT → PERM → NEAR), only those actually bet
    val relevantRows: List<Pair<LedgerExposure, NumCat>> =
        if (isAfterMode) {
            allExposures
                .map { it to categorize(it.number, savedWinner) }
                .filter { (_, cat) -> cat != NumCat.NONE }
                .sortedWith(
                    compareBy(
                        { when (it.second) {
                            NumCat.EXACT        -> 0
                            NumCat.PERMUTATION  -> 1
                            NumCat.NEAR         -> 2
                            else                -> 3
                        }},
                        { it.first.number.toIntOrNull() ?: 0 }
                    )
                )
        } else emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဂဏန်းများ", color = Color.White, fontWeight = FontWeight.Bold) },
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

            // ── Sub-header: batch + winner display + input ─────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(OrangeHeader)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Batch number + declared winner chip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "အကြိမ် :",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$currentBatch",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    if (isAfterMode) {
                        Spacer(Modifier.width(16.dp))
                        Text(
                            savedWinner,
                            color = Color(0xFFFFE57F),   // amber-yellow — winner number
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 3.sp
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                // Winner input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = winInput,
                        onValueChange = { v ->
                            winInput = v.filter { it.isDigit() }.take(3)
                        },
                        placeholder = {
                            Text(
                                "ပေါက်ဂဏန်း ထည့်ပါ",
                                color = Color.White.copy(alpha = 0.55f),
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (winInput.length == 3) {
                                viewModel.saveWinningNumber(winInput)
                                keyboard?.hide()
                            }
                        }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor     = Color.White,
                            unfocusedTextColor   = Color.White,
                            focusedBorderColor   = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                            cursorColor          = Color.White
                        ),
                        textStyle = LocalTextStyle.current.copy(
                            fontSize    = 20.sp,
                            fontWeight  = FontWeight.Bold,
                            textAlign   = TextAlign.Center,
                            fontFamily  = FontFamily.Monospace,
                            color       = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    )

                    // Confirm button
                    Button(
                        onClick = {
                            if (winInput.length == 3) {
                                viewModel.saveWinningNumber(winInput)
                                keyboard?.hide()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (winInput.length == 3) Color.White
                                             else Color.White.copy(alpha = 0.3f)
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(50.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Check, "OK", tint = OrangeHeader, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("OK", color = OrangeHeader, fontWeight = FontWeight.Bold)
                    }

                    // Clear winner button (only visible in after mode)
                    if (isAfterMode) {
                        IconButton(
                            onClick = {
                                winInput = ""
                                viewModel.saveWinningNumber("")
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Close, "Clear", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
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
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
            }

            // ── Rows ──────────────────────────────────────────────────────────
            if (!isAfterMode) {
                // BEFORE MODE — all bet numbers ascending
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
                                number = exposure.number,
                                amount = exposure.totalBetAmount,
                                bgColor = OrangeRow,
                                textColor = Color.White
                            )
                            Divider(color = Color.White.copy(alpha = 0.18f), thickness = 0.5.dp)
                        }
                    }
                }
            } else {
                // AFTER MODE — only exact / perm / near that were actually bet
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
                                textColor = Color.White
                            )
                            Divider(color = Color.White.copy(alpha = 0.18f), thickness = 0.5.dp)
                        }
                    }
                }
            }

            // ── Footer — always shows total of ALL bets ───────────────────────
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
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
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
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
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
            modifier   = Modifier.weight(1f),
            textAlign  = TextAlign.Center
        )
    }
}
