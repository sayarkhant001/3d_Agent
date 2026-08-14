package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BettingScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    var selectedCustomer by remember { mutableStateOf<Int?>(null) }
    var expanded by remember { mutableStateOf(false) }
    
    var showDialog by remember { mutableStateOf(false) }
    
    val pendingBets = remember { mutableStateListOf<Bet>() }
    
    // Amount field
    var amountText by remember { mutableStateOf("100") }
    // Temporary digits entered for shortcuts
    var tempDigits by remember { mutableStateOf("") }
    
    fun addBets(numbers: List<String>) {
        val amount = amountText.toIntOrNull() ?: 0
        if (amount <= 0) return
        for (num in numbers) {
            pendingBets.add(0, Bet(voucherId = 0, number = num, amount = amount))
        }
        tempDigits = "" // reset temp input
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("တင်ကွက်များ", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.padding(16.dp)
            ) {
                OutlinedTextField(
                    value = customers.find { it.id == selectedCustomer }?.name ?: "ကော်မရှင် ရွေးပါ",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("ထိုးသူ (Me)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
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
            
            // List of pending bets
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("စဉ်", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("ဂဏန်း", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Text("ပမာဏ", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
            Divider()
            LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                items(pendingBets.size) { index ->
                    val bet = pendingBets[index]
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${pendingBets.size - index}", modifier = Modifier.weight(1f))
                        Text(bet.number, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("${bet.amount}", modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }
                    Divider()
                }
            }

            // Amount setter bar
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2196F3)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ပမာဏ: ", color = Color.White)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier.width(100.dp).background(Color.White),
                    singleLine = true
                )
                Spacer(modifier = Modifier.weight(1f))
                Text("စုစုပေါင်း: ${pendingBets.sumOf { it.amount }} Ks", color = Color.White, fontWeight = FontWeight.Bold)
            }
            
            // Custom Keypad
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF00897B))) {
                // Number tracking display
                Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp)) {
                    Text(text = if(tempDigits.isEmpty()) "ဂဏန်းရိုက်ထည့်ပါ..." else tempDigits, fontSize = 20.sp, color = Color.Black)
                }

                // Row 0 (Extra): ထိပ်, လယ်, ပိတ်, အပါ
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeypadBtn("ထိပ်", modifier = Modifier.weight(1f)) {
                        val d = tempDigits.toIntOrNull()
                        if (d != null && tempDigits.length == 1) addBets(NumberGenerator.head(d))
                    }
                    KeypadBtn("လယ်", modifier = Modifier.weight(1f)) {
                        val d = tempDigits.toIntOrNull()
                        if (d != null && tempDigits.length == 1) addBets(NumberGenerator.middle(d))
                    }
                    KeypadBtn("ပိတ်", modifier = Modifier.weight(1f)) {
                        val d = tempDigits.toIntOrNull()
                        if (d != null && tempDigits.length == 1) addBets(NumberGenerator.tail(d))
                    }
                    KeypadBtn("အပါ", modifier = Modifier.weight(1f)) {
                        val d = tempDigits.toIntOrNull()
                        if (d != null && tempDigits.length == 1) addBets(NumberGenerator.include(d))
                    }
                }

                // Row 1
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeypadBtn("ရှေ့စီးရီး", modifier = Modifier.weight(1.2f)) {
                        if (tempDigits.length == 2) {
                            val d1 = tempDigits[0].digitToInt()
                            val d2 = tempDigits[1].digitToInt()
                            addBets(NumberGenerator.frontSeries(d1, d2))
                        }
                    }
                    KeypadBtn("လယ်စီးရီး", modifier = Modifier.weight(1.2f)) {
                        if (tempDigits.length == 2) {
                            val d1 = tempDigits[0].digitToInt()
                            val d2 = tempDigits[1].digitToInt()
                            addBets(NumberGenerator.middleSeries(d1, d2))
                        }
                    }
                    KeypadBtn("နောက်စီးရီး", modifier = Modifier.weight(1.2f)) {
                        if (tempDigits.length == 2) {
                            val d1 = tempDigits[0].digitToInt()
                            val d2 = tempDigits[1].digitToInt()
                            addBets(NumberGenerator.backSeries(d1, d2))
                        }
                    }
                    KeypadBtn("ဘရိတ်", modifier = Modifier.weight(1f)) {
                        val d = tempDigits.toIntOrNull()
                        if (d != null && tempDigits.length == 1) addBets(NumberGenerator.breakNum(d))
                    }
                    KeypadBtn("ထွိုင်", modifier = Modifier.weight(1f)) {
                        addBets(NumberGenerator.tri())
                    }
                }
                
                // Row 2
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeypadBtn("ရှေ့ပူး", modifier = Modifier.weight(1f)) { addBets(NumberGenerator.frontDouble()) }
                    KeypadBtn("1", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "1" }
                    KeypadBtn("2", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "2" }
                    KeypadBtn("3", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "3" }
                    KeypadBtn("R", modifier = Modifier.weight(1f)) {
                        if (tempDigits.length == 3) {
                            addBets(NumberGenerator.permutations(tempDigits))
                        }
                    }
                }
                // Row 3
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeypadBtn("နောက်ပူး", modifier = Modifier.weight(1f)) { addBets(NumberGenerator.backDouble()) }
                    KeypadBtn("4", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "4" }
                    KeypadBtn("5", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "5" }
                    KeypadBtn("6", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "6" }
                    KeypadBtn("/", modifier = Modifier.weight(1f)) { 
                        if (tempDigits.isNotEmpty()) tempDigits = tempDigits.dropLast(1)
                    }
                }
                // Row 4
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeypadBtn("အခွ", modifier = Modifier.weight(1f)) { addBets(NumberGenerator.cycle()) }
                    KeypadBtn("7", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "7" }
                    KeypadBtn("8", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "8" }
                    KeypadBtn("9", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "9" }
                    KeypadBtn("ဖျက်", modifier = Modifier.weight(1f)) { pendingBets.clear() }
                }
                // Row 5
                Row(modifier = Modifier.fillMaxWidth()) {
                    KeypadBtn("ရှင်းပါ", modifier = Modifier.weight(1f)) { tempDigits = "" }
                    KeypadBtn("0", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "0" }
                    KeypadBtn("00", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "00" }
                    KeypadBtn("000", modifier = Modifier.weight(1f), isNum = true) { tempDigits += "000" }
                    KeypadBtn("OK", modifier = Modifier.weight(1f), isOk = true) { 
                        if (tempDigits.length == 3) {
                            addBets(listOf(tempDigits))
                        } else if (pendingBets.isNotEmpty()) {
                            showDialog = true
                        }
                    }
                }
            }
        }
        
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("သတိပေးချက်") },
                text = { Text("ထိုးမည်ဆိုတာ သေချာပါသလား?") },
                confirmButton = {
                    TextButton(onClick = {
                        if (selectedCustomer != null && pendingBets.isNotEmpty()) {
                            viewModel.addVoucherWithBetList(selectedCustomer!!, "15", pendingBets.toList())
                            pendingBets.clear()
                            tempDigits = ""
                        }
                        showDialog = false
                    }) {
                        Text("ထိုးမည် (Confirm)", color = Color.White, modifier = Modifier.background(Color(0xFFFF9800)).padding(8.dp))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) {
                        Text("မလုပ်တော့ပါ (Cancel)", color = Color.White, modifier = Modifier.background(Color(0xFFF44336)).padding(8.dp))
                    }
                }
            )
        }
    }
}

@Composable
fun KeypadBtn(text: String, modifier: Modifier = Modifier, isNum: Boolean = false, isOk: Boolean = false, onClick: () -> Unit) {
    val bgColor = if (isOk) Color(0xFFFF9800) else if (isNum) Color(0xFF00695C) else Color(0xFF4CAF50)
    Box(
        modifier = modifier
            .padding(1.dp)
            .background(bgColor)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
