with open('app/src/main/java/com/example/ui/BettingScreen.kt', 'r') as f:
    text = f.read()

replacements = {
    'val blueColor = Color(0xFF2196F3)': 'val blueColor = MaterialTheme.colorScheme.primary',
    'val darkTeal = Color(0xFF00838F)': 'val darkTeal = MaterialTheme.colorScheme.tertiary',
    'val greenColor = Color(0xFF388E3C)': 'val greenColor = MaterialTheme.colorScheme.secondary',
    'val orangeColor = Color(0xFFFF9800)': 'val orangeColor = MaterialTheme.colorScheme.error',
    'Color(0xFF673AB7)': 'MaterialTheme.colorScheme.primaryContainer'
}

for k, v in replacements.items():
    text = text.replace(k, v)

with open('app/src/main/java/com/example/ui/BettingScreen.kt', 'w') as f:
    f.write(text)
