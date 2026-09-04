package com.threeDLedger.ui
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import android.content.Intent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(
    viewModel: MainViewModel,
    initialCustomerId: Int? = null,
    onNavigateBack: () -> Unit
) {
    val vouchers by viewModel.vouchersWithCustomer.collectAsStateWithLifecycle()
    val allVouchersWithBets by viewModel.vouchersWithBets.collectAsStateWithLifecycle()
    val footerText by viewModel.voucherFooterText.collectAsStateWithLifecycle()
    
    val filteredVouchers = if (initialCustomerId != null) {
        allVouchersWithBets.filter { it.voucher.customerId == initialCustomerId }
    } else {
        allVouchersWithBets
    }

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဘောင်ချာများ", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            items(filteredVouchers) { voucherWithBets ->
                val customerName = vouchers.find { it.voucher.id == voucherWithBets.voucher.id }?.customer?.name ?: "Unknown"
                
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("No.${voucherWithBets.voucher.id} (အကြိမ်: ${voucherWithBets.voucher.batchNumber})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("အမည်: $customerName")
                        
                        val dateString = SimpleDateFormat("yyyy.MM.dd/HH:mm:ss").format(Date(voucherWithBets.voucher.timestamp))
                        Text("အချိန်: $dateString", style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            voucherWithBets.bets.forEach { bet ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        bet.number,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        "%,d".format(bet.amount),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        " Ks",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "စုစုပေါင်း: %,d Ks".format(voucherWithBets.voucher.totalAmount),
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            
                            val remarkStr = if (voucherWithBets.voucher.remark.isNotEmpty()) " (${voucherWithBets.voucher.remark})" else ""
                            val textToCopy = buildString {
                                appendLine("========================")
                                appendLine("      3D VOUCHER")
                                appendLine("========================")
                                appendLine(" အကြိမ် : ${voucherWithBets.voucher.batchNumber}")
                                appendLine(" ရက်စွဲ : $dateString")
                                appendLine(" ထိုးသူ : $customerName$remarkStr")
                                appendLine(" ဘောင်ချာအမှတ် : ${voucherWithBets.voucher.id}")
                                appendLine("------------------------")
                                val maxAmt = voucherWithBets.bets.maxOfOrNull { it.amount } ?: 0
                                val amtWidth = "%,d".format(maxAmt).length
                                voucherWithBets.bets.forEach { bet ->
                                    val amtStr = "%,d".format(bet.amount).padStart(amtWidth)
                                    appendLine(" ${bet.number.padEnd(5)} = $amtStr Ks")
                                }
                                appendLine("------------------------")
                                appendLine(" စုစုပေါင်း : ${voucherWithBets.voucher.totalAmount} Ks")
                                appendLine("------------------------")
                                appendLine(" $footerText")
                                appendLine("========================")
                                appendLine("      Thank You!      ")
                                appendLine("========================")
                            }
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                val coroutineScope = rememberCoroutineScope()
                                IconButton(onClick = {
                                    coroutineScope.launch { 
                                        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        val paperSize = prefs.getString("paperSize", "58mm") ?: "58mm"
                                        val voucherData = com.threeDLedger.logic.BluetoothPrinter.VoucherData(
                                            batchNumber = voucherWithBets.voucher.batchNumber,
                                            voucherId = voucherWithBets.voucher.id,
                                            date = SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date(voucherWithBets.voucher.timestamp)),
                                            customerName = customerName,
                                            remark = voucherWithBets.voucher.remark,
                                            bets = voucherWithBets.bets.map { it.number to it.amount },
                                            totalAmount = voucherWithBets.voucher.totalAmount,
                                            footerText = footerText
                                        )
                                        val bitmap = com.threeDLedger.logic.BluetoothPrinter.createVoucherBitmap(voucherData, paperSize)
                                        com.threeDLedger.logic.BluetoothPrinter.printBitmap(bitmap, paperSize)
                                    }
                                }) {
                                    Icon(Icons.Default.Print, contentDescription = "Print")
                                }
                                IconButton(onClick = {
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, textToCopy)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                }) {
                                    Icon(Icons.Default.Share, contentDescription = "Share")
                                }
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(textToCopy))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
