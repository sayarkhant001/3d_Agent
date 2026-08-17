package com.threeDLedger.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("အသေးစိတ်", color = MaterialTheme.colorScheme.onPrimary) },
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
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            Icon(
                Icons.Filled.CheckCircle, 
                contentDescription = "Success", 
                tint = Color(0xFF1976D2),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("လုပ်ဆောင်မှု အောင်မြင်ပါသည်", fontSize = 16.sp)
            Text("-155,000.00 (Ks)", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            
            Spacer(modifier = Modifier.height(32.dp))
            
            val dateString = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
            
            ReceiptRow("လုပ်ဆောင်သော အချိန်", dateString)
            ReceiptRow("လုပ်ဆောင်မှုအမှတ်", "01004241061182046686")
            ReceiptRow("လုပ်ဆောင်မှုအမျိုးအစား", "ငွေလွှဲ")
            ReceiptRow("ငွေလွှဲမည် သူ", "U AUNG MYO WIN (******2720)")
            ReceiptRow("ငွေပမာဏ", "-155,000.00 Ks")
            ReceiptRow("မှတ်ချက်", "ငွေပေးချေခြင်း")
            
            Spacer(modifier = Modifier.weight(1f))
            
            // KBZ Pay mockup
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(64.dp).background(Color(0xFF1976D2), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text("KBZ\nPay", color = Color.White, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Scan ဖတ်ပြီး ငွေပေးချေမှုကို အတည်ပြုပါ")
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ReceiptRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.Gray)
        Text(value)
    }
}
