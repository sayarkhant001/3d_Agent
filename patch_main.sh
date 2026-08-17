#!/bin/bash
sed -i '/import com.example.ui.AppNavigation/a \import com.example.ui.NotificationPermissionHandler\nimport com.example.ui.UpdateDialogHandler' app/src/main/java/com/example/MainActivity.kt
sed -i '/AppNavigation(viewModel)/i \                NotificationPermissionHandler()\n                // UpdateDialogHandler(owner = "YOUR_GITHUB_OWNER", repo = "YOUR_GITHUB_REPO")' app/src/main/java/com/example/MainActivity.kt
