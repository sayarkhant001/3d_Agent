package com.threeDLedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.data.Bet
import com.threeDLedger.logic.NumberGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Myanmar digit to English digit converter
fun String.myanmarToEnglish(): String {
    val myanmarDigits = "၀၁၂၃၄၅၆၇၈၉"
    val englishDigits = "0123456789"
    return this.map { c ->
        val idx = myanmarDigits.indexOf(c)
        if (idx >= 0) englishDigits[idx] else c
    }.joinToString("")
}

// Parse one line of pasted bet text into a list of (number, amount) pairs.
// Handles ALL these real-world formats:
//   723-372-245-309 = 2000         -> 4 numbers at 2000
//   446=1000                       -> 1 number at 1000
//   235-615 = 3000                 -> 2 numbers at 3000
//   456=5000r1000                  -> 456 at 5000, permutations at 1000
//   456R=5000  OR  456/=5000       -> 456 + all perms at 5000  (R and / both mean Round)
//   123/5000   OR  123R5000        -> 123 + perms at 5000
//   123/456/789=5000               -> 123+perms, 456+perms, 789+perms at 5000
//   185-217-378-549 = 10000        -> 4 numbers at 10000
fun parsePastedLine(raw: String): List<Pair<String, Int>> {
    // Convert Myanmar digits -> English and trim
    val line = raw.trim().myanmarToEnglish()
    if (line.isBlank()) return emptyList()

    // Step 1: collapse spaces around plain separators (NOT / -- handled below)
    var text = line.replace(Regex("""\s*([=:\-.,_])\s*"""), "$1")

    // Step 2: normalise R, r, AND / -> "R"  (/ is treated as Round, same as R)
    text = text.replace(Regex("""\s*[Rr/]\s*"""), "R")

    // Find the AMOUNT at the end: (optional separator)(digits)(optional R digits)$
    val tailRegex = Regex("""[=:\-.,_]?(\d+)(?:R(\d+))?$""")
    val tailMatch = tailRegex.find(text) ?: return emptyList()

    val amount  = tailMatch.groupValues[1].toIntOrNull() ?: return emptyList()
    val rAmount = tailMatch.groupValues[2].toIntOrNull()

    // Everything BEFORE the tail match is the numbers section
    val numbersStr = text.substring(0, tailMatch.range.first)
    if (numbersStr.isBlank()) return emptyList()

    // Step 3: extract (number, hasR) pairs using regex.
    // After normalisation "123/456/789=5000" -> "123R456R789=5000"
    // Pattern matches each 2-3 digit number and its optional trailing R
    val numPattern = Regex("""(\d{2,3})(R?)""")
    val results = mutableListOf<Pair<String, Int>>()

    for (match in numPattern.findAll(numbersStr)) {
        val baseNum = match.groupValues[1]
        val hasR    = match.groupValues[2] == "R"

        if (!baseNum.all { it.isDigit() }) continue

        results.add(baseNum to amount)

        when {
            // Global R amount wins (e.g. 456=5000R1000 -> perms at 1000)
            rAmount != null && rAmount > 0 -> {
                NumberGenerator.permutations(baseNum).forEach { perm ->
                    if (perm != baseNum) results.add(perm to rAmount)
                }
            }
            // Number-local R/slash marker (e.g. "456R=5000" or "456/5000")
            hasR -> {
                NumberGenerator.permutations(baseNum).forEach { perm ->
                    if (perm != baseNum) results.add(perm to amount)
                }
            }
        }
    }

    // De-duplicate: same number appearing twice -> sum amounts
    val merged = linkedMapOf<String, Int>()
    results.forEach { (num, amt) -> merged[num] = (merged[num] ?: 0) + amt }
    return merged.entries.map { it.key to it.value }
}


