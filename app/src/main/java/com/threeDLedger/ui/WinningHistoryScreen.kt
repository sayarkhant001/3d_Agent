package com.threeDLedger.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.data.ThreeDWinningHistory
import com.threeDLedger.logic.NumberGenerator
import com.threeDLedger.ui.components.tactileCardSurface
import java.text.SimpleDateFormat
import java.util.Locale
import com.threeDLedger.ui.theme.themeEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinningHistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val historyList by viewModel.winningHistory3D.collectAsStateWithLifecycle()
    val isFetching by viewModel.isFetchingHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val soundManager = remember { com.threeDLedger.logic.SoundManager.getInstance(context) }
    var searchQuery by remember { mutableStateOf("") }

    val emeraldPrimary = themeEmerald()
    val emeraldDark = Color(0xFF065F46)
    val emeraldLight = Color(0xFFECFDF5)

    val sortedHistoryList = remember(historyList) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        historyList.sortedWith { a, b ->
            val dateA = try { sdf.parse(a.drawDate) } catch (_: Exception) { null }
            val dateB = try { sdf.parse(b.drawDate) } catch (_: Exception) { null }
            when {
                dateA != null && dateB != null -> dateB.compareTo(dateA)
                dateA != null -> -1
                dateB != null -> 1
                else -> b.id.compareTo(a.id)
            }
        }
    }

    val filteredList = remember(sortedHistoryList, searchQuery) {
        if (searchQuery.isBlank()) sortedHistoryList
        else sortedHistoryList.filter {
            it.winningNumber.contains(searchQuery.trim()) ||
            it.drawDate.contains(searchQuery.trim()) ||
            it.drawDateFormatted.contains(searchQuery.trim()) ||
            it.firstPrize6D.contains(searchQuery.trim())
        }
    }

    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "ရလဒ်မှတ်တမ်း (၁ နှစ်စာ)",
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "ပြီးခဲ့သော ၁ နှစ်စာ 3D ပေါက်ဂဏန်း မှတ်တမ်း",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "နောက်သို့",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.fetch3DHistory() },
                        enabled = !isFetching
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "ပြန်လည်ရယူမည်", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("ဂဏန်း သို့မဟုတ် ရက်စွဲဖြင့် ရှာရန်...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = emeraldPrimary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            if (filteredList.isEmpty() && isFetching) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = emeraldPrimary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "ရလဒ်မှတ်တမ်းများ ရယူနေပါသည်...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ရလဒ်မှတ်တမ်း မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        com.threeDLedger.ui.components.PrimaryGradientButton(
                            onClick = { viewModel.fetch3DHistory() }
                        ) {
                            Text("ပြန်လည် ရယူမည်")
                        }
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredList, key = { "history_${it.id}_${it.drawDate}" }) { item ->
                        ThreeDHistoryCard(
                            modifier = Modifier.animateItem(),
                            history = item,
                            onCopy = {
                                val tutNums = com.threeDLedger.logic.TutCalculator.calculate(item.winningNumber).allTut.sorted()
                                val text = com.threeDLedger.logic.CopyTextFormatter.formatWinningHistoryCopy(
                                    drawDateFormatted = item.drawDateFormatted,
                                    drawDate = item.drawDate,
                                    winningNumber = item.winningNumber,
                                    firstPrize6D = item.firstPrize6D,
                                    tutNumbers = tutNums
                                )
                                soundManager.playCopy()
                                clipboardManager.setText(AnnotatedString(text))
                                android.widget.Toast.makeText(context, "ပေါက်ဂဏန်း (${item.winningNumber}) ကော်ပီ ကူးယူပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ThreeDHistoryCard(
    history: ThreeDWinningHistory,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val emeraldPrimary = themeEmerald()
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val emeraldDark = if (isDark) Color(0xFFA7F3D0) else Color(0xFF065F46)
    val emeraldLight = com.threeDLedger.ui.theme.themeEmeraldContainer()

    val tutNumbers = remember(history.winningNumber) {
        com.threeDLedger.logic.TutCalculator.calculate(history.winningNumber).let { res ->
            (res.permutations + res.nearMisses).distinct()
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .tactileCardSurface(
                shape = RoundedCornerShape(16.dp),
                elevation = 3.dp,
                accentBorder = emeraldPrimary.copy(alpha = 0.55f)
            ),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header Row: Date & Copy Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(emeraldPrimary)
                    )
                    Column {
                        Text(
                            text = history.drawDateFormatted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = history.drawDate,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(44.dp),
                    border = BorderStroke(1.2.dp, emeraldPrimary)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp), tint = emeraldPrimary)
                    Spacer(Modifier.width(5.dp))
                    Text("ကော်ပီ", fontSize = 14.sp, color = emeraldPrimary, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            // Winning Digits Display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "အတည်ပြု ပေါက်ဂဏန်း",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (history.firstPrize6D.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "ပထမဆု (၆ လုံး): ${history.firstPrize6D}",
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                            color = emeraldPrimary
                        )
                    }
                }

                // 3 Digit Blocks
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    history.winningNumber.forEach { digit ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = emeraldLight,
                            border = BorderStroke(1.dp, emeraldPrimary),
                            shadowElevation = 1.dp
                        ) {
                            Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = digit.toString(),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = emeraldDark
                                )
                            }
                        }
                    }
                }
            }

            // Permutations & Tut Numbers (အလှည့် / တွတ် ၇ ကွက်)
            if (tutNumbers.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "အလှည့် (တွတ်): ",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tutNumbers.joinToString(", "),
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = emeraldPrimary,
                            lineHeight = 19.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
