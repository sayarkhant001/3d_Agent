#!/bin/bash
sed -i 's/val netAmount = totalAmount - commissionCut/val netAmount = totalAmount - commissionCut - customer.paidAmount/g' app/src/main/java/com/example/ui/CustomersScreen.kt

sed -i 's/Icon(Icons.Default.Add, contentDescription = "Add Bet", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))/Icon(Icons.Default.Add, contentDescription = "Add Bet", tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(28.dp))\n                                }\n                                IconButton(onClick = { editingCustomer = customer }) {\n                                    Icon(androidx.compose.material.icons.filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))/g' app/src/main/java/com/example/ui/CustomersScreen.kt

sed -i '1i import androidx.compose.material.icons.filled.Edit' app/src/main/java/com/example/ui/CustomersScreen.kt

