with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'r') as f:
    text = f.read()

text = text.replace('@Composable\n@Composable', '@Composable')

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'w') as f:
    f.write(text)
