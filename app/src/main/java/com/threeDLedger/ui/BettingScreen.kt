package com.threeDLedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDropDown
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

// Myanmar digit to English digit converter
fun String.myanmarToEnglish(): String {
    val myanmarDigits = "၀၁၂၃၄၅၆၇၈၉"
    val englishDigits = "0123456789"
    return this.map { c ->
        val idx = myanmarDigits.indexOf(c)
        if (idx >= 0) englishDigits[idx] else c
    }.joinToString("")
}

// Normalise a raw pasted line into a list of Bet objects
// Supported formats (after Myanmar→English conversion):
//   582=5000        → single bet
//   747=1000R500    → normal bet + all permutations at R-amount
//   747=1000r500    → same, case-insensitive
//   582-5000  582:5000  582 5000  → alternate separators
fun parsePastedLine(raw: String): List<Pair<String, Int>> {
    val line = raw.trim().myanmarToEnglish()
    if (line.isBlank()) return emptyList()

    // Strip leading non-digit/non-letter noise (emoji, bullets, dashes)
    val cleaned = line.trimStart { !it.isLetterOrDigit() }

    // Regex: (3-digit number)(separator)(amount)(optional R/r amount)
    val regex = Regex("""(\d{3})[=:\-\s]+(\d+)(?:[Rr](\d+))?""", RegexOption.IGNORE_CASE)
    val match = regex.find(cleaned) ?: return emptyList()

    val number = match.groupValues[1].padStart(3, '0')
    val amount = match.groupValues[2].toIntOrNull() ?: return emptyList()
    val rAmount = match.groupValues[3].toIntOrNull()

    val results = mutableListOf<Pair<String, Int>>()
    if (amount > 0) results.add(number to amount)

    // R-suffix: generate all unique permutations of the number at rAmount
    if (rAmount != null && rAmount > 0) {
        val perms = NumberGenerator.permutations(number)
        perms.forEach { perm ->
            if (perm != number) results.add(perm to rAmount)
        }
        // Also include the original at rAmount if R is present (full permutation set)
        results.add(number to rAmount)
        // Remove duplicates, keeping last value for same key
        return results.distinctBy { it.first }
    }

    return results
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

    var showDialog by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    
    val pendingBets = remember { mutableStateListOf<Bet>() }
    
    var focusedField by remember { mutableStateOf(FocusField.NUMBER) }
    var tempNumber by remember { mutableStateOf("") }
    var tempAmount by remember { mutableStateOf("1000") }
    var tempRemark by remember { mutableStateOf("") } // Maybe not needed in new layout but keep logic
    
    // Dropdown states
    var expandedBetType by remember { mutableStateOf(false) }
    var currentBetType by remember { mutableStateOf("ဒဲ့") }
    val betTypes = listOf("ဒဲ့", "ထိပ်", "လယ်", "ပိတ်", "အပါ")

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
    
    fun addBetsFromPaste(text: String) {
        val bannedList = viewModel.bannedNumbers.value.map { it.number }
        var addedCount = 0
        var bannedFound = false
        text.lines().forEach { line ->
            val parsed = parsePastedLine(line)
            parsed.forEach { (num, amt) ->
                if (amt > 0) {
                    if (num in bannedList) {
                        bannedFound = true
                    } else {
                        pendingBets.add(Bet(voucherId = 0, number = num, amount = amt))
                        addedCount++
                    }
                }
            }
        }
        if (bannedFound) android.widget.Toast.makeText(context, "ပိတ်ထားသော ဂဏန်းများ ပါဝင်နေ၍ ဖယ်ထုတ်လိုက်ပါသည်", android.widget.Toast.LENGTH_SHORT).show()
        if (addedCount > 0) android.widget.Toast.makeText(context, "$addedCount ကြောင်း ထည့်သွင်းပြီး", android.widget.Toast.LENGTH_SHORT).show()
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
    
    // Colors based on screenshot
    val primaryBlue = Color(0xFF2196F3)
    val buttonTeal = Color(0xFF00838F)
    val buttonGreen = Color(0xFF2E7D32)
    val buttonOrange = Color(0xFFEF6C00)
    val borderColor = Color.LightGray

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // --- TOP BAR ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("အကြိမ် : $currentBatch", fontSize = 18.sp, color = Color.Black)
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(32.dp))
            }
        }

        // --- PASTE DIALOG ---
        if (showPasteDialog) {
            AlertDialog(
                onDismissRequest = { showPasteDialog = false },
                title = { Text("Quick Bet - Paste Numbers") },
                text = {
                    Column {
                        Text(
                            "ဂဏန်းများ paste လုပ်ပါ\nဥပမာ: 582=5000 သို့မဟုတ် 747=1000R500",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = pasteText,
                            onValueChange = { pasteText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            placeholder = { Text("582=5000\n452=2500\n747=1000R500\n...") },
                            maxLines = 30
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            addBetsFromPaste(pasteText)
                            showPasteDialog = false
                            pasteText = ""
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = buttonTeal)
                    ) { Text("ထည့်မည်") }
                },
                dismissButton = {
                    TextButton(onClick = { showPasteDialog = false }) { Text("မလုပ်တော့") }
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

        // --- LIST HEADER & LIST (Flexible height) ---
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
                .border(1.dp, primaryBlue)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(primaryBlue)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("စဉ်", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("|", color = Color.White)
                Text("ဂဏန်း", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("|", color = Color.White)
                Text("ပမာဏ", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
            
            // List
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(pendingBets.size) { i ->
                    val bet = pendingBets[i]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clickable {
                                // Allow removing by tapping
                                pendingBets.removeAt(i)
                            },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${i + 1}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Black)
                        Text("${bet.number}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Black)
                        Text("${bet.amount}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.Black)
                    }
                    Divider(color = Color.LightGray)
                }
            }
        }

        // --- SUMMARY & ACTION BAR ---
        val totalAmount = pendingBets.sumOf { it.amount }
        var isSwitchChecked by remember { mutableStateOf(false) }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(primaryBlue)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Quick Bet button in summary bar (left side)
            Button(
                onClick = { pasteText = ""; showPasteDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White.copy(alpha = 0.2f),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("အမြန်ထိုးရွေးပါ", color = Color.White, fontSize = 14.sp)
            }

            Text("စုစုပေါင်း = $totalAmount", color = Color.White, fontSize = 16.sp)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ထိုးရန်", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(end = 4.dp))
                Switch(
                    checked = isSwitchChecked,
                    onCheckedChange = { 
                        if (it) {
                            submitVoucher()
                            // Revert switch automatically
                            isSwitchChecked = false
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = buttonGreen,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.LightGray
                    )
                )
            }
        }

        // --- INPUT BOXES ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Number Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clickable { focusedField = FocusField.NUMBER }
                    .border(2.dp, if (focusedField == FocusField.NUMBER) buttonTeal else borderColor)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(if (tempNumber.isEmpty()) "ဂဏန်း" else tempNumber, color = Color.Gray, fontSize = 18.sp)
            }
            // Bet Type Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .border(2.dp, buttonTeal)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(currentBetType, color = Color.Gray, fontSize = 18.sp)
            }
            // Amount Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clickable { focusedField = FocusField.AMOUNT }
                    .border(2.dp, if (focusedField == FocusField.AMOUNT) buttonTeal else borderColor)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text(tempAmount, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        // --- SCROLLABLE BET TYPE SHORTCUTS BAR ---
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(betTypes.size) { i ->
                val type = betTypes[i]
                val isSelected = currentBetType == type
                Button(
                    onClick = { currentBetType = type },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) primaryBlue else Color.LightGray,
                        contentColor = if (isSelected) Color.White else Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(type, fontSize = 14.sp)
                }
            }
        }

        // --- QUICK BET AMOUNT BAR ---
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(quickAmounts.size) { i ->
                val amt = quickAmounts[i]
                val isSelected = tempAmount == amt
                Button(
                    onClick = { 
                        tempAmount = amt
                        focusedField = FocusField.AMOUNT
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) buttonOrange else buttonTeal,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(amt, fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        // --- 5x5 KEYPAD ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Row 1
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KeypadButton("ရှေ့စီးရီး", buttonGreen, Modifier.weight(1f)) { handleSpecial("ရှေ့စီးရီး") }
                KeypadButton("လယ်စီးရီး", buttonGreen, Modifier.weight(1f)) { handleSpecial("လယ်စီးရီး") }
                KeypadButton("နောက်စီးရီး", buttonGreen, Modifier.weight(1f)) { handleSpecial("နောက်စီးရီး") }
                KeypadButton("ဘရိတ်", buttonGreen, Modifier.weight(1f)) { handleSpecial("ဘရိတ်") }
                KeypadButton("ထွိုင်", buttonGreen, Modifier.weight(1f)) { handleSpecial("ထွိုင်") }
            }
            // Row 2
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KeypadButton("ရှေ့ပူး", buttonGreen, Modifier.weight(1f)) { handleSpecial("ရှေ့ပူး") }
                KeypadButton("1", buttonTeal, Modifier.weight(1f)) { appendText("1") }
                KeypadButton("2", buttonTeal, Modifier.weight(1f)) { appendText("2") }
                KeypadButton("3", buttonTeal, Modifier.weight(1f)) { appendText("3") }
                KeypadButton("R", buttonGreen, Modifier.weight(1f)) { handleSpecial("R") }
            }
            // Row 3
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KeypadButton("နောက်ပူး", buttonGreen, Modifier.weight(1f)) { handleSpecial("နောက်ပူး") }
                KeypadButton("4", buttonTeal, Modifier.weight(1f)) { appendText("4") }
                KeypadButton("5", buttonTeal, Modifier.weight(1f)) { appendText("5") }
                KeypadButton("6", buttonTeal, Modifier.weight(1f)) { appendText("6") }
                KeypadButton("/", buttonGreen, Modifier.weight(1f)) { handleSpecial("/") }
            }
            // Row 4
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KeypadButton("အခွ", buttonGreen, Modifier.weight(1f)) { handleSpecial("အခွ") }
                KeypadButton("7", buttonTeal, Modifier.weight(1f)) { appendText("7") }
                KeypadButton("8", buttonTeal, Modifier.weight(1f)) { appendText("8") }
                KeypadButton("9", buttonTeal, Modifier.weight(1f)) { appendText("9") }
                KeypadButton("ဖျက်", buttonGreen, Modifier.weight(1f)) { handleSpecial("ဖျက်") }
            }
            // Row 5
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                KeypadButton("ရှင်းပါ", buttonOrange, Modifier.weight(1f)) { handleSpecial("ရှင်းပါ") }
                KeypadButton("0", buttonTeal, Modifier.weight(1f)) { appendText("0") }
                KeypadButton("00", buttonTeal, Modifier.weight(1f)) { appendText("00") }
                KeypadButton("000", buttonTeal, Modifier.weight(1f)) { appendText("000") }
                KeypadButton("OK", buttonOrange, Modifier.weight(1f)) { submit() }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun KeypadButton(text: String, bgColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.aspectRatio(1.3f), // Approximate square/rectangle
        colors = ButtonDefaults.buttonColors(containerColor = bgColor, contentColor = Color.White),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp), // square corners like screenshot
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(text, fontSize = 16.sp, textAlign = TextAlign.Center)
    }
}
