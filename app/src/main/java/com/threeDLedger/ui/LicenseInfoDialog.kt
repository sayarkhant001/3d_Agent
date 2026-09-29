package com.threeDLedger.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.threeDLedger.R
import com.threeDLedger.logic.LicenseDetails
import com.threeDLedger.logic.LicenseManager
import com.threeDLedger.logic.LicensePlanType
import com.threeDLedger.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Interactive TopAppBar badge representing license status:
 * - "အစမ်းသုံး" (Trial)
 * - "Pro 1 Year" (1 Year License)
 * - "Pro Lifetime" (Lifetime License)
 * - "အချိန်စစ်ဆေးရန်" (Clock Tampering Error)
 */
@Composable
fun LicenseStatusBadge(
    licenseDetails: LicenseDetails,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    val badgeBg = when {
        licenseDetails.isClockTampered -> Color(0xFFFEE2E2)
        licenseDetails.planType == LicensePlanType.TRIAL -> Color(0xFFFEF3C7)
        licenseDetails.planType == LicensePlanType.LIFETIME -> Color(0xFFD1FAE5)
        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
    }

    val badgeBorder = when {
        licenseDetails.isClockTampered -> Color(0xFFEF4444)
        licenseDetails.planType == LicensePlanType.TRIAL -> Color(0xFFF59E0B)
        licenseDetails.planType == LicensePlanType.LIFETIME -> Color(0xFF10B981)
        else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    }

    val badgeTextColor = when {
        licenseDetails.isClockTampered -> Color(0xFFDC2626)
        licenseDetails.planType == LicensePlanType.TRIAL -> Color(0xFFB45309)
        licenseDetails.planType == LicensePlanType.LIFETIME -> Color(0xFF047857)
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        shape = RoundedCornerShape(6.dp),
        color = badgeBg,
        border = BorderStroke(0.8.dp, badgeBorder),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (licenseDetails.isClockTampered) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = badgeTextColor,
                    modifier = Modifier.size(11.dp)
                )
            } else if (licenseDetails.planType == LicensePlanType.LIFETIME) {
                Icon(
                    imageVector = Icons.Default.Diamond,
                    contentDescription = null,
                    tint = badgeTextColor,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = if (licenseDetails.isClockTampered) "အချိန်စစ်ဆေးရန်" else licenseDetails.badgeText,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 10.sp,
                color = badgeTextColor
            )
        }
    }
}

/**
 * Interactive Dialog showing full license details:
 * - Remaining days (working by calendar)
 * - Expiry date to renew
 * - Lifetime unlimited status
 * - Sync server MMT action
 * - Renew via Telegram action
 */
