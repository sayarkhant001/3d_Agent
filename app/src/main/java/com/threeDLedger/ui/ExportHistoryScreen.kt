package com.threeDLedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.data.ExportRecordWithNumbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportHistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val exportRecords by viewModel.allExportRecords.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val footerText by viewModel.voucherFooterText.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("တင်ကွက် မှတ်တမ်း", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        if (exportRecords.isEmpty()) {
            Box(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("မှတ်တမ်း မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(12.dp)) {
                items(exportRecords) { export ->
                    ExportRecordCard(
                        export = export,
                        footerText = footerText,
                        onCopy = { text ->
                            val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("Export Record", text))
                            android.widget.Toast.makeText(context, "ကူးယူပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        onPrint = { export ->
                            coroutineScope.launch {
                                try {
                                    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                    val paperSize = prefs.getString("paperSize", "58mm") ?: "58mm"
                                    val voucherData = com.threeDLedger.logic.BluetoothPrinter.VoucherData(
                                        batchNumber = export.record.batchNumber,
                                        voucherId = export.record.id,
                                        date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(export.record.timestamp)),
                                        customerName = export.record.type,
                                        bets = export.numbers.map { it.number to it.amount },
                                        totalAmount = export.record.totalAmount,
                                        footerText = footerText
                                    )
                                    val bitmap = com.threeDLedger.logic.BluetoothPrinter.createVoucherBitmap(voucherData, paperSize)
                                    com.threeDLedger.logic.BluetoothPrinter.printBitmap(bitmap, paperSize)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "ပရင်တာ error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportRecordCard(
    export: ExportRecordWithNumbers,
    footerText: String,
    onCopy: (String) -> Unit,
    onPrint: (ExportRecordWithNumbers) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateString = SimpleDateFormat("HH:mm:ss  dd/MM/yyyy", Locale.getDefault()).format(Date(export.record.timestamp))
    val isOverflow = export.record.type.contains("Overflow") || export.record.type.contains("တင်ကွက်")
    val headerColor = if (isOverflow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val sortedNumbers = export.numbers.sortedByDescending { it.amount }

    val voucherText = buildString {
        appendLine("========================")
        appendLine("   တင်ကွက် မှတ်တမ်း     ")
        appendLine("========================")
        appendLine(" ဘောင်ချာ  : #${export.record.id}")
        appendLine(" အကြိမ်    : ${export.record.batchNumber}")
        appendLine(" အချိန်    : $dateString")
        appendLine(" အမျိုးအစား: ${export.record.type}")
        appendLine("------------------------")
        sortedNumbers.forEach { num ->
            appendLine(" ${num.number.padEnd(5)} = ${num.amount}")
        }
        appendLine("------------------------")
        appendLine(" စုစုပေါင်း : %,d Ks".format(export.record.totalAmount))
        appendLine("========================")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // ── Card header bar (Always visible, tap to expand/collapse) ────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "ဘောင်ချာ #${export.record.id}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "အကြိမ်: ${export.record.batchNumber}",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            dateString,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%,d Ks".format(export.record.totalAmount),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "${sortedNumbers.size} ဂဏန်း",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            // ── Expanded Body ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Column headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "ဂဏန်း",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                        Text(
                            "ပမာဏ",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    }

                    // Per-number rows
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, headerColor.copy(alpha = 0.25f))
                    ) {
                        sortedNumbers.forEachIndexed { index, num ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (index % 2 == 0) Color.Transparent
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    num.number,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "%,d Ks".format(num.amount),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOverflow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (index < sortedNumbers.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                        }
                    }

                    // Footer total
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerColor)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "စုစုပေါင်း (${sortedNumbers.size} ဂဏန်း)",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "%,d Ks".format(export.record.totalAmount),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPrint(export) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = headerColor)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ပရင့်ထုတ်မည်", fontSize = 13.sp)
                        }
                        Button(
                            onClick = { onCopy(voucherText) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = headerColor)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ကော်ပီကူးမည်", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
