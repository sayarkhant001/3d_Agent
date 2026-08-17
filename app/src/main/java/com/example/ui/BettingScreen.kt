package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
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
import com.example.data.Bet
import com.example.logic.NumberGenerator

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
    var expanded by remember { mutableStateOf(false) }
    
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.bannedNumberEvent.collect {
            android.widget.Toast.makeText(context, "ထိုးထားသော ဂဏန်းများထဲတွင် ပိတ်ထားသော ဂဏန်းများ ပါဝင်နေသဖြင့် ဖယ်ရှားလိုက်ပါသည်", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    var showDialog by remember { mutableStateOf(false) }
    
    val pendingBets = remember { mutableStateListOf<Bet>() }
    
    var focusedField by remember { mutableStateOf(FocusField.NUMBER) }
    var tempNumber by remember { mutableStateOf("") }
    var tempAmount by remember { mutableStateOf("1000") }
    var tempRemark by remember { mutableStateOf("") }
    
    var showPasteDialog by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    
    fun addBets(numbers: List<String>) {
        val amount = tempAmount.toIntOrNull() ?: 0
        if (amount <= 0) return
        val bannedList = viewModel.bannedNumbers.value.map { it.number }
        var bannedFound = false
        val validNumbers = numbers.filter { 
            if (it in bannedList) { bannedFound = true; false } else true 
        }
        for (num in validNumbers) {
            pendingBets.add(0, Bet(voucherId = 0, number = num, amount = amount))
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
            tempAmount += txt
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
        tempAmount = ""
        focusedField = FocusField.NUMBER
    }
    
    fun submit() {
        if (tempNumber.length == 3) {
            addBets(listOf(tempNumber))
        } else if (pendingBets.isNotEmpty()) {
            showDialog = true
        }
    }

    fun handleSpecial(cmd: String) {
        val num = tempNumber.toIntOrNull()
        val digits = tempNumber
        when (cmd) {
            "ထိပ်" -> if (num != null && digits.length == 1) addBets(NumberGenerator.head(num))
            "လယ်" -> if (num != null && digits.length == 1) addBets(NumberGenerator.middle(num))
            "ပိတ်" -> if (num != null && digits.length == 1) addBets(NumberGenerator.tail(num))
            "အပါ" -> if (num != null && digits.length == 1) addBets(NumberGenerator.include(num))
            "ရှေ့စီးရီး" -> if (digits.length == 2) addBets(NumberGenerator.frontSeries(digits[0].digitToInt(), digits[1].digitToInt()))
            "လယ်စီးရီး" -> if (digits.length == 2) addBets(NumberGenerator.middleSeries(digits[0].digitToInt(), digits[1].digitToInt()))
            "နောက်စီးရီး" -> if (digits.length == 2) addBets(NumberGenerator.backSeries(digits[0].digitToInt(), digits[1].digitToInt()))
            "ဘရိတ်" -> if (num != null && digits.length == 1) addBets(NumberGenerator.breakNum(num))
            "ထွိုင်" -> addBets(NumberGenerator.tri())
            "ရှေ့ပူး" -> addBets(NumberGenerator.frontDouble())
            "နောက်ပူး" -> addBets(NumberGenerator.backDouble())
            "အခွ" -> addBets(NumberGenerator.cycle())
            "R" -> if (digits.length == 3) addBets(NumberGenerator.permutations(digits))
        }
    }
    
    val blueColor = MaterialTheme.colorScheme.primary
    val darkTeal = MaterialTheme.colorScheme.tertiary
    val greenColor = MaterialTheme.colorScheme.secondary
    val orangeColor = MaterialTheme.colorScheme.error

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("အကြိမ် : $currentBatch", fontSize = 18.sp, color = Color.Black)
                Row {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black, modifier = Modifier.size(32.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val bannedNumbers by viewModel.bannedNumbers.collectAsStateWithLifecycle()
            if (bannedNumbers.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = "ပိတ်ထားသော ဂဏန်းများ: ${bannedNumbers.joinToString(", ") { it.number }}",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            OutlinedTextField(
                value = tempRemark,
                onValueChange = { tempRemark = it },
                label = { Text("မှတ်ချက် (အမည်)") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                singleLine = true
            )

            // Customer Selector Row
            Box(modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Row {
                    Text("ထိုးသူ : ", color = Color.Black, fontSize = 16.sp)
                    Text(customers.find { it.id == selectedCustomer }?.name ?: "ကော်မရှင် ရွေးပါ", color = Color.Black, fontSize = 16.sp)
                    Text(" , ဘောင်ချာများ ကြည့်ရန် နှိပ်ပါ", color = Color.Red, fontSize = 16.sp, modifier = Modifier.clickable {
                        selectedCustomer?.let { onNavigateToCustomerVouchers(it) }
                    })
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    customers.forEach { customer ->
                        DropdownMenuItem(
                            text = { Text(customer.name) },
                            onClick = {
                                selectedCustomer = customer.id
                                expanded = false
                            }
                        )
                    }
                }
            }

            Row(modifier = Modifier.fillMaxSize()) {
                // Left Panel: Pending Bets
                Column(modifier = Modifier.weight(1f).fillMaxHeight().border(1.dp, Color.LightGray)) {
                    LazyColumn(modifier = Modifier.weight(1f).padding(8.dp)) {
                        items(pendingBets.size) { i ->
                            val bet = pendingBets[i]
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${i + 1}. ${bet.number}", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("${bet.amount}", fontSize = 18.sp, color = darkTeal)
                                IconButton(onClick = { pendingBets.removeAt(i) }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color.Red)
                                }
                            }
                        }
                    }
                    val total = pendingBets.sumOf { it.amount }
                    Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("စုစုပေါင်း:", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("$total", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = darkTeal)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (selectedCustomer == null) {
                                    expanded = true
                                    return@Button
                                }
                                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                viewModel.addVoucherWithBetList(selectedCustomer!!, time, pendingBets, tempRemark)
                                tempRemark = ""
                                pendingBets.clear()
                                tempNumber = ""
                                tempAmount = "1000"
                                android.widget.Toast.makeText(context, "ဘောင်ချာ သိမ်းဆည်းပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = greenColor),
                            enabled = pendingBets.isNotEmpty() && selectedCustomer != null
                        ) {
                            Text("သိမ်းမည်", fontSize = 18.sp, color = Color.White)
                        }
                    }
                }

                // Right Panel: Numpad & Special
                Column(modifier = Modifier.weight(1.5f).fillMaxHeight().padding(8.dp)) {
                    // Input Displays
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(modifier = Modifier.weight(1f).height(64.dp).clickable { focusedField = FocusField.NUMBER },
                            colors = CardDefaults.cardColors(containerColor = if (focusedField == FocusField.NUMBER) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (focusedField == FocusField.NUMBER) orangeColor else Color.LightGray)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(if (tempNumber.isEmpty()) "ဂဏန်း" else tempNumber, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Card(modifier = Modifier.weight(1f).height(64.dp).clickable { focusedField = FocusField.AMOUNT },
                            colors = CardDefaults.cardColors(containerColor = if (focusedField == FocusField.AMOUNT) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (focusedField == FocusField.AMOUNT) orangeColor else Color.LightGray)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(tempAmount, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = darkTeal)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Quick Amounts
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(500, 1000, 5000, 10000).forEach { amt ->
                            Button(onClick = { tempAmount = amt.toString(); focusedField = FocusField.NUMBER }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = darkTeal), contentPadding = PaddingValues(0.dp)) {
                                Text("$amt", fontSize = 14.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Main Keypad
                    val specialKeys = listOf("ထိပ်", "လယ်", "ပိတ်", "အပါ", "ရှေ့စီးရီး", "လယ်စီးရီး", "နောက်စီးရီး", "ဘရိတ်", "ထွိုင်", "ရှေ့ပူး", "နောက်ပူး", "အခွ", "R")
                    Row(modifier = Modifier.fillMaxWidth()) {
                        // Numpad Grid
                        Column(modifier = Modifier.weight(2f)) {
                            val nums = listOf(
                                listOf("1", "2", "3"),
                                listOf("4", "5", "6"),
                                listOf("7", "8", "9"),
                                listOf("0", "00", "000")
                            )
                            nums.forEach { row ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    row.forEach { n ->
                                        Button(onClick = { appendText(n) }, modifier = Modifier.weight(1f).aspectRatio(1.2f), colors = ButtonDefaults.buttonColors(containerColor = blueColor)) {
                                            Text(n, fontSize = 20.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(onClick = { clearAll() }, modifier = Modifier.weight(1f).aspectRatio(1.2f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                                    Text("C", fontSize = 20.sp)
                                }
                                Button(onClick = { backspace() }, modifier = Modifier.weight(1f).aspectRatio(1.2f), colors = ButtonDefaults.buttonColors(containerColor = orangeColor)) {
                                    Text("<-", fontSize = 20.sp)
                                }
                                Button(onClick = { submit() }, modifier = Modifier.weight(1f).aspectRatio(1.2f), colors = ButtonDefaults.buttonColors(containerColor = greenColor)) {
                                    Text("OK", fontSize = 20.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // Special Keys
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            items(specialKeys.size) { i ->
                                val key = specialKeys[i]
                                Button(onClick = { handleSpecial(key) }, modifier = Modifier.fillMaxWidth().height(40.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer), contentPadding = PaddingValues(0.dp)) {
                                    Text(key, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
