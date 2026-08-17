#!/bin/bash
sed -i 's/var tempAmount by remember { mutableStateOf("1000") }/var tempAmount by remember { mutableStateOf("1000") }\n    var tempRemark by remember { mutableStateOf("") }/g' app/src/main/java/com/example/ui/BettingScreen.kt

sed -i 's/addVoucherWithBetList(selectedCustomer!!, time, pendingBets)/addVoucherWithBetList(selectedCustomer!!, time, pendingBets, tempRemark)\n                    tempRemark = ""/g' app/src/main/java/com/example/ui/BettingScreen.kt

sed -i 's/addVoucherAndBets(selectedCustomer!!, time, pasteText)/addVoucherAndBets(selectedCustomer!!, time, pasteText, tempRemark)\n                                        tempRemark = ""/g' app/src/main/java/com/example/ui/BettingScreen.kt

sed -i '/Row {/i \
                OutlinedTextField(\
                    value = tempRemark,\
                    onValueChange = { tempRemark = it },\
                    label = { Text("မှတ်ချက် (အမည်)") },\
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),\
                    singleLine = true\
                )\
' app/src/main/java/com/example/ui/BettingScreen.kt

