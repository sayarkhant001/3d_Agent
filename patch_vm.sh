#!/bin/bash
sed -i 's/fun addVoucherAndBets(customerId: Int, time: String, rawInput: String)/fun addVoucherAndBets(customerId: Int, time: String, rawInput: String, remark: String = "")/g' app/src/main/java/com/example/ui/MainViewModel.kt

sed -i 's/val voucher = Voucher(customerId = customerId, batchNumber = currentBatch.value, date = date, time = time, totalAmount = totalAmount)/val voucher = Voucher(customerId = customerId, batchNumber = currentBatch.value, date = date, time = time, totalAmount = totalAmount, remark = remark)/g' app/src/main/java/com/example/ui/MainViewModel.kt

sed -i 's/fun addVoucherWithBetList(customerId: Int, time: String, bets: List<Bet>)/fun addVoucherWithBetList(customerId: Int, time: String, bets: List<Bet>, remark: String = "")/g' app/src/main/java/com/example/ui/MainViewModel.kt
