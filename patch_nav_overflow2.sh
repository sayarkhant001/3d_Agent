#!/bin/bash
sed -i '/OverflowScreen(/i \
        composable<OverflowRoute> {\
' app/src/main/java/com/example/ui/AppNavigation.kt
