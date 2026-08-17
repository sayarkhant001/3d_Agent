with open('app/src/main/java/com/example/ui/HomeScreen.kt', 'r') as f:
    text = f.read()

import re
# Remove the old Settings Row block
text = re.sub(r'Spacer\(modifier = Modifier.height\(16.dp\)\)\s*Row\(\s*modifier = Modifier.fillMaxWidth\(\),\s*horizontalArrangement = Arrangement.SpaceEvenly\s*\)\s*\{\s*MenuButton\(\s*title = "ဆက်တင်",\s*icon = Icons.Default.Settings,\s*onClick = onNavigateToSettings\s*\)\s*\}', '', text)

# Add a new smaller Settings button at the top right of Scaffold
text = text.replace('TopAppBar(', '''TopAppBar(
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },''')

with open('app/src/main/java/com/example/ui/HomeScreen.kt', 'w') as f:
    f.write(text)
