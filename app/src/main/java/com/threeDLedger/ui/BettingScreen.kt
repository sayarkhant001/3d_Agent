package com.threeDLedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
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

// Myanmar digit to English digit converter, supporting digit zero (၀), consonant Wa (ဝ), and Burmese round terms
fun String.myanmarToEnglish(): String {
    val myanmarDigits = "၀၁၂၃၄၅၆၇၈၉"
    val englishDigits = "0123456789"
    var res = this.map { c ->
        if (c == 'ဝ' || c == '၀') '0'
        else {
            val idx = myanmarDigits.indexOf(c)
            if (idx >= 0) englishDigits[idx] else c
        }
    }.joinToString("")

    // Normalize Burmese round terms (ပတ်လည် / ပတ်) to 'R'
    res = res.replace(Regex("""(?:\s*\(?(?:ပတ်လည်|ပတ်)\)?\s*)"""), "R")
    return res
}

private val SEPARATOR_SPACES_REGEX = Regex("""\s*([=:\-.,_+၊။])\s*""")
private val ROUND_MARKERS_REGEX    = Regex("""\s*[Rr/]\s*""")
private val TAIL_AMOUNT_REGEX       = Regex("""(?:[=:\s]|(?<=\d)[Rr/]|-(?!\d{3}$))\s*(\d+)(?:\s*[Rr/]\s*(\d+))?$""")
private val NUM_PATTERN_REGEX       = Regex("""(?<!\d)(\d{3})(R?)(?!\d)""")
private val CURRENCY_SUFFIX_REGEX   = Regex("""(?i)\s*(?:ks|ကျပ်|ဖိုး)\s*$""")

// Error data class when a line has invalid numbers or format
data class BetLineParseError(
    val lineNumber: Int,
    val rawLine: String,
    val reason: String,
    val invalidTokens: List<String> = emptyList()
)

sealed class LineParseResult {
    data class Success(val bets: List<Pair<String, Int>>) : LineParseResult()
    data class Error(val error: BetLineParseError) : LineParseResult()
    object Ignored : LineParseResult()
}

data class PasteValidationResult(
    val isValid: Boolean,
    val validBets: List<Pair<String, Int>>,
    val errors: List<BetLineParseError>
)

