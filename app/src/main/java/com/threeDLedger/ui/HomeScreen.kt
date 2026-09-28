package com.threeDLedger.ui

import com.threeDLedger.logic.LicenseManager

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.R
import com.threeDLedger.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MenuItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColors: List<Color>,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToCustomers: () -> Unit,
    onNavigateToBetting: () -> Unit,
    onNavigateToWinner: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToVouchers: () -> Unit,
    onNavigateToReceipt: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToOverflow: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit = {}
) {
    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    val currentDate = dateFormat.format(Date())
    val currentBatch by viewModel.currentBatch.collectAsStateWithLifecycle()
    val bannedNumbers by viewModel.bannedNumbers.collectAsStateWithLifecycle()
    val vouchersWithBets by viewModel.vouchersWithBets.collectAsStateWithLifecycle()
    val allExportRecords by viewModel.allExportRecords.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val winningNumber by viewModel.winningNumber.collectAsStateWithLifecycle()

    val haptic = LocalHapticFeedback.current
    val rDimens = rememberResponsiveDimens()
    val context = androidx.compose.ui.platform.LocalContext.current
    val licenseManager = remember { LicenseManager(context) }
    var showLicenseDetailsDialog by remember { mutableStateOf(false) }
    var licenseDetails by remember { mutableStateOf(licenseManager.getLicenseDetails()) }
    var isBatchSummaryExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        licenseManager.syncServerTime()
        licenseDetails = licenseManager.getLicenseDetails()
    }
    var lastBackPressTime by remember { mutableLongStateOf(0L) }

    BackHandler {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < 2000) {
            (context as? android.app.Activity)?.finish()
        } else {
            lastBackPressTime = currentTime
            android.widget.Toast.makeText(context, "အက်ပ်မှ ထွက်ရန် နောက်သို့ ထပ်နှိပ်ပါ", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Dynamic Financial Summary for current batch
    val stats = remember(currentBatch, vouchersWithBets, allExportRecords, customers, winningNumber) {
        viewModel.getBatchFinancialSummary(currentBatch)
    }

    val menuItems = listOf(
        MenuItem(
            title = "ကော်မရှင်",
            subtitle = "စာရင်းသွင်းသူများ",
            icon = Icons.Default.People,
            iconColors = listOf(Color(0xFF0284C7), Color(0xFF0369A1)),
            onClick = onNavigateToCustomers
        ),
        MenuItem(
            title = "ဂဏန်းများ",
            subtitle = "ပေါက်ဂဏန်း စစ်ဆေးချက်",
            icon = Icons.AutoMirrored.Filled.List,
            iconColors = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)),
            onClick = onNavigateToLedger
        ),
        MenuItem(
            title = "ဘောင်ချာ",
            subtitle = "ရောင်းရငွေ ဘောင်ချာများ",
            icon = Icons.Default.Receipt,
            iconColors = listOf(Color(0xFFD97706), Color(0xFFB45309)),
            onClick = onNavigateToVouchers
        ),
        MenuItem(
            title = "တင်ကွက်များ",
            subtitle = "အထက်ဒိုင် တင်ကွက်",
            icon = Icons.Default.Payment,
            iconColors = listOf(Color(0xFF7C3AED), Color(0xFF6D28D9)),
            onClick = onNavigateToOverflow
        )
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "3D စာရင်း Logo",
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "3D စာရင်း",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    letterSpacing = 0.3.sp
                                )
                                LicenseStatusBadge(
                                    licenseDetails = licenseDetails,
                                    onClick = { showLicenseDetailsDialog = true }
                                )
                            }
                            Text(
                                text = currentDate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    // Winning History for 1 Year
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToHistory()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = "ထွက်ဂဏန်း မှတ်တမ်း",
                            tint = EmeraldPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Winner Screen Button
                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToWinner()
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A).copy(alpha = 0.8f)),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEvents,
                                contentDescription = "ပေါက်ဂဏန်း",
                                tint = Color(0xFFB45309),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "ပေါက်ဂဏန်း",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 800.dp)
                    .padding(padding)
                    .padding(horizontal = 18.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // ── Tactile Batch Stepper Controller Card ─────────────────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "အကြိမ်",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = rDimens.responsiveSp(16f),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "လက်ရှိ အသုံးပြုနေသော အကြိမ်",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = rDimens.responsiveSp(11f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Stepper buttons + Input
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (currentBatch > 1) viewModel.currentBatch.value = currentBatch - 1
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    Icons.Default.Remove,
                                    contentDescription = "လျှော့မည်",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            var batchText by remember { mutableStateOf(currentBatch.toString()) }
                            LaunchedEffect(currentBatch) {
                                batchText = currentBatch.toString()
                            }

                            OutlinedTextField(
                                value = batchText,
                                onValueChange = { newText ->
                                    batchText = newText
                                    newText.toIntOrNull()?.let { num ->
                                        if (num in 1..1000) viewModel.currentBatch.value = num
                                    }
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .width(76.dp)
                                    .padding(horizontal = 6.dp),
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    if (currentBatch < 999) viewModel.currentBatch.value = currentBatch + 1
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "တိုးမည်",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Tap-to-Show / Expandable Batch Financial Summary ─────────
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.25f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Header: Tap to expand / collapse
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { isBatchSummaryExpanded = !isBatchSummaryExpanded }
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "📊 အကြိမ် #${currentBatch} ရှင်းတမ်း အနှစ်ချုပ်",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = EmeraldPrimary
                                )
                                Icon(
                                    if (isBatchSummaryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (stats.isDeclared) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFFEE2E2),
                                        border = BorderStroke(1.dp, Color(0xFFFCA5A5))
                                    ) {
                                        Text(
                                            text = "🏆 ပေါက်: ${stats.winningNumber}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color(0xFFB91C1C),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFFDCFCE7),
                                        border = BorderStroke(1.dp, Color(0xFF86EFAC))
                                    ) {
                                        Text(
                                            text = "🟢 ဖွင့်လှစ်ဆဲ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (!isBatchSummaryExpanded) {
                                    Text(
                                        text = "ကြည့်ရန် နှိပ်ပါ",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldPrimary
                                    )
                                }
                            }
                        }

                        // Collapsible Stats Grid Content
                        AnimatedVisibility(
                            visible = isBatchSummaryExpanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Row 1: Sales & Net Balance
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BatchStatItem(
                                        modifier = Modifier.weight(1f),
                                        label = "အရောင်းကြေး",
                                        value = "%,d Ks".format(stats.totalSales),
                                        icon = Icons.Default.AccountBalanceWallet,
                                        accentColor = EmeraldPrimary
                                    )
                                    BatchStatItem(
                                        modifier = Modifier.weight(1f),
                                        label = "ကျန်ရှိငွေ",
                                        value = "%,d Ks".format(stats.netBalance),
                                        icon = Icons.Default.AccountBalance,
                                        accentColor = Color(0xFF059669)
                                    )
                                }

                                // Row 2: Commission & Export / Winning Payout
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BatchStatItem(
                                        modifier = Modifier.weight(1f),
                                        label = "ကော်မရှင်ခ",
                                        value = "%,d Ks".format(stats.commissionAmount),
                                        icon = Icons.Default.Percent,
                                        accentColor = Color(0xFFD97706)
                                    )
                                    BatchStatItem(
                                        modifier = Modifier.weight(1f),
                                        label = if (stats.isDeclared) "ပေါက်သီး လျော်ငွေ" else "တင်ကွက်ငွေ",
                                        value = "%,d Ks".format(if (stats.isDeclared) stats.winningPayout else stats.exportedAmount.toLong()),
                                        icon = if (stats.isDeclared) Icons.Default.EmojiEvents else Icons.Default.Payment,
                                        accentColor = if (stats.isDeclared) Color(0xFFDC2626) else Color(0xFF7C3AED)
                                    )
                                }

                                // Row 3: Operational Counts
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    BatchStatItem(
                                        modifier = Modifier.weight(1f),
                                        label = "ဘောင်ချာများ (အားလုံး)",
                                        value = "%,d စောင်".format(stats.voucherCount),
                                        icon = Icons.Default.Receipt,
                                        accentColor = Color(0xFF0891B2)
                                    )
                                    BatchStatItem(
                                        modifier = Modifier.weight(1f),
                                        label = "ထိုးသား ဦးရေ",
                                        value = "%,d ဦး".format(stats.customerCount),
                                        icon = Icons.Default.People,
                                        accentColor = Color(0xFF4F46E5)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Hero Action Card: "ထိုးကြေး စာရင်းသွင်းမည်" (Direct Betting Entry) ────
                Card(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToBetting()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = EmeraldPrimary)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF046A4E),
                                        Color(0xFF065F46)
                                    )
                                )
                            )
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Calculate,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "ထိုးကြေး စာရင်းသွင်းမည်",
                                        fontWeight = FontWeight.Black,
                                        fontSize = rDimens.responsiveSp(16f),
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "ကီးပက်ဖြင့် အမြန် စာရင်းသွင်းရန် နှိပ်ပါ",
                                        fontSize = rDimens.responsiveSp(11.5f),
                                        color = Color.White.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color(0xFFFFD93D),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (bannedNumbers.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.error, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Info, contentDescription = "သတိပေးချက်", tint = MaterialTheme.colorScheme.onError, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "ပိတ်ထားသော ဂဏန်းများ",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = bannedNumbers.joinToString(", ") {
                                        if (it.amountLimit > 0) "${it.number} (≤%,d Ks)".format(it.amountLimit)
                                        else "${it.number} (လုံးဝပိတ်)"
                                    },
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // ── 2x2 Grid Menu with Tactile Medallion Cards ─────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        MenuCard(
                            title = menuItems[0].title,
                            subtitle = menuItems[0].subtitle,
                            icon = menuItems[0].icon,
                            iconColors = menuItems[0].iconColors,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                menuItems[0].onClick()
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        MenuCard(
                            title = menuItems[1].title,
                            subtitle = menuItems[1].subtitle,
                            icon = menuItems[1].icon,
                            iconColors = menuItems[1].iconColors,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                menuItems[1].onClick()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        MenuCard(
                            title = menuItems[2].title,
                            subtitle = menuItems[2].subtitle,
                            icon = menuItems[2].icon,
                            iconColors = menuItems[2].iconColors,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                menuItems[2].onClick()
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        MenuCard(
                            title = menuItems[3].title,
                            subtitle = menuItems[3].subtitle,
                            icon = menuItems[3].icon,
                            iconColors = menuItems[3].iconColors,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                menuItems[3].onClick()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // ── Settings & Preferences Navigation Card ───────────────────
                Card(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToSettings()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(18.dp)),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = "ဆက်တင်",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "ဆက်တင်နှင့် အချက်အလက်",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "လိုင်စင်၊ စကားဝှက်၊ အရန်သိမ်းဆည်းမှု စီမံရန်",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        if (showLicenseDetailsDialog) {
            LicenseDetailsDialog(
                licenseManager = licenseManager,
                onDismiss = {
                    showLicenseDetailsDialog = false
                    licenseDetails = licenseManager.getLicenseDetails()
                }
            )
        }

        if (licenseDetails.isClockTampered) {
            ClockTamperedBlockDialog(
                licenseManager = licenseManager,
                onRestored = {
                    licenseDetails = licenseManager.getLicenseDetails()
                }
            )
        }
    }
}

// ── Financial Stat Item Component ────────────────────────────────────────────
@Composable
fun BatchStatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = value,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Tactile Pro Medallion Menu Card ──────────────────────────────────────────
@Composable
fun MenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColors: List<Color>,
    onClick: () -> Unit
) {
    val rDimens = rememberResponsiveDimens()
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (rDimens.isCompact) 112.dp else 124.dp)
            .shadow(2.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDimens.responsiveDp(14f)),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            // Gradient Icon Medallion
            Box(
                modifier = Modifier
                    .size(if (rDimens.isCompact) 40.dp else 44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(colors = iconColors)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(if (rDimens.isCompact) 20.dp else 22.dp),
                    tint = Color.White
                )
            }

            Spacer(Modifier.height(8.dp))

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = rDimens.responsiveSp(15.5f),
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = rDimens.responsiveSp(11f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
