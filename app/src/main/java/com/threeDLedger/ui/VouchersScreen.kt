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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.threeDLedger.ui.theme.rememberResponsiveDimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(
    viewModel: MainViewModel,
    initialCustomerId: Int? = null,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val dimens = rememberResponsiveDimens()
    val vouchers by viewModel.vouchersWithCustomer.collectAsStateWithLifecycle()
    val allVouchersWithBets by viewModel.vouchersWithBets.collectAsStateWithLifecycle()
    val footerText by viewModel.voucherFooterText.collectAsStateWithLifecycle()
    
    val filteredVouchers = (if (initialCustomerId != null) {
        allVouchersWithBets.filter { it.voucher.customerId == initialCustomerId }
    } else {
        allVouchersWithBets
    }).filter {
        !it.voucher.remark.contains("တင်ကွက်") &&
        !it.voucher.remark.contains("overflow", ignoreCase = true)
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
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(dimens.responsiveDp(8.dp, 12.dp, 16.dp))) {
            items(filteredVouchers) { voucherWithBets ->
                val customerName = vouchers.find { it.voucher.id == voucherWithBets.voucher.id }?.customer?.name ?: "Unknown"
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(dimens.responsiveDp(10.dp, 14.dp, 16.dp))) {
                        Text("ဘောင်ချာအမှတ်: ${voucherWithBets.voucher.id} (အကြိမ်: ${voucherWithBets.voucher.batchNumber})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = dimens.responsiveSp(12.5.sp, 13.5.sp, 14.5.sp))
                        Text("အမည်: $customerName", fontSize = dimens.responsiveSp(12.sp, 13.sp, 14.sp))
                        
                        val dateString = SimpleDateFormat("yyyy.MM.dd/HH:mm:ss").format(Date(voucherWithBets.voucher.timestamp))
                        Text("အချိန်: $dateString", style = MaterialTheme.typography.bodySmall, fontSize = dimens.responsiveSp(10.5.sp, 11.5.sp, 12.sp))
                        
                        Spacer(modifier = Modifier.height(8.dp))

                        // === BET TABLE — Numbers page format with alternating color lines & robust layout ===
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, Color(0xFF059669)),
                            shadowElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // Table Header (identical to Numbers page)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color(0xFFDCFCE7)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "စဉ်   ဂဏန်း:",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF065F46)
                                        )
                                        Text(
                                            "ထိုးငွေ ပမာဏ",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp,
                                            color = Color(0xFF065F46)
                                        )
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF6EE7B7), thickness = 1.dp)

                                // Alternating rows (color lines)
                                voucherWithBets.bets.forEachIndexed { idx, bet ->
                                    val isEven = idx % 2 == 0
                                    val rowBg = if (isEven) Color.White else Color(0xFFF0FDF4)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(rowBg)
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Left: Index & Number (never wraps even on large display sizes)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                "${idx + 1}.",
                                                fontSize = 13.sp,
                                                color = Color(0xFF64748B),
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.widthIn(min = 28.dp),
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                bet.number,
                                                color = Color(0xFF047857),
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 18.sp,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 1.5.sp,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }

                                        // Right: Amount & Ks (never wraps)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "%,d".format(bet.amount),
                                                color = Color(0xFF0F172A),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "Ks",
                                                color = Color(0xFF64748B),
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                    }

                                    if (idx < voucherWithBets.bets.size - 1) {
                                        HorizontalDivider(
                                            color = Color(0xFFE2E8F0),
                                            thickness = 0.5.dp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "စုစုပေါင်း: %,d Ks".format(voucherWithBets.voucher.totalAmount),
                                fontWeight = FontWeight.Bold,
                                fontSize = dimens.responsiveSp(13.sp, 14.5.sp, 16.sp),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                            
                            val remarkStr = if (voucherWithBets.voucher.remark.isNotEmpty()) " (${voucherWithBets.voucher.remark})" else ""
                            val textToCopy = buildString {
                                appendLine("========================")
                                appendLine("      3D ဘောင်ချာ      ")
                                appendLine("========================")
                                appendLine(" ဘောင်ချာအမှတ်-${voucherWithBets.voucher.id}  အကြိမ် : ${voucherWithBets.voucher.batchNumber}")
                                appendLine(" ရက်စွဲ : $dateString")
                                appendLine(" ထိုးသူ : $customerName$remarkStr")
                                appendLine("------------------------")
                                val maxAmt = voucherWithBets.bets.maxOfOrNull { it.amount } ?: 0
                                val amtWidth = "%,d".format(maxAmt).length
                                voucherWithBets.bets.forEach { bet ->
                                    val amtStr = "%,d".format(bet.amount).padStart(amtWidth)
                                    appendLine(" ${bet.number.padStart(3)} = $amtStr Ks")
                                }
                                appendLine("------------------------")
                                appendLine(" ထိုးကြေးငွေ = ${ "%,d".format(voucherWithBets.voucher.totalAmount).padStart(amtWidth)} Ks")
                                appendLine("------------------------")
                                appendLine(" $footerText")
                                appendLine("========================")
                                appendLine("      ကျေးဇူးတင်ပါသည်      ")
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
