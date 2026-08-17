with open('app/src/main/java/com/example/ui/CustomersScreen.kt', 'r') as f:
    text = f.read()
text = text.replace('modifier = Modifier.widthIn(max = 600.dp)', 'modifier = Modifier')
with open('app/src/main/java/com/example/ui/CustomersScreen.kt', 'w') as f:
    f.write(text)