// Check if a line is voucher metadata/header/footer/timestamp to ignore
fun isVoucherMetadataLine(raw: String): Boolean {
    val trimmed = raw.trim().myanmarToEnglish()
    if (trimmed.isBlank()) return true
    
    // Pure separators: ---, ===, ***, ___, ၊, ။
    if (trimmed.all { it == '-' || it == '=' || it == '*' || it == '_' || it == '—' || it == ' ' || it == '၊' || it == '။' }) return true
    
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

// Parses numbers string with a validated explicit amount, strictly enforcing 3-digit bet rules.
fun parseNumbersWithExplicitAmount(
    numbersStr: String,
    amount: Int,
    rAmount: Int?,
    lineNumber: Int,
    raw: String
): LineParseResult {
    val chunks = numbersStr.split(Regex("""[\s\-.,_+၊။]+""")).filter { it.isNotBlank() }
    if (chunks.isEmpty()) {
        return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ထိုးဂဏန်း မပါရှိပါ"))
    }

    val lessThan3Digits = mutableListOf<String>()
    val moreThan3Digits = mutableListOf<String>()
    val invalidFormat = mutableListOf<String>()
    val parsedBets = mutableListOf<Pair<String, Int>>()

    for (chunk in chunks) {
        val subTokens = if (chunk.contains('R')) {
            if (chunk.endsWith("R") && chunk.count { it == 'R' } == 1) {
                listOf(chunk)
            } else {
                chunk.split('R').filter { it.isNotBlank() }.map { "${it}R" }
            }
        } else {
            listOf(chunk)
        }

        for (token in subTokens) {
            val hasR = token.endsWith("R")
            val baseNum = if (hasR) token.dropLast(1) else token

            if (!baseNum.all { it.isDigit() } || baseNum.isEmpty()) {
                invalidFormat.add(token)
                continue
            }

            if (baseNum.length < 3) {
                lessThan3Digits.add(baseNum)
                continue
            }

            if (baseNum.length > 3) {
                moreThan3Digits.add(baseNum)
                continue
            }

            parsedBets.add(baseNum to amount)

            when {
                rAmount != null && rAmount > 0 -> {
                    NumberGenerator.permutations(baseNum).forEach { perm ->
                        if (perm != baseNum) parsedBets.add(perm to rAmount)
                    }
                }
                hasR -> {
                    NumberGenerator.permutations(baseNum).forEach { perm ->
                        if (perm != baseNum) parsedBets.add(perm to amount)
                    }
                }
            }
        }
    }

    if (lessThan3Digits.isNotEmpty()) {
        return LineParseResult.Error(
            BetLineParseError(
                lineNumber = lineNumber,
                rawLine = raw,
                reason = "ဂဏန်း ၃ လုံး မပြည့်ပါ",
                invalidTokens = lessThan3Digits
            )
        )
    }

    if (moreThan3Digits.isNotEmpty()) {
        return LineParseResult.Error(
            BetLineParseError(
                lineNumber = lineNumber,
                rawLine = raw,
                reason = "ဂဏန်း ၃ လုံးထက် ပိုနေပါသည်",
                invalidTokens = moreThan3Digits
            )
        )
    }

    if (invalidFormat.isNotEmpty()) {
        return LineParseResult.Error(
            BetLineParseError(
                lineNumber = lineNumber,
                rawLine = raw,
                reason = "ပုံစံမမှန်ပါ",
                invalidTokens = invalidFormat
            )
        )
    }

    if (parsedBets.isEmpty()) {
        return LineParseResult.Error(
            BetLineParseError(
                lineNumber = lineNumber,
                rawLine = raw,
                reason = "ထိုးဂဏန်း မပါရှိပါ သို့မဟုတ် ပုံစံမမှန်ပါ"
            )
        )
    }

    // De-duplicate: same number appearing twice -> sum amounts
    val merged = linkedMapOf<String, Int>()
    parsedBets.forEach { (num, amt) -> merged[num] = (merged[num] ?: 0) + amt }
    return LineParseResult.Success(merged.entries.map { it.key to it.value })
}

// Parses and validates a single line. Detects less-than-3-digit numbers, more-than-3-digit numbers, and wrong formats.
// Strictly prevents amounts and numbers from being confused or mistakenly assumed.
fun validateAndParseLine(raw: String, lineNumber: Int): LineParseResult {
    val trimmed = raw.trim()
    if (trimmed.isBlank() || isVoucherMetadataLine(trimmed)) {
        return LineParseResult.Ignored
    }

    // Convert Myanmar digits -> English
    val converted = trimmed.myanmarToEnglish()
    val hasCurrencySuffix = CURRENCY_SUFFIX_REGEX.containsMatchIn(converted)
    var line = converted.replace(CURRENCY_SUFFIX_REGEX, "").trim()
    if (line.isBlank()) return LineParseResult.Ignored

    // 1. Strip optional leading serial prefix (e.g. "စဉ်", "No.", "#")
    line = line.replace(Regex("""^(?:စဉ်|No\.?|no\.?|#)\s*\d*\s*[\.:\)\-၊။]?\s*""", RegexOption.IGNORE_CASE), "").trim()

    // 2. Strip leading list numerals e.g. "1.", "2.", "10.", "1)", "(1)", "[1]", "1:", "1။", or "1 - "
    line = line.replace(Regex("""^\s*(?:\(\d{1,3}\)|\[\d{1,3}\]|\d{1,3}\))\s*"""), "").trim()
    line = line.replace(Regex("""^\s*\d{1,2}\s*[\.:၊။\-]\s+(?=\d)"""), "").trim()
    if (line.isBlank()) return LineParseResult.Ignored

    // 3. Prefix amount pattern e.g. "1000ဖိုး 123 456" or "1000 ks : 123 456" or "500ကျပ် = 123"
    val prefixMatch = Regex("""^(\d+)\s*(?:ဖိုး|ks|ကျပ်)\s*[:=\-]?\s*(.+)$""", RegexOption.IGNORE_CASE).matchEntire(line)
    if (prefixMatch != null) {
        val amt = prefixMatch.groupValues[1].toIntOrNull()
        val numPart = prefixMatch.groupValues[2].trim()
        if (amt == null || amt <= 0) {
            return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ထိုးကြေး ၀ သို့မဟုတ် ပုံစံမမှန်ပါ"))
        }
        return parseNumbersWithExplicitAmount(numPart, amt, null, lineNumber, raw)
    }

    // 4. Check for explicit '=' or ':' delimiter
    if (line.contains('=') || line.contains(':')) {
        // Direct single bet format check e.g. "108 = 50", "108=50", "108:50"
        val directMatch = Regex("""^([^\s=:.,_+\-]+)\s*[=:]\s*(\d+)$""").matchEntire(line)
        if (directMatch != null) {
            val numToken = directMatch.groupValues[1]
            val amt = directMatch.groupValues[2].toIntOrNull()
            if (amt == null || amt <= 0) {
                return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ထိုးကြေး ၀ သို့မဟုတ် ပုံစံမမှန်ပါ"))
            }
            val cleanNum = numToken.removeSuffix("R").removeSuffix("r").removeSuffix("/").trim()
            if (!cleanNum.all { it.isDigit() }) {
                return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ပုံစံမမှန်ပါ", listOf(numToken)))
            }
            if (cleanNum.length < 3) {
                return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ဂဏန်း ၃ လုံး မပြည့်ပါ", listOf(cleanNum)))
            }
            if (cleanNum.length > 3) {
                return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ဂဏန်း ၃ လုံးထက် ပိုနေပါသည်", listOf(cleanNum)))
            }
            val isRound = numToken.endsWith("R", ignoreCase = true) || numToken.endsWith("/")
            val results = mutableListOf<Pair<String, Int>>()
            results.add(cleanNum to amt)
            if (isRound) {
                NumberGenerator.permutations(cleanNum).forEach { perm ->
                    if (perm != cleanNum) results.add(perm to amt)
                }
            }
            return LineParseResult.Success(results)
        }

        val delimIdx = if (line.contains('=')) line.lastIndexOf('=') else line.lastIndexOf(':')
        val partLeft = line.substring(0, delimIdx).trim()
        val partRight = line.substring(delimIdx + 1).trim()

        if (partLeft.isBlank()) {
            return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ထိုးဂဏန်း မပါရှိပါ"))
        }
        if (partRight.isBlank()) {
            return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ထိုးကြေး မပါရှိပါ သို့မဟုတ် ပုံစံမမှန်ပါ"))
        }

        // Check if Left is Amount and Right is Numbers: e.g. "1000 = 123 456"
        val leftClean = partLeft.replace(Regex("""\s*(?:ဖိုး|ks|ကျပ်)\s*$""", RegexOption.IGNORE_CASE), "").trim()
        val leftAsAmount = leftClean.toIntOrNull()
        val isLeftExplicitAmount = (leftAsAmount != null && leftAsAmount >= 1000 && !partLeft.contains(Regex("""[\s\-.,_+]"""))) ||
                partLeft.endsWith("ဖိုး") || partLeft.endsWith("ks", ignoreCase = true) || partLeft.endsWith("ကျပ်")

        val rightClean = partRight.replace(Regex("""\s*(?:ks|ကျပ်)\s*$""", RegexOption.IGNORE_CASE), "").trim()
        val rightAmountMatch = Regex("""^(\d+)(?:\s*[Rr/]\s*(\d+))?$""").matchEntire(rightClean)

        if (isLeftExplicitAmount && rightAmountMatch == null && leftAsAmount != null && leftAsAmount > 0) {
            return parseNumbersWithExplicitAmount(partRight, leftAsAmount, null, lineNumber, raw)
        }

        if (rightAmountMatch != null) {
            val amt = rightAmountMatch.groupValues[1].toIntOrNull()
            if (amt == null || amt <= 0) {
                return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ထိုးကြေး ၀ သို့မဟုတ် ပုံစံမမှန်ပါ"))
            }
            val rAmt = rightAmountMatch.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
            return parseNumbersWithExplicitAmount(partLeft, amt, rAmt, lineNumber, raw)
        } else {
            return LineParseResult.Error(BetLineParseError(lineNumber, raw, "ထိုးကြေး မပါရှိပါ သို့မဟုတ် ပုံစံမမှန်ပါ"))
        }
    }

    // 5. Lines without '=' or ':'.
    // Step 1: collapse spaces around plain separators (NOT / -- handled below)
    var text = line.replace(SEPARATOR_SPACES_REGEX, "$1")
    // Step 2: normalise R, r, AND / -> "R"
    text = text.replace(ROUND_MARKERS_REGEX, "R")

    val tailAmountRegex = Regex("""(?:[\s\-]|(?<=\d)R)\s*(\d+)(?:\s*R\s*(\d+))?$""")
    val tailMatch = tailAmountRegex.find(text)

    if (tailMatch != null) {
        val candidateAmtStr = tailMatch.groupValues[1]
        val candidateAmt = candidateAmtStr.toIntOrNull()
        val candidateRAmt = tailMatch.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull()
        val prefixText = text.substring(0, tailMatch.range.first).trim()

        // PROTECTION RULE:
        // A 3-digit candidate amount without currency suffix ('Ks', 'ကျပ်') is AMBIGUOUS with a 3D bet number (e.g. "123 456 789").
        // Therefore:
        // If candidate amount is 3 digits (or fewer), it is ONLY accepted as an amount IF:
        // 1) hadCurrencySuffix is true (e.g. "123 500 Ks")
        // 2) OR it has a round amount (e.g. "123 500r100")
        // 3) OR it is 1-2 digits (e.g. 50 Ks)
        // Otherwise, it could be a betting number (e.g. 789 in "123 456 789"), so we reject as missing amount!
        val isConfirmedAmount = hasCurrencySuffix || 
                                (candidateAmtStr.length >= 4) || 
                                (candidateRAmt != null) ||
                                (candidateAmt != null && candidateAmt < 100)

        if (isConfirmedAmount && candidateAmt != null && candidateAmt > 0 && prefixText.isNotBlank()) {
            return parseNumbersWithExplicitAmount(prefixText, candidateAmt, candidateRAmt, lineNumber, raw)
        }
    }

    // If we reached here, there is NO valid explicit amount!
    return LineParseResult.Error(
        BetLineParseError(
            lineNumber = lineNumber,
            rawLine = raw,
            reason = "ထိုးကြေး မပါရှိပါ သို့မဟုတ် ပုံစံမမှန်ပါ (ထိုးကြေးကို = ဖြင့် ထည့်ပေးပါ)"
        )
    )
}

// Validates the entire pasted text block. If ANY line has errors, declines the WHOLE paste.
fun validatePastedText(text: String): PasteValidationResult {
    val lines = text.lines()
    val allBets = mutableListOf<Pair<String, Int>>()
    val errors = mutableListOf<BetLineParseError>()

    lines.forEachIndexed { index, rawLine ->
        val lineNum = index + 1
        when (val res = validateAndParseLine(rawLine, lineNum)) {
            is LineParseResult.Success -> {
                allBets.addAll(res.bets)
            }
            is LineParseResult.Error -> {
                errors.add(res.error)
            }
            is LineParseResult.Ignored -> {
                // Ignore blank lines and metadata lines
            }
        }
    }

    return if (errors.isNotEmpty()) {
        // DECLINE THE WHOLE PASTE!
        PasteValidationResult(isValid = false, validBets = emptyList(), errors = errors)
    } else {
        PasteValidationResult(isValid = true, validBets = allBets, errors = emptyList())
    }
}

// Parse one line of pasted bet text into a list of (number, amount) pairs (returns emptyList on error).
fun parsePastedLine(raw: String): List<Pair<String, Int>> {
    return when (val res = validateAndParseLine(raw, 0)) {
        is LineParseResult.Success -> res.bets
        else -> emptyList()
    }
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
    var showPasteDialog by remember { mutableStateOf(false) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var isParsing       by remember { mutableStateOf(false) }
    val rDimens = rememberResponsiveDimens()

    BackHandler {
        when {
            showClearConfirmDialog -> showClearConfirmDialog = false
            showPasteDialog -> {
                if (!isParsing) {
                    showPasteDialog = false
                }
            }
            expandedCustomer -> {
                expandedCustomer = false
            }
            else -> {
                onNavigateBack()
            }
        }
    }
    
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

    var pasteText       by remember { mutableStateOf("") }
    var parseProgress   by remember { mutableStateOf(0f) }
    var parseStatus     by remember { mutableStateOf("") }
    var pasteErrors     by remember { mutableStateOf<List<BetLineParseError>>(emptyList()) }

    val pendingBets = remember { mutableStateListOf<Bet>() }

    var focusedField by remember { mutableStateOf(FocusField.NUMBER) }
    var tempNumber   by remember { mutableStateOf("") }
    var tempAmount   by remember { mutableStateOf("1000") }
    var tempRemark   by remember { mutableStateOf("") }
    var showManualKeypad by remember { mutableStateOf(true) }

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
    
    // Async paste processing — validates all bets and adds them off the main thread.
    // Declines the whole paste if any numbers are less than 3 digits or in wrong format.
    // For <= 500 resulting bets: adds to pendingBets (shows in list).
    // For > 500: submits directly as a voucher so the list never lags.
    fun addBetsFromPasteAsync(text: String) {
        val validation = validatePastedText(text)
        if (!validation.isValid) {
            pasteErrors = validation.errors
            return
        }

        if (selectedCustomer == null && text.lines().size > 500) {
            android.widget.Toast.makeText(context, "ထိုးသူ ဦးစွာရွေးချယ်ပေးပါ", android.widget.Toast.LENGTH_SHORT).show()
        }
        isParsing = true
        parseProgress = 0f
        parseStatus = "ပြင်ဆင်နေသည်..."
        coroutineScope.launch {
            val bannedList = viewModel.bannedNumbers.value.map { it.number }.toHashSet()
            val total = validation.validBets.size.coerceAtLeast(1)
            val allBets = ArrayList<Bet>(total)
            var bannedCount = 0

            withContext(Dispatchers.Default) {
                validation.validBets.forEachIndexed { i, (num, amt) ->
                    if (amt > 0) {
                        if (num in bannedList) bannedCount++
                        else allBets.add(Bet(voucherId = 0, number = num, amount = amt))
                    }
                    if (i % 1000 == 0 || i == total - 1) {
                        withContext(Dispatchers.Main) {
                            parseProgress = (i + 1).toFloat() / total
                            parseStatus = "${i + 1} ဂဏန်း ထည့်သွင်းပြီး..."
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
            "ရှင်းပါ" -> {
                if (pendingBets.isNotEmpty()) {
                    showClearConfirmDialog = true
                } else {
                    clearAll()
                }
            }
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
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("အကြိမ် : $currentBatch", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
        }

        // --- PASTE DIALOG ---
        if (showPasteDialog) {
            val lineCount = pasteText.lines().count { it.isNotBlank() }
            AlertDialog(
                onDismissRequest = { if (!isParsing) { showPasteDialog = false } },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("⚡ အမြန်ထိုး စာရင်းထည့်သွင်းခြင်း", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            if (lineCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        "%,d မျဉ်း".format(lineCount),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = {
                                if (!isParsing) {
                                    showPasteDialog = false
                                    pasteText = ""
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                text = {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "စာကြောင်းအလိုက် အမြန်ထိုး / တင်ကွက် ဘောင်ချာ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (pasteText.isNotBlank()) {
                                TextButton(
                                    onClick = { pasteText = "" },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("စာသား ပြန်ရှင်းမည်", fontSize = 11.sp, color = Color(0xFFEF4444))
                                }
                            }
                        }

                        // Text input — placeholder vanishes on paste/type
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            modifier = Modifier.fillMaxWidth().height(210.dp),
                            enabled = !isParsing,
                            placeholder = {
                                Text(
                                    "အောက်ပါ 'စာသား ကူးထည့်မည် (PASTE)' ခလုတ်ကို နှိပ်ပါ သို့မဟုတ် စာရင်း ရိုက်ထည့်ပါ...",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        )

                        // Live Validation Feedback for Quick Bet
                        if (pasteText.isNotBlank() && !isParsing) {
                            val liveValidation = remember(pasteText) { validatePastedText(pasteText) }
                            if (liveValidation.isValid && liveValidation.validBets.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFECFDF5),
                                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "✅ ${liveValidation.validBets.size} ကွက် စစ်ဆေးပြီး",
                                            color = Color(0xFF047857),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            "= %,d Ks".format(liveValidation.validBets.sumOf { it.second }),
                                            color = Color(0xFF065F46),
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            } else if (!liveValidation.isValid) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF2F2),
                                    border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "⚠️ အမှား ${liveValidation.errors.size} ခု တွေ့ရှိပါသည် (ထိုးကြေးကို = ဖြင့် သေချာ ထည့်ပေးပါ)",
                                        color = Color(0xFFB91C1C),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Progress / status
                        if (isParsing) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                LinearProgressIndicator(
                                    progress = { parseProgress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(parseStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        } else if (parseStatus.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = Color(0xFF43AA8B))
                                Text(parseStatus, fontSize = 12.sp, color = Color(0xFF43AA8B), fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (lineCount > 500)
                            Text(
                                "⚡ ${"%,d".format(lineCount)} မျဉ်း — ထိုးသူ ရွေးထားလျှင် ပေါက်သီး DB သိမ်းမည်",
                                fontSize = 11.sp, color = Color(0xFFFF9800),
                                fontWeight = FontWeight.SemiBold
                            )
                    }
                },
                confirmButton = {
                    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                    if (pasteText.isBlank()) {
                        // Prominent Paste button positioned in place of Cancel & ထည့်မည်
                        Button(
                            onClick = {
                                val clip = clipboardManager.getText()?.text
                                if (!clip.isNullOrBlank()) {
                                    pasteText = clip
                                } else {
                                    android.widget.Toast.makeText(context, "Clipboard တွင် ကူးယူထားသော စာသား မတွေ့ပါ", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                        ) {
                            Icon(
                                Icons.Default.ContentPaste,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "📋 စာသား ကူးထည့်မည် (PASTE)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // After pasting numbers: Confirm & Add Bets button appears
                        Button(
                            onClick = {
                                val validation = validatePastedText(pasteText)
                                if (!validation.isValid) {
                                    pasteErrors = validation.errors
                                } else {
                                    pasteErrors = emptyList()
                                    addBetsFromPasteAsync(pasteText)
                                    showPasteDialog = false
                                    pasteText = ""
                                }
                            },
                            enabled = !isParsing,
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isParsing) "ပြင်ဆင်နေသည်..." else "ထည့်မည်",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                dismissButton = {
                    if (pasteText.isNotBlank()) {
                        // After pasting numbers: Cancel button appears alongside ထည့်မည်
                        OutlinedButton(
                            onClick = {
                                if (!isParsing) {
                                    showPasteDialog = false
                                    pasteText = ""
                                    pasteErrors = emptyList()
                                }
                            },
                            modifier = Modifier.height(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("မလုပ်တော့")
                        }
                    }
                }
            )
        }

        // --- PASTE ERROR DECLINE ALERT DIALOG ---
        if (pasteErrors.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { pasteErrors = emptyList() },
                icon = {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFFEE2E2),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⚠️", fontSize = 26.sp)
                        }
                    }
                },
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "စာရင်း ပယ်ချပါသည် (Declined)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFFDC2626),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "အမှား ${pasteErrors.size} ခု တွေ့ရှိပါသည်",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF991B1B)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "၃ လုံး မပြည့်သော ဂဏန်းများ သို့မဟုတ် ပုံစံမမှန်သော စာကြောင်းများ ပါဝင်နေသဖြင့် စာရင်းတစ်ခုလုံးကို ထည့်သွင်းခြင်း မပြုဘဲ ပယ်ချလိုက်ပါသည်။ အောက်ပါ အမှားများကို ပြင်ဆင်ပြီးမှ ပြန်လည် ထည့်သွင်းပေးပါ -",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEF2F2),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 280.dp)
                        ) {
                            LazyColumn(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(pasteErrors) { err ->
                                    Card(
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White),
                                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "မျဉ်း ${err.lineNumber}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFFDC2626)
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFFFEE2E2)
                                                ) {
                                                    Text(
                                                        err.reason,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFFB91C1C),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                err.rawLine,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF1E293B),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (err.invalidTokens.isNotEmpty()) {
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    "မှားယွင်းနေသော ဂဏန်း: ${err.invalidTokens.joinToString(", ")}",
                                                    fontSize = 11.5.sp,
                                                    color = Color(0xFFDC2626),
                                                    fontWeight = FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { pasteErrors = emptyList() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✏️ ပြန်လည် ပြင်ဆင်မည်", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
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
                .weight(if (showManualKeypad) 2f else 1f)
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
                if (pendingBets.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier.size(24.dp).padding(start = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "အားလုံး ရှင်းမည်",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.width(28.dp))  // room for delete icon column
                }
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

        // --- CONFIRM CLEAR ALL BETS DIALOG ---
        if (showClearConfirmDialog) {
            val totalAmount = pendingBets.sumOf { it.amount }
            AlertDialog(
                onDismissRequest = { showClearConfirmDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(36.dp)
                    )
                },
                title = {
                    Text(
                        "ထိုးထားသော စာရင်းများ အားလုံး ရှင်းလင်းမည်လား?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ဖျက်မည့် စာရင်း :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${pendingBets.size} ကွက်", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("စုစုပေါင်း ပမာဏ :", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("%,d Ks".format(totalAmount), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error, fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                                }
                            }
                        }
                        Text(
                            "စာရင်းသွင်းထားသော ထိုးကြေးဂဏန်းများ အားလုံး ပျက်သွားပါမည်။ အမှန်တကယ် ရှင်းလင်းမည်ဆိုပါက 'အားလုံး ရှင်းမည်' ကို နှိပ်ပါ။",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            pendingBets.clear()
                            clearAll()
                            showClearConfirmDialog = false
                            android.widget.Toast.makeText(context, "စာရင်းများ အားလုံး ရှင်းလင်းပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                    ) {
                        Text("အားလုံး ရှင်းမည်", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showClearConfirmDialog = false },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
                    ) {
                        Text("မရှင်းပါ (ဖျက်သိမ်း)")
                    }
                }
            )
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
                .padding(horizontal = 8.dp, vertical = 2.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (rDimens.isCompact) 6.dp else 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Summary Badge (Scales cleanly for all number places)
                Surface(
                    modifier = Modifier.weight(1f, fill = false),
                    shape = RoundedCornerShape(9.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = if (rDimens.isCompact) 7.dp else 9.dp,
                            vertical = if (rDimens.isCompact) 2.dp else 3.dp
                        ),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "${pendingBets.size} ကွက်",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = if (rDimens.isCompact) 10.5.sp else 11.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                        val amountFontSize = when {
                            totalAmount >= 100_000_000 -> if (rDimens.isCompact) 10.sp else 11.sp
                            totalAmount >= 10_000_000  -> if (rDimens.isCompact) 10.5.sp else 11.5.sp
                            totalAmount >= 1_000_000   -> if (rDimens.isCompact) 11.5.sp else 12.5.sp
                            else                       -> if (rDimens.isCompact) 12.5.sp else 13.5.sp
                        }
                        Text(
                            "= %,d Ks".format(totalAmount),
                            fontWeight = FontWeight.Black,
                            fontSize = amountFontSize,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(Modifier.width(5.dp))

                // Right: Quick Bet (အမြန်ထိုး), Keypad Toggle (⌨️), & ထိုးမည်
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            pasteText = ""
                            showPasteDialog = true
                        },
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(
                            horizontal = if (rDimens.isCompact) 7.dp else 9.dp,
                            vertical = 2.dp
                        ),
                        modifier = Modifier.height(if (rDimens.isCompact) 34.dp else 36.dp)
                    ) {
                        Icon(Icons.Default.ElectricBolt, contentDescription = "Quick Bet", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(
                            "အမြန်ထိုး",
                            fontSize = if (rDimens.isCompact) 11.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    // Keypad Toggle: easily collapse keypad to inspect bets, or open for quick manual edits
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showManualKeypad = !showManualKeypad
                        },
                        shape = RoundedCornerShape(9.dp),
                        contentPadding = PaddingValues(
                            horizontal = if (rDimens.isCompact) 6.dp else 8.dp,
                            vertical = 2.dp
                        ),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (showManualKeypad) primaryBlue.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (showManualKeypad) primaryBlue else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.height(if (rDimens.isCompact) 34.dp else 36.dp)
                    ) {
                        Text(
                            if (showManualKeypad) "⌨️ ဝှက်" else "⌨️ ကီးပက်",
                            fontSize = if (rDimens.isCompact) 10.5.sp else 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
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
                        shape = RoundedCornerShape(9.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = if (pendingBets.isNotEmpty()) 2.dp else 0.dp),
                        contentPadding = PaddingValues(
                            horizontal = if (rDimens.isCompact) 9.dp else 12.dp,
                            vertical = 2.dp
                        ),
                        modifier = Modifier.height(if (rDimens.isCompact) 34.dp else 36.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(
                            "ထိုးမည်",
                            fontSize = if (rDimens.isCompact) 12.5.sp else 13.5.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }

        // ── RED-LINED MANUAL BETTING SECTION (Exactly 1/3 of the screen when shown) ──
        if (showManualKeypad) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // --- INPUT ROW: Number | BetType | Amount ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isNumFocused = focusedField == FocusField.NUMBER
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isNumFocused) EmeraldLight.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface)
                            .border(
                                width = if (isNumFocused) 2.dp else 1.dp,
                                color = if (isNumFocused) KeypadFocusRing else borderColor,
                                shape = RoundedCornerShape(8.dp)
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
                            fontSize = if (tempNumber.isEmpty()) 11.5.sp else 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = if (tempNumber.isEmpty()) 0.sp else 1.5.sp,
                            maxLines = 1
                        )
                    }

                    // Interactive Bet Type Toggle Box
                    Box(
                        modifier = Modifier
                            .weight(0.9f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f))
                            .border(
                                width = 1.2.dp,
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(8.dp)
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Cycle Bet Type",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Amount Input Box
                    val isAmtFocused = focusedField == FocusField.AMOUNT
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAmtFocused) GoldContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surface)
                            .border(
                                width = if (isAmtFocused) 2.dp else 1.dp,
                                color = if (isAmtFocused) GoldAccent else borderColor,
                                shape = RoundedCornerShape(8.dp)
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
                                fontSize = if (tempAmount.length > 5) 13.sp else 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                "Ks",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // --- COMBINED COMPACT PILLS: Quick Amounts & Shortcuts ---
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Amounts
                    items(listOf("100", "300", "500", "1000", "2000", "3000", "5000", "10000")) { amt ->
                        val isSel = tempAmount == amt
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                tempAmount = amt
                                focusedField = FocusField.AMOUNT
                            },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSel) GoldAccent else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSel) GoldDark else borderColor.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text(
                                    amt,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Black else FontWeight.SemiBold,
                                    color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Divider dot
                    item {
                        Text("•", color = borderColor, fontSize = 12.sp)
                    }

                    // Shortcuts
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
                    items(shortcuts.size) { i ->
                        val (label, isBetTypeChip, action) = shortcuts[i]
                        val isSelected = isBetTypeChip && currentBetType == label
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                action()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) primaryBlue else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, if (isSelected) primaryBlue else borderColor.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 9.dp)) {
                                Text(
                                    label,
                                    fontSize = 11.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // --- 4x4 TACTILE KEYPAD (Proportionally weighted inside 1/3 section) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.5.dp)
                ) {
                    // Row 1: 1, 2, 3, R (ပတ်လည်)
                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
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
                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
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
                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
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
                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
                        TactileKeypadButton(
                            text = "ရှင်း",
                            subtitle = "Clear",
                            bgColor = KeypadClearAmber,
                            bevelColor = Color(0xFF92400E),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (pendingBets.isNotEmpty()) {
                                showClearConfirmDialog = true
                            } else {
                                clearAll()
                            }
                        }
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
            }
        } else {
            Spacer(modifier = Modifier.height(4.dp).navigationBarsPadding())
        }
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

    val offsetY = if (isPressed) 1.5.dp else 0.dp
    val elevation = if (isPressed) 1.dp else 2.dp

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 32.dp)
            .fillMaxHeight()
            .padding(horizontal = 1.dp, vertical = 0.5.dp)
            .offset(y = offsetY)
            .shadow(elevation, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
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
                        Color.White.copy(alpha = if (isPressed) 0.1f else 0.45f),
                        Color.Black.copy(alpha = 0.16f)
                    )
                ),
                shape = RoundedCornerShape(8.dp)
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
                    modifier = Modifier.size(17.dp)
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.85f)
                    )
                }
            } else {
                val rDimens = rememberResponsiveDimens()
                Text(
                    text = text,
                    fontSize = if (text.length > 2) (if (rDimens.isCompact) 11.5.sp else 13.sp) else (if (rDimens.isCompact) 15.sp else 18.sp),
                    fontWeight = FontWeight.Black,
                    color = contentColor,
                    fontFamily = if (text.all { it.isDigit() }) FontFamily.Monospace else FontFamily.Default,
                    letterSpacing = if (text.all { it.isDigit() }) 1.sp else 0.sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = if (rDimens.isCompact) 7.5.sp else 8.5.sp,
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
