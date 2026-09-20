package com.threeDLedger.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class WindowSizeClass {
    COMPACT,   // < 360 dp (small / budget smartphones or larger display scaling)
    STANDARD,  // 360 dp .. 420 dp (standard smartphones)
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

    val (fontScale, paddingScale) = when (sizeClass) {
        WindowSizeClass.COMPACT -> 0.88f to 0.85f
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
