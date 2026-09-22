package com.threeDLedger.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threeDLedger.logic.ActivationResult
import com.threeDLedger.logic.LicenseManager
import com.threeDLedger.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val licenseManager = remember { LicenseManager(context) }
    val coroutineScope = rememberCoroutineScope()
    val dimens = rememberResponsiveDimens()

    var cdKey by remember { mutableStateOf(licenseManager.getPendingCdKey() ?: "") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isPendingApproval by remember { mutableStateOf(licenseManager.getPendingCdKey() != null) }
    val expiredWarning by remember { mutableStateOf(licenseManager.getExpiredWarning()) }
    var migrationNotice by remember { mutableStateOf<String?>(null) }

    var isCheckingAutoRestore by remember { mutableStateOf(!licenseManager.isActivated()) }

    LaunchedEffect(Unit) {
        if (licenseManager.isActivated()) {
            onActivated()
            return@LaunchedEffect
        }
        // Attempt cloud auto-restore for previously activated device
        try {
            val restored = licenseManager.autoRestoreLicense()
            if (restored) {
                licenseManager.clearExpiredWarning()
                onActivated()
                return@LaunchedEffect
            }
        } catch (_: Exception) {}
        isCheckingAutoRestore = false
    }

    // Polling while waiting for Admin's Telegram approval
    LaunchedEffect(isPendingApproval, cdKey) {
        if (isPendingApproval && cdKey.isNotBlank()) {
            while (isPendingApproval) {
                delay(3000)
                val check = licenseManager.checkPendingStatus(cdKey)
                if (check is ActivationResult.Success) {
                    isPendingApproval = false
                    licenseManager.clearExpiredWarning()
                    onActivated()
                    break
                } else if (check is ActivationResult.Error) {
                    isPendingApproval = false
                    errorMessage = check.message
                    break
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        if (isCheckingAutoRestore) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (dimens.isCompact) 0.88f else 0.75f)
                    .widthIn(max = 380.dp)
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    Text(
                        "လိုင်စင် အခြေအနေ စစ်ဆေးနေပါသည်...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "ဤဖုန်းတွင် ယခင် အသုံးပြုခဲ့သော လိုင်စင် အသက်ဝင်နေမှုအား အလိုအလျောက် ပြန်လည် ချိတ်ဆက်နေပါသည်",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
        Card(
            modifier = Modifier
                .fillMaxWidth(if (dimens.isCompact) 0.96f else 0.92f)
                .widthIn(max = 480.dp)
                .padding(vertical = dimens.responsiveDp(8.dp, 16.dp, 20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(dimens.responsiveDp(14.dp, 20.dp, 24.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isPendingApproval) {
                    // Pending Approval Waiting View
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.HourglassTop,
                            contentDescription = "Pending Approval",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "ခွင့်ပြုချက် စောင့်ဆိုင်းနေပါသည်",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Telegram Bot သို့ ခွင့်ပြုချက် တောင်းခံလွှာ ပို့ထားပြီး ဖြစ်ပါသည်။ Admin မှ အတည်ပြုပေးသည်နှင့် အလိုအလျောက် ပွင့်သွားပါမည်။",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "တောင်းဆိုထားသော ကုတ်နံပါတ်",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = cdKey,
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isPendingApproval = false
                                errorMessage = null
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("ပယ်ဖျက်မည်")
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    val check = licenseManager.checkPendingStatus(cdKey)
                                    isLoading = false
                                    if (check is ActivationResult.Success) {
                                        licenseManager.clearExpiredWarning()
                                        onActivated()
                                    } else if (check is ActivationResult.Error) {
                                        isPendingApproval = false
                                        errorMessage = check.message
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("စစ်ဆေးမည်")
                            }
                        }
                    }
                } else {
                    // Normal Activation Form

                    // 1. Expiration or Device Migration Warning Banner
                    if (expiredWarning != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            border = BorderStroke(1.5.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = "အသုံးပြုခွင့် သတိပေးချက်",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF991B1B),
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = expiredWarning!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFB91C1C),
                                        lineHeight = 18.sp
                                    )
                                }
                            }
                        }
                    }

                    if (migrationNotice != null) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                            border = BorderStroke(1.5.dp, EmeraldPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 18.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "Success",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = migrationNotice!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldDark
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "Activation Key",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "3D စာရင်း အသုံးပြုခွင့် ဖွင့်ရန်",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "အက်ပ်အား ဆက်လက်အသုံးပြုရန် CD-Key ရိုက်ထည့်ပါ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    // Available Plans Showcase
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(dimens.responsiveDp(10.dp, 12.dp, 14.dp))
                        ) {
                            Text(
                                text = "📋 ရရှိနိုင်သော ဝန်ဆောင်မှု အစီအစဉ်များ",
                                fontWeight = FontWeight.Bold,
                                fontSize = dimens.responsiveSp(11.5.sp, 12.5.sp, 13.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))

                            // 1-Year Plan
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(dimens.responsiveDp(8.dp, 10.dp, 12.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "⭐ ၁ နှစ် (Changeable)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = dimens.responsiveSp(11.sp, 12.sp, 13.sp),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f, fill = false),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                "စက်ပြောင်းနိုင်",
                                                fontSize = dimens.responsiveSp(9.sp, 9.5.sp, 10.sp),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "၁၈၀,၀၀၀ ကျပ် / နှစ် (လက်ကျန်ရက် အပြည့်ဖြင့် ဖုန်းအသစ်ပြောင်းသုံးနိုင်)",
                                        fontSize = dimens.responsiveSp(10.sp, 10.5.sp, 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Lifetime Plan
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(dimens.responsiveDp(8.dp, 10.dp, 12.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "💎 တစ်သက်တာ (Lifetime)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = dimens.responsiveSp(11.sp, 12.sp, 13.sp),
                                            color = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.weight(1f, fill = false),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                "စက်ပြောင်းမရ",
                                                fontSize = dimens.responsiveSp(9.sp, 9.5.sp, 10.sp),
                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "၄၅,၀၀၀ ကျပ် (ဖုန်း ၁ လုံးသာ အသုံးပြုနိုင်)",
                                        fontSize = dimens.responsiveSp(10.sp, 10.5.sp, 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))

                            // Free Trial
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .border(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(dimens.responsiveDp(8.dp, 10.dp, 12.dp)),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "🎁 ၃ ရက် အခမဲ့ စမ်းသပ်ခွင့် (Free 72-Hour Trial)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = dimens.responsiveSp(11.sp, 12.sp, 13.sp),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Telegram Bot တွင် /start နှိပ်၍ အခမဲ့ ကုတ် ရယူပါ (၇၂ နာရီတိတိ)",
                                        fontSize = dimens.responsiveSp(10.sp, 10.5.sp, 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    OutlinedTextField(
                        value = cdKey,
                        onValueChange = {
                            cdKey = it.uppercase()
                            errorMessage = null
                        },
                        label = { Text("CD-Key လိုင်စင်ကုတ်") },
                        placeholder = { Text("XXXX-XXXX-XXXX-XXXX") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        isError = errorMessage != null,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        cdKey = clip.trim().uppercase()
                                        errorMessage = null
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.ContentPaste,
                                    contentDescription = "Clipboard မှ ကူးထည့်မည်",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        textStyle = LocalTextStyle.current.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (cdKey.isBlank()) {
                                errorMessage = "ကျေးဇူးပြု၍ CD-Key ရိုက်ထည့်ပါ"
                                return@Button
                            }

                            isLoading = true
                            errorMessage = null

                            coroutineScope.launch {
                                val result = licenseManager.activateLicense(cdKey)
                                isLoading = false
                                when (result) {
                                    is ActivationResult.Success -> {
                                        licenseManager.clearExpiredWarning()
                                        if (result.deviceMigrated) {
                                            migrationNotice = result.message ?: "စက်အသစ်သို့ အောင်မြင်စွာ ပြောင်းလဲလိုက်ပါပြီ"
                                            delay(1500)
                                        }
                                        onActivated()
                                    }
                                    is ActivationResult.Pending -> {
                                        isPendingApproval = true
                                    }
                                    is ActivationResult.Error -> {
                                        errorMessage = result.message
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimens.responsiveDp(46.dp, 50.dp, 52.dp)),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("အတည်ပြု ဖွင့်လှစ်မည်", fontWeight = FontWeight.Bold, fontSize = dimens.responsiveSp(14.sp, 15.sp, 16.sp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = {
                            val tgUrl = "https://t.me/threed_ledger_bot?start=trial"
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(tgUrl)).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(dimens.responsiveDp(42.dp, 46.dp, 48.dp)),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Telegram Bot မှ ၃ ရက် Trial ကုတ် ရယူမည်",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = dimens.responsiveSp(11.sp, 12.sp, 13.sp),
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            coroutineScope.launch {
                                val restored = licenseManager.autoRestoreLicense()
                                isLoading = false
                                if (restored) {
                                    licenseManager.clearExpiredWarning()
                                    onActivated()
                                } else {
                                    errorMessage = licenseManager.getExpiredWarning() ?: "ဤဖုန်းအတွက် အသက်ဝင်နေသော လိုင်စင် မတွေ့ရှိပါ"
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "🔄 ယခင် လိုင်စင် အလိုအလျောက် ပြန်လည်ရှာဖွေမည်",
                            fontSize = dimens.responsiveSp(11.5.sp, 12.sp, 13.sp),
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
}