@Composable
fun LicenseDetailsDialog(
    licenseManager: LicenseManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }
    var currentDetails by remember { mutableStateOf(licenseManager.getLicenseDetails()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "3D စာရင်း Logo",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0xFFD4AF37).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text("3D စာရင်း လိုင်စင် အချက်အလက်", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("လိုင်စင် အချက်အလက်နှင့် သက်တမ်းတိုးရန်", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (currentDetails.planType) {
                        LicensePlanType.TRIAL -> Color(0xFFFEF3C7)
                        LicensePlanType.LIFETIME -> Color(0xFFD1FAE5)
                        LicensePlanType.ONE_YEAR -> MaterialTheme.colorScheme.primaryContainer
                    }
                ) {
                    Text(
                        text = currentDetails.badgeText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = when (currentDetails.planType) {
                            LicensePlanType.TRIAL -> Color(0xFFB45309)
                            LicensePlanType.LIFETIME -> Color(0xFF047857)
                            LicensePlanType.ONE_YEAR -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Info Summary Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Plan Type
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("လိုင်စင် အမျိုးအစား :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = when (currentDetails.planType) {
                                    LicensePlanType.TRIAL -> "အစမ်းသုံး (72-Hour Free Trial)"
                                    LicensePlanType.LIFETIME -> "တစ်သက်တာ (Pro Lifetime)"
                                    LicensePlanType.ONE_YEAR -> "၁ နှစ် (Pro 1 Year)"
                                },
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // CD-Key with One-Tap Copy
                        val rawKey = currentDetails.activeCdKey
                        if (!rawKey.isNullOrBlank()) {
                            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(rawKey))
                                    android.widget.Toast.makeText(context, "CD Key ($rawKey) ကူးယူပြီးပါပြီ။ အခြားစက်များတွင် အသုံးပြုနိုင်ပါသည်", android.widget.Toast.LENGTH_LONG).show()
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, EmeraldPrimary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "လိုင်စင် CD-Key (ကူးယူရန် နှိပ်ပါ) :",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = rawKey,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = EmeraldPrimary,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = EmeraldLight.copy(alpha = 0.6f)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
                                                contentDescription = "Copy CD Key",
                                                tint = EmeraldPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                "ကူးယူမည်",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldPrimary
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Remaining Days (Calendar Calculation)
                        if (currentDetails.planType == LicensePlanType.LIFETIME) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ကျန်ရှိသော ရက်ပေါင်း :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Surface(
                                    color = Color(0xFFDCFCE7),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, Color(0xFF86EFAC))
                                ) {
                                    Text(
                                        text = "သက်တမ်း ကန့်သတ်ချက် မရှိပါ",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("အသုံးပြုခွင့် သက်တမ်း :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = "အမြဲတမ်း Pro အသုံးပြုခွင့် ရရှိထားပါသည်",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF047857)
                                )
                            }
                        } else {
                            val remDays = currentDetails.remainingDays ?: 0L
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ကျန်ရှိသော ရက်ပေါင်း :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Surface(
                                    color = if (remDays <= 3L) Color(0xFFFEE2E2) else MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(0.8.dp, if (remDays <= 3L) Color(0xFFFCA5A5) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "%,d ရက် ကျန်ပါသည်".format(remDays),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.sp,
                                        color = if (remDays <= 3L) Color(0xFFDC2626) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Expiry Date formatted
                            val expDate = currentDetails.expiryDateFormatted ?: "-"
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("သက်တမ်းကုန်ဆုံးမည့်ရက် :", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = expDate,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Time Sync Note
                        val lastSync = currentDetails.lastSyncMmtFormatted
                        if (!lastSync.isNullOrBlank()) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("မြန်မာစံတော်ချိန် (MMT) :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(lastSync, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                // Telegram Renew Button (if trial or expiring within 30 days)
                val isExpiringSoon = (currentDetails.remainingDays ?: 100L) <= 30L || currentDetails.planType == LicensePlanType.TRIAL
                if (isExpiringSoon && currentDetails.planType != LicensePlanType.LIFETIME) {
                    Button(
                        onClick = {
                            val tgUrl = "https://t.me/threeDLedger2026bot?start=renew"
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(tgUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Telegram အက်ပ် မတွေ့ရှိပါ", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (currentDetails.planType == LicensePlanType.TRIAL) "Pro သို့ အဆင့်မြှင့်တင်ရန် ဆက်သွယ်မည်" else "သက်တမ်းတိုးရန် Admin ဆက်သွယ်မည်",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp
                        )
                    }
                }

                // Sync Time Button
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            isSyncing = true
                            val success = licenseManager.syncServerTime()
                            isSyncing = false
                            if (success) {
                                currentDetails = licenseManager.getLicenseDetails()
                                Toast.makeText(context, "မြန်မာစံတော်ချိန် တိုက်ဆိုင်စစ်ဆေးပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "အင်တာနက် စစ်ဆေးပြီး ပြန်လည်ကြိုးစားပါ", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isSyncing,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("ချိန်ညှိနေသည်...", fontSize = 12.sp)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("အင်တာနက်ဖြင့် အချိန် ပြန်လည်ချိန်ညှိမည် (Sync MMT)", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ပိတ်မည်", fontWeight = FontWeight.Bold)
            }
        }
    )
}

/**
 * Full-screen blocking modal displayed when phone clock has been drawn back (clock rollback tampering).
 * Blocks access to all app features until the user connects to internet and synchronizes real MMT.
 */
@Composable
fun ClockTamperedBlockDialog(
    licenseManager: LicenseManager,
    onRestored: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSyncing by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* Non-dismissible: Must sync time! */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.2.dp, Color(0xFFEF4444)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFFEE2E2),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.HourglassDisabled,
                            contentDescription = null,
                            tint = Color(0xFFDC2626),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Text(
                    text = "အချိန်နှင့် နေ့စွဲ မှားယွင်းနေပါသည်",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFDC2626),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "ဖုန်း၏ နေ့စွဲနှင့် အချိန် နောက်ပြန်ဆုတ်ထားသည်ကို စစ်ဆေးတွေ့ရှိရပါသည် (အချိန် ပြန်လည်ပြင်ဆင်ထားမှု တွေ့ရှိ)။",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "လိုင်စင် သက်တမ်း တိကျမှန်ကန်စေရန် အင်တာနက် ဖွင့်ထားပေးပြီး မြန်မာစံတော်ချိန်ကို ပြန်လည်ချိန်ညှိပေးပါ။",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = {
                        coroutineScope.launch {
                            isSyncing = true
                            val success = licenseManager.syncServerTime()
                            isSyncing = false
                            if (success && !licenseManager.timeIntegrity.isClockTampered()) {
                                Toast.makeText(context, "မြန်မာစံတော်ချိန် အောင်မြင်စွာ ပြန်လည်ချိန်ညှိပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                                onRestored()
                            } else {
                                Toast.makeText(context, "အင်တာနက် ချိတ်ဆက်၍ မရသေးပါ။ ကွန်ရက် ဖွင့်ပြီး ပြန်လည်ကြိုးစားပါ", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("ချိန်ညှိနေသည်...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("အင်တာနက်ဖြင့် အချိန် ပြန်ညှိမည်", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                    }
                }
            }
        }
    }
}
