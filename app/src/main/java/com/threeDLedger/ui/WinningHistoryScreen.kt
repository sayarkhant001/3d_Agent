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
import java.text.SimpleDateFormat
import java.util.Locale

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
    var searchQuery by remember { mutableStateOf("") }

    val emeraldPrimary = Color(0xFF047857)
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
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = "ပြီးခဲ့သော ၁ နှစ်စာ 3D ပေါက်ဂဏန်း မှတ်တမ်း",
                            fontSize = 11.sp,
                            color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "နောက်သို့", tint = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.fetch3DHistory() },
                        enabled = !isFetching
                    ) {
                        if (isFetching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = if (isDark) MaterialTheme.colorScheme.primary else Color.White)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "ပြန်လည်ရယူမည်", tint = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isDark) MaterialTheme.colorScheme.surface else emeraldPrimary)
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
                        Button(
                            onClick = { viewModel.fetch3DHistory() },
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary)
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
                            history = item,
                            onCopy = {
                                val text = "ရက်စွဲ: ${item.drawDateFormatted} (${item.drawDate})\nပေါက်ဂဏန်း: ${item.winningNumber}\nပထမဆု: ${item.firstPrize6D}"
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
    onCopy: () -> Unit
) {
    val emeraldPrimary = Color(0xFF047857)
    val emeraldDark = Color(0xFF065F46)
    val emeraldLight = Color(0xFFECFDF5)

    val tutNumbers = remember(history.winningNumber) {
        if (history.winningNumber.length == 3) {
            val perms = (NumberGenerator.permutations(history.winningNumber).toSet() - setOf(history.winningNumber)).sorted()
            val winInt = history.winningNumber.toIntOrNull() ?: 0
            val numPlus1 = String.format("%03d", (winInt + 1) % 1000)
            val numMinus1 = String.format("%03d", if (winInt == 0) 999 else winInt - 1)
            val near = listOf(numMinus1, numPlus1).filter { it != history.winningNumber }
            (perms + near).distinct()
        } else emptyList()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, emeraldPrimary.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(2.dp)
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
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCopy,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp),
                    border = BorderStroke(1.dp, emeraldPrimary)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(13.dp), tint = emeraldPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("ကော်ပီ", fontSize = 11.sp, color = emeraldPrimary, fontWeight = FontWeight.Bold)
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (history.firstPrize6D.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "ပထမဆု (၆ လုံး): ${history.firstPrize6D}",
                            fontSize = 11.5.sp,
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tutNumbers.joinToString(", "),
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4338CA),
                            lineHeight = 16.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
