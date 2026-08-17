with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'r') as f:
    text = f.read()

text = text.replace('@Composable\n\n@Composable', '@Composable')
text = text.replace('import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.Color', 'import androidx.compose.ui.graphics.Color')

with open('app/src/main/java/com/example/ui/SettingsScreen.kt', 'w') as f:
    f.write(text)
