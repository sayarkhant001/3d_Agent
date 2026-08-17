with open('app/src/main/java/com/example/ui/BettingScreen.kt', 'r') as f:
    text = f.read()

text = text.replace('color = MaterialTheme.colorScheme.surface', 'color = Color.White')
text = text.replace('MaterialTheme.colorScheme.onSurface', 'Color.Black')

with open('app/src/main/java/com/example/ui/BettingScreen.kt', 'w') as f:
    f.write(text)