enum class FocusField { NUMBER, AMOUNT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BettingScreen(
    viewModel: MainViewModel,
    initialCustomerId: Int? = null,
    onNavigateBack: () -> Unit,
    onNavigateToCustomerVouchers: (Int) -> Unit = {}
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val currentBatch by viewModel.currentBatch.collectAsStateWithLifecycle()
    var selectedCustomer by remember { mutableStateOf<Int?>(initialCustomerId) }
    var expandedCustomer by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.bannedNumberEvent.collect {
            android.widget.Toast.makeText(context, "ထိုးထားသော ဂဏန်းများထဲတွင် ပိတ်ထားသော ဂဏန်းများ ပါဝင်နေသဖြင့် ဖယ်ရှားလိုက်ပါသည်", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    var currentBetType  by remember { mutableStateOf("ဒဲ့") }

    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText       by remember { mutableStateOf("") }
    var isParsing       by remember { mutableStateOf(false) }
    var parseProgress   by remember { mutableStateOf(0f) }
    var parseStatus     by remember { mutableStateOf("") }

    val pendingBets = remember { mutableStateListOf<Bet>() }

    var focusedField by remember { mutableStateOf(FocusField.NUMBER) }
    var tempNumber   by remember { mutableStateOf("") }
    var tempAmount   by remember { mutableStateOf("1000") }
    var tempRemark   by remember { mutableStateOf("") }

    val coroutineScope = rememberCoroutineScope()

    val quickAmounts = listOf("100", "300", "500", "1000", "2000", "5000", "10000")

    fun addBets(numbers: List<String>) {
        val amount = tempAmount.toIntOrNull() ?: 0
        if (amount <= 0) return
        val bannedList = viewModel.bannedNumbers.value.map { it.number }
        var bannedFound = false
        val validNumbers = numbers.filter { 
            if (it in bannedList) { bannedFound = true; false } else true 
        }
        for (num in validNumbers) {
            // Append at end like normal list, or insert at 0
            pendingBets.add(Bet(voucherId = 0, number = num, amount = amount))
        }
        if (bannedFound) {
            android.widget.Toast.makeText(context, "ပိတ်ထားသော ဂဏန်းများ ပါဝင်နေ၍ ဖယ်ထုတ်လိုက်ပါသည်", android.widget.Toast.LENGTH_SHORT).show()
        }
        tempNumber = "" // reset temp input
        focusedField = FocusField.NUMBER
    }
    
    fun appendText(txt: String) {
        if (focusedField == FocusField.NUMBER) {
            if (tempNumber.length < 3) tempNumber += txt
        } else {
            if (tempAmount == "0" || tempAmount.isEmpty()) {
                tempAmount = txt
            } else {
                tempAmount += txt
            }
        }
    }
    
    fun backspace() {
        if (focusedField == FocusField.NUMBER && tempNumber.isNotEmpty()) {
            tempNumber = tempNumber.dropLast(1)
        } else if (focusedField == FocusField.AMOUNT && tempAmount.isNotEmpty()) {
            tempAmount = tempAmount.dropLast(1)
        }
    }
    
    fun clearAll() {
        tempNumber = ""
        tempAmount = "1000"
        focusedField = FocusField.NUMBER
    }
    
    // Async paste processing — runs IO-heavy parsing off the main thread.
    // For <= 500 resulting bets: adds to pendingBets (shows in list).
    // For > 500: submits directly as a voucher so the list never lags.
    fun addBetsFromPasteAsync(text: String) {
        if (selectedCustomer == null && text.lines().size > 500) {
            android.widget.Toast.makeText(context, "ထိုးသူ ရွေးပါ — large paste needs customer selected", android.widget.Toast.LENGTH_SHORT).show()
        }
        isParsing = true
        parseProgress = 0f
        parseStatus = "ပြင်ဆင်နေသည်..."
        coroutineScope.launch {
            val bannedList = viewModel.bannedNumbers.value.map { it.number }.toHashSet()
            val lines = text.lines().filter { it.isNotBlank() }
            val total = lines.size.coerceAtLeast(1)
            val allBets = ArrayList<Bet>(total * 2)
            var bannedCount = 0

            withContext(Dispatchers.Default) {
                lines.forEachIndexed { i, line ->
                    val parsed = parsePastedLine(line)
                    parsed.forEach { (num, amt) ->
                        if (amt > 0) {
                            if (num in bannedList) bannedCount++
                            else allBets.add(Bet(voucherId = 0, number = num, amount = amt))
                        }
                    }
                    if (i % 200 == 0) {
                        withContext(Dispatchers.Main) {
                            parseProgress = i.toFloat() / total
                            parseStatus = " ကြောင်း ရှာနေသည်..."
                        }
                    }
                }
            }

            // Back on Main thread — update UI
            val addedCount = allBets.size
            if (addedCount <= 500) {
                // Small batch: buffer in the list so user can review
                pendingBets.addAll(allBets)
            } else {
                // Large batch: submit straight to DB in one voucher to keep UI responsive
                if (selectedCustomer != null) {
                    val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                    // Split into chunks of 1000 bets per voucher so Room doesn't time out
                    allBets.chunked(1000).forEach { chunk ->
                        viewModel.addVoucherWithBetList(selectedCustomer!!, time, chunk, tempRemark)
                    }
                    tempRemark = ""
                    android.widget.Toast.makeText(context, " ကြောင်း တိုက်ရိုက်သိမ်းဆည်းပြီး", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    // No customer selected — still buffer (they can submit later)
                    pendingBets.addAll(allBets)
                }
            }

            if (bannedCount > 0)
                android.widget.Toast.makeText(context, " ကြောင်း ပိတ်ဂဏန်းများ ဖယ်ထုတ်ပြီး", android.widget.Toast.LENGTH_SHORT).show()
            if (addedCount > 0 && addedCount <= 500)
                android.widget.Toast.makeText(context, " ကြောင်း ထည့်သွင်းပြီး", android.widget.Toast.LENGTH_SHORT).show()

            isParsing = false
            parseProgress = 1f
            parseStatus = " ကြောင်း"
        }
    }


    fun submit() {
        val num = tempNumber.toIntOrNull()
        val digits = tempNumber

        if (tempNumber.isEmpty()) return

        when (currentBetType) {
            "ဒဲ့" -> if (digits.length == 3) addBets(listOf(digits))
            "ထိပ်" -> if (num != null && digits.length == 1) addBets(NumberGenerator.head(num))
            "လယ်" -> if (num != null && digits.length == 1) addBets(NumberGenerator.middle(num))
            "ပိတ်" -> if (num != null && digits.length == 1) addBets(NumberGenerator.tail(num))
            "အပါ" -> if (num != null && digits.length == 1) addBets(NumberGenerator.include(num))
        }
    }

    fun handleSpecial(cmd: String) {
        val num = tempNumber.toIntOrNull()
        val digits = tempNumber
        when (cmd) {
            "ရှေ့စီးရီး" -> if (digits.length == 2) addBets(NumberGenerator.frontSeries(digits[0].digitToInt(), digits[1].digitToInt()))
            "လယ်စီးရီး" -> if (digits.length == 2) addBets(NumberGenerator.middleSeries(digits[0].digitToInt(), digits[1].digitToInt()))
            "နောက်စီးရီး" -> if (digits.length == 2) addBets(NumberGenerator.backSeries(digits[0].digitToInt(), digits[1].digitToInt()))
            "ဘရိတ်" -> if (num != null && digits.length == 1) addBets(NumberGenerator.breakNum(num))
            "ထွိုင်" -> addBets(NumberGenerator.tri())
            "ရှေ့ပူး" -> addBets(NumberGenerator.frontDouble())
            "နောက်ပူး" -> addBets(NumberGenerator.backDouble())
            "အခွ" -> addBets(NumberGenerator.cycle())
            "R" -> if (digits.length == 3) addBets(NumberGenerator.permutations(digits))
            "/" -> backspace()
            "ဖျက်" -> backspace()
            "ရှင်းပါ" -> clearAll()
        }
    }

    fun submitVoucher() {
        if (selectedCustomer == null) {
            expandedCustomer = true
            android.widget.Toast.makeText(context, "ထိုးသူ ရွေးပါ", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (pendingBets.isEmpty()) return

        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        viewModel.addVoucherWithBetList(selectedCustomer!!, time, pendingBets.toList(), tempRemark)
        tempRemark = ""
        pendingBets.clear()
        clearAll()
        android.widget.Toast.makeText(context, "ဘောင်ချာ သိမ်းဆည်းပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    // Theme-aware color aliases (respond to Emerald-Gold palette)
    val primaryBlue  = MaterialTheme.colorScheme.primary
    val buttonTeal   = MaterialTheme.colorScheme.tertiary
    val buttonGreen  = MaterialTheme.colorScheme.primaryContainer
    val buttonOrange = MaterialTheme.colorScheme.secondary
    val borderColor  = MaterialTheme.colorScheme.outline

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("အကြိမ် : $currentBatch", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
            }
        }

        // --- PASTE DIALOG ---
        if (showPasteDialog) {
            val lineCount = pasteText.lines().count { it.isNotBlank() }
            AlertDialog(
                onDismissRequest = { if (!isParsing) { showPasteDialog = false } },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Quick Bet — Paste", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        if (lineCount > 0)
                            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                                Text("%,d မျဉ်း".format(lineCount),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Format hint
                        Text(
                            "446=1000  |  235-615=3000  |  456R=5000  |  123/456/789=5000",
                            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )

                        // Text input — BasicTextField so it never lags on huge pastes
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            enabled = !isParsing,
                            placeholder = {
                                Text("446=1000\n235-615=3000\n723-372-245-309=2000\n456R=5000\n123/456/789=10000\n...",
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
                            }
                        )

                        // Progress / status
                        if (isParsing) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { parseProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(parseStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                        } else if (parseStatus.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = Color(0xFF43AA8B))
                                Text(parseStatus, fontSize = 12.sp, color = Color(0xFF43AA8B), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            }
                        }

                        if (lineCount > 500)
                            Text(
                                "⚡ ${"%,d".format(lineCount)} မျဉ်း — ထိုးသူ ရွေးထားလျှင် တိုက်ရိုက် DB သိမ်းမည်",
                                fontSize = 11.sp, color = Color(0xFFFF9800),
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                            )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            addBetsFromPasteAsync(pasteText)
                            showPasteDialog = false
                            pasteText = ""
                        },
                        enabled = pasteText.isNotBlank() && !isParsing,
                        colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                    ) { Text(if (isParsing) "ပြင်ဆင်နေသည်..." else "ထည့်မည်") }
                },
                dismissButton = {
                    TextButton(onClick = { if (!isParsing) { showPasteDialog = false; pasteText = "" } }) { Text("မလုပ်တော့") }
                }
            )
        }


        // --- CUSTOMER ROW ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expandedCustomer = true }
                .padding(horizontal = 16.dp, vertical = 4.dp), 
            contentAlignment = Alignment.Center
        ) {
            Row {
                Text("ထိုးသူ : ", color = Color.Black, fontSize = 16.sp)
                Text(customers.find { it.id == selectedCustomer }?.name ?: "ကော်မရှင် ရွေးပါ", color = Color.Black, fontSize = 16.sp)
                Text(" , ဘောင်ချာများ ကြည့်ရန် နှိပ်ပါ", color = Color.Red, fontSize = 16.sp, modifier = Modifier.clickable {
                    selectedCustomer?.let { onNavigateToCustomerVouchers(it) }
                })
            }
            DropdownMenu(expanded = expandedCustomer, onDismissRequest = { expandedCustomer = false }) {
                customers.forEach { customer ->
                    DropdownMenuItem(
                        text = { Text(customer.name) },
                        onClick = {
                            selectedCustomer = customer.id
                            expandedCustomer = false
                        }
                    )
                }
            }
        }

        // --- BET LIST ---
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .border(1.dp, primaryBlue)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().background(primaryBlue).padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("စဉ်",   color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, fontSize = 13.sp)
                Text("|",   color = MaterialTheme.colorScheme.onPrimary)
                Text("ဂဏန်း", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f),   textAlign = TextAlign.Center, fontSize = 13.sp)
                Text("|",   color = MaterialTheme.colorScheme.onPrimary)
                Text("ပမာဏ",  color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f),   textAlign = TextAlign.End,    fontSize = 13.sp)
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pendingBets.size) { i ->
                    val bet = pendingBets[i]
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)
                            .clickable { pendingBets.removeAt(i) },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${i + 1}", modifier = Modifier.weight(0.6f), textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text(bet.number,  modifier = Modifier.weight(1f),   textAlign = TextAlign.Center, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("%,d".format(bet.amount), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 13.sp,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                }
            }
        }

        // --- SUMMARY BAR ---
        val totalAmount = pendingBets.sumOf { it.amount }
        var isSwitchChecked by remember { mutableStateOf(false) }
        Row(
            modifier = Modifier.fillMaxWidth().background(primaryBlue).padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = { pasteText = ""; showPasteDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f), contentColor = MaterialTheme.colorScheme.onPrimary),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) { Text("Quick Bet", fontSize = 12.sp) }
            Text("= %,d Ks".format(totalAmount), color = MaterialTheme.colorScheme.onPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ထိုးရန်", color = MaterialTheme.colorScheme.onPrimary, fontSize = 13.sp, modifier = Modifier.padding(end = 4.dp))
                Switch(
                    checked = isSwitchChecked,
                    onCheckedChange = { if (it) { submitVoucher(); isSwitchChecked = false } },
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.onPrimary, checkedTrackColor = MaterialTheme.colorScheme.secondary, uncheckedThumbColor = MaterialTheme.colorScheme.onPrimary, uncheckedTrackColor = MaterialTheme.colorScheme.outline)
                )
            }
        }

        // --- INPUT ROW: Number | BetType | Amount ---
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.weight(1f).height(44.dp).clickable { focusedField = FocusField.NUMBER }
                    .border(2.dp, if (focusedField == FocusField.NUMBER) buttonTeal else borderColor)
                    .background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                Text(if (tempNumber.isEmpty()) "ဂဏန်း" else tempNumber,
                    color = if (tempNumber.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Box(modifier = Modifier.weight(1f).height(44.dp).border(2.dp, buttonTeal).background(Color.White), contentAlignment = Alignment.Center) {
                Text(currentBetType, color = Color.Gray, fontSize = 18.sp)
            }
            Box(modifier = Modifier.weight(1f).height(44.dp).clickable { focusedField = FocusField.AMOUNT }
                    .border(2.dp, if (focusedField == FocusField.AMOUNT) buttonTeal else borderColor)
                    .background(Color.White), contentAlignment = Alignment.Center) {
                Text(tempAmount, color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            }
        }

        // --- မှတ်ချက် (Remark) row ---
        OutlinedTextField(
            value = tempRemark,
            onValueChange = { tempRemark = it },
            label = { Text("မှတ်ချက်", fontSize = 11.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
        )

        // --- QUICK AMOUNTS (fixed 4 buttons) ---
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("100","300","500","1000").forEach { amt ->
                val isSel = tempAmount == amt
                Button(
                    onClick = { tempAmount = amt; focusedField = FocusField.AMOUNT },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isSel) buttonOrange else buttonTeal, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier.weight(1f).height(30.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                ) { Text(amt, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
            }
            // More amounts scrollable
            LazyRow(modifier = Modifier.weight(2f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(listOf("2000","3000","5000","10000")) { amt ->
                    val isSel = tempAmount == amt
                    Button(
                        onClick = { tempAmount = amt; focusedField = FocusField.AMOUNT },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSel) buttonOrange else buttonTeal, contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp), shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                    ) { Text(amt, fontSize = 12.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) }
                }
            }
        }

        // --- SCROLLABLE SHORTCUT CHIPS ---
        val shortcuts = listOf(
            Triple("ဒဲ့",         true,  { currentBetType = "ဒဲ့" }),
            Triple("ထိပ်",        true,  { currentBetType = "ထိပ်" }),
            Triple("လယ်",         true,  { currentBetType = "လယ်" }),
            Triple("ပိတ်",        true,  { currentBetType = "ပိတ်" }),
            Triple("အပါ",         true,  { currentBetType = "အပါ" }),
            Triple("ရှေ့စီးရီး",  false, { handleSpecial("ရှေ့စီးရီး") }),
            Triple("လယ်စီးရီး",   false, { handleSpecial("လယ်စီးရီး") }),
            Triple("နောက်စီးရီး", false, { handleSpecial("နောက်စီးရီး") }),
            Triple("ဘရိတ်",       false, { handleSpecial("ဘရိတ်") }),
            Triple("ထွိုင်",       false, { handleSpecial("ထွိုင်") }),
            Triple("ရှေ့ပူး",      false, { handleSpecial("ရှေ့ပူး") }),
            Triple("နောက်ပူး",     false, { handleSpecial("နောက်ပူး") }),
            Triple("အခွ",          false, { handleSpecial("အခွ") })
        )
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(shortcuts.size) { i ->
                val (label, isBetTypeChip, action) = shortcuts[i]
                val isSelected = isBetTypeChip && currentBetType == label
                Surface(
                    onClick = { action() },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    color = if (isSelected) primaryBlue else buttonGreen,
                    modifier = Modifier.height(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Text(label, fontSize = 12.sp, color = Color.White, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        // --- 4x4 NUMPAD ---
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KeypadButton("1",  buttonTeal,   Modifier.weight(1f)) { appendText("1") }
                KeypadButton("2",  buttonTeal,   Modifier.weight(1f)) { appendText("2") }
                KeypadButton("3",  buttonTeal,   Modifier.weight(1f)) { appendText("3") }
                KeypadButton("R",  buttonGreen,  Modifier.weight(1f)) { handleSpecial("R") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KeypadButton("4",  buttonTeal,   Modifier.weight(1f)) { appendText("4") }
                KeypadButton("5",  buttonTeal,   Modifier.weight(1f)) { appendText("5") }
                KeypadButton("6",  buttonTeal,   Modifier.weight(1f)) { appendText("6") }
                KeypadButton("/",  buttonGreen,  Modifier.weight(1f)) { handleSpecial("R") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KeypadButton("7",  buttonTeal,   Modifier.weight(1f)) { appendText("7") }
                KeypadButton("8",  buttonTeal,   Modifier.weight(1f)) { appendText("8") }
                KeypadButton("9",  buttonTeal,   Modifier.weight(1f)) { appendText("9") }
                KeypadButton("ဖျက်", buttonGreen, Modifier.weight(1f)) { backspace() }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KeypadButton("ရှင်း", buttonOrange, Modifier.weight(1f)) { clearAll() }
                KeypadButton("0",   buttonTeal,  Modifier.weight(1f)) { appendText("0") }
                KeypadButton("00",  buttonTeal,  Modifier.weight(1f)) { appendText("00") }
                KeypadButton("OK",  buttonOrange, Modifier.weight(1f)) { submit() }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
fun KeypadButton(text: String, bgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.6f),
        colors = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = Color.White),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, fontSize = 15.sp, textAlign = TextAlign.Center)
    }
}
