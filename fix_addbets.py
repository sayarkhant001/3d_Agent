with open('app/src/main/java/com/example/ui/BettingScreen.kt', 'r') as f:
    text = f.read()

import re

old_addbets = r'''    fun addBets\(numbers: List<String>\) \{
        val amount = tempAmount\.toIntOrNull\(\) \?: 0
        if \(amount <= 0\) return
        val bannedList = viewModel\.bannedNumbers\.value\.map \{ it\.number \}
        var bannedFound = false
        val validNumbers = numbers\.filter \{ 
            if \(it in bannedList\) \{ bannedFound = true; false \} else true 
        \}
        for \(num in validNumbers\) \{
            pendingBets\.add\(0, Bet\(voucherId = 0, number = num, amount = amount\)\)
        \}
        if \(bannedFound\) \{
            android\.widget\.Toast\.makeText\(context, "ပိတ်ထားသော ဂဏန်းများ ပါဝင်နေ၍ ဖယ်ထုတ်လိုက်ပါသည်", android\.widget\.Toast\.LENGTH_SHORT\)\.show\(\)
        \}
        tempNumber = "" // reset temp input
        focusedField = FocusField\.NUMBER
        if \(amount <= 0\) return
        for \(num in numbers\) \{
            pendingBets\.add\(0, Bet\(voucherId = 0, number = num, amount = amount\)\)
        \}
        tempNumber = "" // reset temp input
        focusedField = FocusField\.NUMBER
    \}'''

new_addbets = '''    fun addBets(numbers: List<String>) {
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
    }'''

text = re.sub(old_addbets, new_addbets, text)

with open('app/src/main/java/com/example/ui/BettingScreen.kt', 'w') as f:
    f.write(text)
