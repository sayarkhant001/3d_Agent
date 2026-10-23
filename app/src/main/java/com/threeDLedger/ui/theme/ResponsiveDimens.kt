package com.threeDLedger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class WindowSizeClass {
    COMPACT,   // < 360 dp (small smartphones or high display zoom)
    STANDARD,  // 360 dp .. 420 dp (standard modern smartphones)
    EXPANDED   // > 420 dp (large devices, foldables, tablets)
}

data class ResponsiveDimens(
    val windowSizeClass: WindowSizeClass,
    val fontScale: Float,
    val paddingScale: Float,
    val isCompact: Boolean,
    val isExpanded: Boolean
) {
    fun responsiveSp(baseSp: Float): TextUnit = (baseSp * fontScale).sp
    fun responsiveDp(baseDp: Float): Dp = (baseDp * paddingScale).dp

    fun responsiveSp(compact: TextUnit, standard: TextUnit, expanded: TextUnit): TextUnit = when (windowSizeClass) {
        WindowSizeClass.COMPACT -> compact
        WindowSizeClass.STANDARD -> standard
        WindowSizeClass.EXPANDED -> expanded
    }

    fun responsiveDp(compact: Dp, standard: Dp, expanded: Dp): Dp = when (windowSizeClass) {
        WindowSizeClass.COMPACT -> compact
        WindowSizeClass.STANDARD -> standard
        WindowSizeClass.EXPANDED -> expanded
    }

    // Senior-accessible touch target minimum: 48dp (WCAG 2.1 AA requirement)
    val minTouchTarget: Dp get() = 48.dp
    // Comfortable, easy-to-press buttons for older users (52–56dp)
    val compactButtonHeight: Dp get() = if (isCompact) 52.dp else 56.dp
    // Rounded, friendly card corners
    val cardCornerRadius: Dp get() = if (isCompact) 16.dp else 20.dp
}

@Composable
fun rememberResponsiveDimens(): ResponsiveDimens {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp

    val sizeClass = when {
        screenWidth < 360 -> WindowSizeClass.COMPACT
        screenWidth <= 420 -> WindowSizeClass.STANDARD
        else -> WindowSizeClass.EXPANDED
    }

    // Never shrink text — small screens adapt via FitText instead.
    // Padding scales gently on compact devices to preserve layout space.
    val (fontScale, paddingScale) = when (sizeClass) {
        WindowSizeClass.COMPACT -> 1.0f to 0.90f
        WindowSizeClass.STANDARD -> 1.0f to 1.0f
        WindowSizeClass.EXPANDED -> 1.05f to 1.1f
    }

    return ResponsiveDimens(
        windowSizeClass = sizeClass,
        fontScale = fontScale,
        paddingScale = paddingScale,
        isCompact = sizeClass == WindowSizeClass.COMPACT,
        isExpanded = sizeClass == WindowSizeClass.EXPANDED
    )
}

