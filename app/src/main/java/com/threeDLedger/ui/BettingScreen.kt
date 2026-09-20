package com.threeDLedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ElectricBolt
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.data.Bet
import com.threeDLedger.logic.NumberGenerator
import com.threeDLedger.ui.theme.*
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

private val SEPARATOR_SPACES_REGEX = Regex("""\s*([=:\-.,_])\s*""")
private val ROUND_MARKERS_REGEX    = Regex("""\s*[Rr/]\s*""")
private val TAIL_AMOUNT_REGEX       = Regex("""[=:\-.,_]?(\d+)(?:R(\d+))?$""")
private val NUM_PATTERN_REGEX       = Regex("""(?<!\d)(\d{2,3})(R?)(?!\d)""")
private val CURRENCY_SUFFIX_REGEX   = Regex("""(?i)\s*(?:ks|ကျပ်)\s*$""")

// Check if a line is voucher metadata/header/footer/timestamp to ignore
fun isVoucherMetadataLine(raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return true
    
    // Pure separators: ---, ===, ***, ___
    if (trimmed.all { it == '-' || it == '=' || it == '*' || it == '_' || it == '—' || it == ' ' }) return true
    
    val lower = trimmed.lowercase()
    if (lower.contains("တင်ကွက်") || 
        lower.contains("ဘောင်ချာ") || 
        lower.contains("အကြိမ်") || 
        lower.contains("အချိန်") || 
        lower.contains("စုစုပေါင်း") || 
        lower.contains("အထက်ဒိုင်") || 
        lower.contains("ရက်စွဲ") ||
        lower.contains("voucher") ||
        lower.contains("batch") ||
        lower.contains("time") ||
        lower.contains("total")) {
        return true
    }
    
    // Protect date and timestamp lines (e.g. 13/09/2026 or 11:57:45)
    if (Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}""").containsMatchIn(trimmed)) return true
    if (Regex("""\d{1,2}:\d{2}(?::\d{2})?""").containsMatchIn(trimmed)) return true
    
    return false
}

// Parse one line of pasted bet text into a list of (number, amount) pairs.
// Handles ALL these real-world formats:
//   108   = 50                     -> 1 number at 50 (from overflow voucher)
//   723-372-245-309 = 2000         -> 4 numbers at 2000
//   446=1000                       -> 1 number at 1000
//   235-615 = 3000                 -> 2 numbers at 3000
//   456=5000r1000                  -> 456 at 5000, permutations at 1000
//   456R=5000  OR  456/=5000       -> 456 + all perms at 5000  (R and / both mean Round)
//   123/5000   OR  123R5000        -> 123 + perms at 5000
//   123/456/789=5000               -> 123+perms, 456+perms, 789+perms at 5000
//   185-217-378-549 = 10000        -> 4 numbers at 10000
fun parsePastedLine(raw: String): List<Pair<String, Int>> {
    if (isVoucherMetadataLine(raw)) return emptyList()

    // Convert Myanmar digits -> English, strip currency suffix, and trim
    var line = raw.trim().myanmarToEnglish().replace(CURRENCY_SUFFIX_REGEX, "").trim()
    if (line.isBlank()) return emptyList()

    // 1. Strip optional leading serial prefix (e.g. "စဉ်", "No.", "#")
    line = line.replace(Regex("""^(?:စဉ်|No\.?|no\.?|#)\s*""", RegexOption.IGNORE_CASE), "").trim()

    // 2. Strip leading list numerals e.g. "1.", "2.", "10.", "1)", "(1)", "[1]", "1:", "1။", or "1 - "
    // Must distinguish between list indices and real 3D/2D betting numbers (e.g. "723-", "245-")!
    line = line.replace(Regex("""^\s*(?:\(?\d{1,3}\)?\s*[\.\)\]\:\၊။]\s*|\d{1}\s*[-,\/]\s+)"""), "").trim()
    if (line.isBlank()) return emptyList()

    // Direct single bet format check e.g. "108 = 50", "108=50", "108-50"
    val directMatch = Regex("""^(\d{2,3})\s*[=:\-]\s*(\d+)$""").matchEntire(line)
    if (directMatch != null) {
        val num = directMatch.groupValues[1]
        val amt = directMatch.groupValues[2].toIntOrNull()
        if (amt != null && amt > 0) {
            return listOf(num to amt)
        }
    }

    // Step 1: collapse spaces around plain separators (NOT / -- handled below)
    var text = line.replace(SEPARATOR_SPACES_REGEX, "$1")

    // Step 2: normalise R, r, AND / -> "R"  (/ is treated as Round, same as R)
    text = text.replace(ROUND_MARKERS_REGEX, "R")

    // Find the AMOUNT at the end: (optional separator)(digits)(optional R digits)$
    val tailMatch = TAIL_AMOUNT_REGEX.find(text) ?: return emptyList()

    val amount  = tailMatch.groupValues[1].toIntOrNull() ?: return emptyList()
    val rAmount = tailMatch.groupValues[2].toIntOrNull()

    // Everything BEFORE the tail match is the numbers section
    val numbersStr = text.substring(0, tailMatch.range.first)
    if (numbersStr.isBlank()) return emptyList()

    // Step 3: extract (number, hasR) pairs using regex.
    // After normalisation "123/456/789=5000" -> "123R456R789=5000"
    // Pattern matches each 2-3 digit number and its optional trailing R
    val results = mutableListOf<Pair<String, Int>>()

    for (match in NUM_PATTERN_REGEX.findAll(numbersStr)) {
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
    val rDimens = rememberResponsiveDimens()
    
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.bannedNumberEvent.collect {
            android.widget.Toast.makeText(context, "ထိုးထားသော ဂဏန်းများထဲတွင် ပိတ်ထားသော ဂဏန်းများ ပါဝင်နေသဖြင့် ဖယ်ရှားလိုက်ပါသည်", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(customers) {
        if (selectedCustomer == null && customers.isNotEmpty()) {
            val defaultCust = customers.firstOrNull { !it.name.contains("တင်ကွက်") && !it.name.contains("overflow", ignoreCase = true) }
            if (defaultCust != null) {
                selectedCustomer = defaultCust.id
            }
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
            android.widget.Toast.makeText(context, "ထိုးသူ ဦးစွာရွေးချယ်ပေးပါ", android.widget.Toast.LENGTH_SHORT).show()
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
                    if (i % 1000 == 0 || i == total - 1) {
                        withContext(Dispatchers.Main) {
                            parseProgress = (i + 1).toFloat() / total
                            parseStatus = "${i + 1} ကြောင်း စစ်ဆေးပြီး..."
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
                    viewModel.addVoucherWithBetList(selectedCustomer!!, time, allBets, tempRemark)
                    tempRemark = ""
                    android.widget.Toast.makeText(context, "${allBets.size} ကြောင်း ထိုးကြေး သိမ်းဆည်းပြီး", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    // No customer selected — still buffer (they can submit later)
                    pendingBets.addAll(allBets)
                }
            }

            if (bannedCount > 0)
                android.widget.Toast.makeText(context, "$bannedCount ကြောင်း ပိတ်ဂဏန်းများ ဖယ်ထုတ်ပြီး", android.widget.Toast.LENGTH_SHORT).show()
            if (addedCount > 0 && addedCount <= 500)
                android.widget.Toast.makeText(context, "$addedCount ကြောင်း ထည့်သွင်းပြီး", android.widget.Toast.LENGTH_SHORT).show()

            isParsing = false
            parseProgress = 1f
            parseStatus = "$addedCount ကြောင်း ထည့်သွင်းပြီး"
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
    
    // ── Dynamic theme colour tokens ──────────────────────────────────────────
    val primaryBlue  = MaterialTheme.colorScheme.primary
    val buttonTeal   = MaterialTheme.colorScheme.primary
    val buttonGreen  = MaterialTheme.colorScheme.secondary
    val buttonOrange = MaterialTheme.colorScheme.tertiary
    val borderColor  = MaterialTheme.colorScheme.outlineVariant

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
                        Text("⚡ အမြန်ထိုး စာရင်းထည့်သွင်းခြင်း", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
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
                        val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "စာကြောင်းအလိုက် အမြန်ထိုး / တင်ကွက် ဘောင်ချာ",
                                fontSize = 11.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            FilledTonalButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text
                                    if (!clip.isNullOrBlank()) {
                                        pasteText = clip
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("📋 Clipboard ကူးယူမည်", fontSize = 11.sp)
                            }
                        }

                        // Text input — placeholder vanishes on paste/type
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            modifier = Modifier.fillMaxWidth().height(240.dp),
                            enabled = !isParsing,
                            placeholder = {
                                Text(
                                    "ဤနေရာတွင် အမြန်ထိုး စာရင်း ကူးထည့်ပါ...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
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
                                "⚡ ${"%,d".format(lineCount)} မျဉ်း — ထိုးသူ ရွေးထားလျှင် ပေါက်သီး DB သိမ်းမည်",
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


        // --- CUSTOMER SELECTOR BAR ---
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .clickable { expandedCustomer = true }
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = primaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "ထိုးသူ : ",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        customers.find { it.id == selectedCustomer }?.name ?: "ကော်မရှင် ရွေးပါ ▾",
                        color = if (selectedCustomer != null) primaryBlue else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (selectedCustomer != null) {
                    Surface(
                        onClick = { onNavigateToCustomerVouchers(selectedCustomer!!) },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "ဘောင်ချာများ ကြည့်ရန်",
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            DropdownMenu(expanded = expandedCustomer, onDismissRequest = { expandedCustomer = false }) {
                customers
                    .filter { !it.name.contains("တင်ကွက်") && !it.name.contains("overflow", ignoreCase = true) && !it.name.contains("upper", ignoreCase = true) }
                    .forEach { customer ->
                        DropdownMenuItem(
                            text = { Text(customer.name, fontWeight = FontWeight.SemiBold) },
                            onClick = {
                                selectedCustomer = customer.id
                                expandedCustomer = false
                            }
                        )
                    }
            }
        }

        // ── BET LIST BOX ─────────────────────────────────────────────────────
        val maxAmtB   = if (pendingBets.isNotEmpty()) pendingBets.maxOf { it.amount } else 0
        val amtWidthB = if (maxAmtB > 0) "%,d".format(maxAmtB).length else 5

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                .border(2.dp, primaryBlue, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        primaryBlue,
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "စဉ်   ဂဏန်း",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "ပမာဏ",
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(90.dp)
                )
                Spacer(Modifier.width(28.dp))  // room for delete icon column
            }

            // ── Rows ──────────────────────────────────────────────────────────
            if (pendingBets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "ဂဏန်းထည့်ရန်",
                            color = primaryBlue.copy(alpha = 0.35f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "ကီးပက်ကို သုံး၍ ထိုးနိုင်သည်",
                            color = primaryBlue.copy(alpha = 0.25f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(pendingBets.size) { i ->
                        val bet = pendingBets[i]
                        val isEven = i % 2 == 0

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (isEven) MaterialTheme.colorScheme.surface
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                                .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Row number
                            Text(
                                "${i + 1}.",
                                fontSize = 11.sp,
                                color = primaryBlue.copy(alpha = 0.45f),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.width(26.dp),
                                textAlign = TextAlign.End
                            )
                            Spacer(Modifier.width(6.dp))

                            // Number — large bold emerald
                            Text(
                                bet.number,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = primaryBlue,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                letterSpacing = 2.sp,
                                modifier = Modifier.width(52.dp)
                            )

                            // Equals sign
                            Text(
                                "=",
                                fontSize = 16.sp,
                                color = Color(0xFF9CA3AF),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )

                            // Amount — right-aligned, large
                            Text(
                                "%,d".format(bet.amount).padStart(amtWidthB),
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111827),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                textAlign = TextAlign.End,
                                modifier = Modifier.weight(1f)
                            )

                            // "Ks" suffix
                            Text(
                                " Ks",
                                fontSize = 11.sp,
                                color = Color(0xFF6B7280),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )

                            // Delete button
                            IconButton(
                                onClick = { pendingBets.removeAt(i) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "ဖျက်",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        if (i < pendingBets.lastIndex)
                            HorizontalDivider(
                                color = primaryBlue.copy(alpha = 0.08f),
                                thickness = 0.5.dp
                            )
                    }
                }
            }
        }

        // --- SUMMARY BAR & BET CONFIRMATION ---
        val totalAmount = pendingBets.sumOf { it.amount }
        var isSwitchChecked by remember { mutableStateOf(false) }
        var showBetConfirmDialog by remember { mutableStateOf(false) }

        fun onAttemptBet() {
            if (selectedCustomer == null) {
                expandedCustomer = true
                android.widget.Toast.makeText(context, "ထိုးသူ ဦးစွာ ရွေးချယ်ပေးပါ", android.widget.Toast.LENGTH_SHORT).show()
                isSwitchChecked = false
                return
            }
            if (pendingBets.isEmpty()) {
                android.widget.Toast.makeText(context, "ထိုးမည့် ဂဏန်းများ မရှိသေးပါ", android.widget.Toast.LENGTH_SHORT).show()
                isSwitchChecked = false
                return
            }
            showBetConfirmDialog = true
        }

        if (showBetConfirmDialog) {
            val customerObj = customers.find { it.id == selectedCustomer }
            val customerName = customerObj?.name ?: "သတ်မှတ်မထားပါ"
            AlertDialog(
                onDismissRequest = {
                    showBetConfirmDialog = false
                    isSwitchChecked = false
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(38.dp)
                    )
                },
                title = {
                    Text(
                        "ထိုးကြေး စာရင်းသွင်းရန် အတည်ပြုပါ",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ထိုးသူ :", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text(customerName, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ဂဏန်း အရေအတွက် :", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                                    Text("${pendingBets.size} ကွက်", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("ကျသင့်ငွေ :", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                                    Text(
                                        "= %,d Ks".format(totalAmount),
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                }
                                if (tempRemark.isNotBlank()) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("မှတ်ချက် :", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                        Text(tempRemark, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        Text(
                            "မတော်တဆ ထိမိခြင်းမှ ကာကွယ်ရန် ထိုးကြေး စာရင်းသွင်းမှုကို အတည်ပြုပေးပါ။ အမှန်တကယ် ထိုးမည်ဆိုပါက 'အတည်ပြု ထိုးမည်' ကို နှိပ်ပါ။",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 17.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBetConfirmDialog = false
                            isSwitchChecked = false
                            submitVoucher()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                    ) {
                        Text("အတည်ပြု ထိုးမည်", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = {
                            showBetConfirmDialog = false
                            isSwitchChecked = false
                        },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                    ) {
                        Text("ဖျက်သိမ်းမည် (မထိုးပါ)")
                    }
                }
            )
        }

        val haptic = LocalHapticFeedback.current

        // ── PRO CASHIER VOUCHER ACTION BAR ──────────────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 3.dp),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 3.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (rDimens.isCompact) 6.dp else 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Summary Badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (rDimens.isCompact) 6.dp else 10.dp,
                            vertical = if (rDimens.isCompact) 4.dp else 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${pendingBets.size} ကွက်",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (rDimens.isCompact) 11.5.sp else 13.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "|",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "= %,d Ks".format(totalAmount),
                            fontWeight = FontWeight.Black,
                            fontSize = if (rDimens.isCompact) 13.sp else 15.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Right: Quick Bet (အမြန်ထိုး) & ထိုးမည်
                Row(
                    horizontalArrangement = Arrangement.spacedBy(if (rDimens.isCompact) 4.dp else 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pasteText = ""
                            showPasteDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(
                            horizontal = if (rDimens.isCompact) 7.dp else 10.dp,
                            vertical = 2.dp
                        ),
                        modifier = Modifier.height(if (rDimens.isCompact) 34.dp else 38.dp)
                    ) {
                        Icon(Icons.Default.ElectricBolt, contentDescription = "Quick Bet", modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("အမြန်ထိုး", fontSize = if (rDimens.isCompact) 11.sp else 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onAttemptBet()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pendingBets.isNotEmpty()) EmeraldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (pendingBets.isNotEmpty()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (pendingBets.isNotEmpty()) 3.dp else 0.dp),
                        contentPadding = PaddingValues(
                            horizontal = if (rDimens.isCompact) 9.dp else 12.dp,
                            vertical = 2.dp
                        ),
                        modifier = Modifier.height(if (rDimens.isCompact) 34.dp else 38.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("ထိုးမည်", fontSize = if (rDimens.isCompact) 13.sp else 14.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // --- INPUT ROW: Number | BetType | Amount ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            val isNumFocused = focusedField == FocusField.NUMBER
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .height(if (rDimens.isCompact) 42.dp else 46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isNumFocused) EmeraldLight.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface)
                .border(
                    width = if (isNumFocused) 2.5.dp else 1.dp,
                    color = if (isNumFocused) KeypadFocusRing else borderColor,
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    focusedField = FocusField.NUMBER
                },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (tempNumber.isEmpty()) "ဂဏန်းရိုက်ပါ" else tempNumber,
                    color = if (tempNumber.isEmpty()) MaterialTheme.colorScheme.outline else EmeraldPrimary,
                    fontSize = if (tempNumber.isEmpty()) (if (rDimens.isCompact) 11.5.sp else 13.sp) else (if (rDimens.isCompact) 19.sp else 22.sp),
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = if (tempNumber.isEmpty()) 0.sp else 2.sp,
                    maxLines = 1
                )
            }

            // Interactive Bet Type Toggle Box
            Box(
                modifier = Modifier
                    .weight(0.9f)
                    .height(if (rDimens.isCompact) 42.dp else 46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val cycleList = listOf("ဒဲ့", "ထွိုင်", "ထိပ်", "လယ်", "ပိတ်", "အပါ")
                        val nextIdx = (cycleList.indexOf(currentBetType) + 1) % cycleList.size
                        currentBetType = cycleList[nextIdx]
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = currentBetType,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = if (rDimens.isCompact) 15.sp else 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(2.dp))
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "Cycle Bet Type",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Amount Input Box
            val isAmtFocused = focusedField == FocusField.AMOUNT
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .height(if (rDimens.isCompact) 42.dp else 46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isAmtFocused) GoldContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface)
                    .border(
                        width = if (isAmtFocused) 2.5.dp else 1.dp,
                        color = if (isAmtFocused) GoldAccent else borderColor,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        focusedField = FocusField.AMOUNT
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tempAmount,
                        color = if (isAmtFocused) GoldDark else MaterialTheme.colorScheme.onSurface,
                        fontSize = if (tempAmount.length > 5) (if (rDimens.isCompact) 14.sp else 16.sp) else (if (rDimens.isCompact) 17.sp else 20.sp),
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "Ks",
                        fontSize = if (rDimens.isCompact) 10.sp else 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // --- မှတ်ချက် (Remark) row ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (tempRemark.isEmpty()) {
                Text(
                    "မှတ်ချက် (မထည့်လည်းရသည်)",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            androidx.compose.foundation.text.BasicTextField(
                value = tempRemark,
                onValueChange = { tempRemark = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        // --- QUICK AMOUNTS (Ergonomic Thumb Pills) ---
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(listOf("100", "300", "500", "1000", "2000", "3000", "5000", "10000")) { amt ->
                val isSel = tempAmount == amt
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        tempAmount = amt
                        focusedField = FocusField.AMOUNT
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSel) GoldAccent else MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) GoldDark else borderColor),
                    shadowElevation = if (isSel) 2.dp else 1.dp,
                    modifier = Modifier.height(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Text(
                            amt,
                            fontSize = 13.sp,
                            fontWeight = if (isSel) FontWeight.Black else FontWeight.SemiBold,
                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // --- SCROLLABLE SHORTCUT CHIPS ---
        val shortcuts = listOf(
            Triple("ဒဲ့",         true,  { currentBetType = "ဒဲ့" }),
            Triple("ထွိုင်",       false, { handleSpecial("ထွိုင်") }),
            Triple("ထိပ်",        true,  { currentBetType = "ထိပ်" }),
            Triple("လယ်",         true,  { currentBetType = "လယ်" }),
            Triple("ပိတ်",        true,  { currentBetType = "ပိတ်" }),
            Triple("အပါ",         true,  { currentBetType = "အပါ" }),
            Triple("ရှေ့စီးရီး",  false, { handleSpecial("ရှေ့စီးရီး") }),
            Triple("လယ်စီးရီး",   false, { handleSpecial("လယ်စီးရီး") }),
            Triple("နောက်စီးရီး", false, { handleSpecial("နောက်စီးရီး") }),
            Triple("ဘရိတ်",       false, { handleSpecial("ဘရိတ်") }),
            Triple("ရှေ့ပူး",      false, { handleSpecial("ရှေ့ပူး") }),
            Triple("နောက်ပူး",     false, { handleSpecial("နောက်ပူး") }),
            Triple("အခွ",          false, { handleSpecial("အခွ") })
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(shortcuts.size) { i ->
                val (label, isBetTypeChip, action) = shortcuts[i]
                val isSelected = isBetTypeChip && currentBetType == label
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        action()
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) primaryBlue else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) primaryBlue else borderColor.copy(alpha = 0.5f)),
                    shadowElevation = if (isSelected) 2.dp else 0.dp,
                    modifier = Modifier.height(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 11.dp)) {
                        Text(
                            label,
                            fontSize = 12.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // --- 4x4 TACTILE KEYPAD ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Row 1: 1, 2, 3, R (ပတ်လည်)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TactileKeypadButton("1", modifier = Modifier.weight(1f)) { appendText("1") }
                TactileKeypadButton("2", modifier = Modifier.weight(1f)) { appendText("2") }
                TactileKeypadButton("3", modifier = Modifier.weight(1f)) { appendText("3") }
                TactileKeypadButton(
                    text = "R",
                    subtitle = "ပတ်လည်",
                    bgColor = KeypadActionEmerald,
                    bevelColor = Color(0xFF065F46),
                    modifier = Modifier.weight(1f)
                ) { handleSpecial("R") }
            }

            // Row 2: 4, 5, 6, ထွိုင် (၃ပူး)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TactileKeypadButton("4", modifier = Modifier.weight(1f)) { appendText("4") }
                TactileKeypadButton("5", modifier = Modifier.weight(1f)) { appendText("5") }
                TactileKeypadButton("6", modifier = Modifier.weight(1f)) { appendText("6") }
                TactileKeypadButton(
                    text = "ထွိုင်",
                    subtitle = "အပူး",
                    bgColor = KeypadActionTeal,
                    bevelColor = Color(0xFF115E59),
                    modifier = Modifier.weight(1f)
                ) { handleSpecial("ထွိုင်") }
            }

            // Row 3: 7, 8, 9, ⌫ (ဖျက်)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TactileKeypadButton("7", modifier = Modifier.weight(1f)) { appendText("7") }
                TactileKeypadButton("8", modifier = Modifier.weight(1f)) { appendText("8") }
                TactileKeypadButton("9", modifier = Modifier.weight(1f)) { appendText("9") }
                TactileKeypadButton(
                    text = "⌫",
                    subtitle = "ဖျက်",
                    icon = Icons.AutoMirrored.Filled.Backspace,
                    bgColor = KeypadBackspaceRed,
                    bevelColor = Color(0xFF991B1B),
                    modifier = Modifier.weight(1f)
                ) { backspace() }
            }

            // Row 4: ရှင်း (Clear), 0, 00, OK (ထည့်)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                TactileKeypadButton(
                    text = "ရှင်း",
                    subtitle = "Clear",
                    bgColor = KeypadClearAmber,
                    bevelColor = Color(0xFF92400E),
                    modifier = Modifier.weight(1f)
                ) { clearAll() }
                TactileKeypadButton("0", modifier = Modifier.weight(1f)) { appendText("0") }
                TactileKeypadButton("00", modifier = Modifier.weight(1f)) { appendText("00") }
                TactileKeypadButton(
                    text = "OK",
                    subtitle = "ထည့်မည်",
                    bgColor = KeypadSubmitBg,
                    bevelColor = Color(0xFF022C22),
                    modifier = Modifier.weight(1f)
                ) { submit() }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ── Ultra-Realistic Tactile 3D Keypad Button ─────────────────────────────────
@Composable
fun TactileKeypadButton(
    text: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    bgColor: Color = Color.White,
    contentColor: Color = if (bgColor == Color.White) KeypadDigitText else Color.White,
    bevelColor: Color = if (bgColor == Color.White) Color(0xFFE2E8F0) else bgColor.copy(alpha = 0.85f),
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val offsetY = if (isPressed) 2.dp else 0.dp
    val elevation = if (isPressed) 1.dp else 3.dp

    Box(
        modifier = modifier
            .heightIn(min = 40.dp, max = 58.dp)
            .aspectRatio(1.5f)
            .padding(horizontal = 1.dp, vertical = 1.dp)
            .offset(y = offsetY)
            .shadow(elevation, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bgColor,
                        bevelColor
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isPressed) 0.1f else 0.5f),
                        Color.Black.copy(alpha = 0.18f)
                    )
                ),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = text,
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                }
            } else {
                val rDimens = rememberResponsiveDimens()
                Text(
                    text = text,
                    fontSize = if (text.length > 2) (if (rDimens.isCompact) 13.sp else 15.sp) else (if (rDimens.isCompact) 18.sp else 21.sp),
                    fontWeight = FontWeight.Black,
                    color = contentColor,
                    fontFamily = if (text.all { it.isDigit() }) FontFamily.Monospace else FontFamily.Default,
                    letterSpacing = if (text.all { it.isDigit() }) 1.sp else 0.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = if (rDimens.isCompact) 8.sp else 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/**
 * Backwards-compatible KeypadButton wrapper delegating to TactileKeypadButton
 */
@Composable
fun KeypadButton(
    text: String,
    bgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    TactileKeypadButton(
        text = text,
        bgColor = bgColor,
        modifier = modifier,
        onClick = onClick
    )
}
