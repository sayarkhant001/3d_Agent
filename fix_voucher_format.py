with open('app/src/main/java/com/example/ui/VouchersScreen.kt', 'r') as f:
    text = f.read()

import re

old_block = r'''                            val textToCopy = buildString \{.*?appendLine\("\$\{voucherWithBets\.voucher\.batchNumber\}\.\$footerText"\)\n                            \}'''

new_block = '''                            val remarkStr = if (voucherWithBets.voucher.remark.isNotEmpty()) " (${voucherWithBets.voucher.remark})" else ""
                            val textToCopy = buildString {
                                appendLine("========================")
                                appendLine("      3D VOUCHER")
                                appendLine("========================")
                                appendLine(" အကြိမ် : ${voucherWithBets.voucher.batchNumber}")
                                appendLine(" ရက်စွဲ : $dateString")
                                appendLine(" ထိုးသူ : $customerName$remarkStr")
                                appendLine(" ဘောင်ချာအမှတ် : ${voucherWithBets.voucher.id}")
                                appendLine("------------------------")
                                voucherWithBets.bets.forEach { bet ->
                                    appendLine(" ${bet.number.padEnd(5)} = ${bet.amount}")
                                }
                                appendLine("------------------------")
                                appendLine(" စုစုပေါင်း : ${voucherWithBets.voucher.totalAmount} Ks")
                                appendLine("------------------------")
                                appendLine(" $footerText")
                                appendLine("========================")
                                appendLine("      Thank You!      ")
                                appendLine("========================")
                            }'''

text = re.sub(old_block, new_block, text, flags=re.DOTALL)

with open('app/src/main/java/com/example/ui/VouchersScreen.kt', 'w') as f:
    f.write(text)
