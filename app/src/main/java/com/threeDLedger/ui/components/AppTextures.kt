package com.threeDLedger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight GPU-Cached Texture Modifiers for 3D Ledger.
 * Optimized for smooth 60/120fps on any Android device:
 * - Uses `drawWithCache` so gradients & brushes allocate ONCE per size change (zero per-frame GC churn).
 * - Avoids loop-heavy canvas dot grids and expensive offscreen shadow blurs inside lazy lists.
 */

/**
 * Ambient emerald & warm gold corner radiance cached in GPU command buffer.
 */
@Composable
fun Modifier.ledgerCanvasTexture(
    isDarkOverride: Boolean? = null
): Modifier {
    val isDark = isDarkOverride ?: isSystemInDarkTheme()
    val emeraldGlow = if (isDark) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFF047857).copy(alpha = 0.045f)
    val goldGlow = if (isDark) Color(0xFFF59E0B).copy(alpha = 0.06f) else Color(0xFFD97706).copy(alpha = 0.045f)

    return this.drawWithCache {
        val radiusTop = (size.maxDimension * 0.55f).coerceAtLeast(1f)
        val centerTop = Offset(size.width * 0.12f, size.height * 0.06f)
        val topBrush = Brush.radialGradient(
            colors = listOf(emeraldGlow, Color.Transparent),
            center = centerTop,
            radius = radiusTop
        )

        val radiusBottom = (size.maxDimension * 0.52f).coerceAtLeast(1f)
        val centerBottom = Offset(size.width * 0.88f, size.height * 0.86f)
        val bottomBrush = Brush.radialGradient(
            colors = listOf(goldGlow, Color.Transparent),
            center = centerBottom,
            radius = radiusBottom
        )

        onDrawBehind {
            drawCircle(brush = topBrush, radius = radiusTop, center = centerTop)
            drawCircle(brush = bottomBrush, radius = radiusBottom, center = centerBottom)
        }
    }
}

/**
 * Embossed satin card surface with cached gradient & zero-blur beveled border.
 * Safe and silky-smooth inside LazyColumn scrolling lists on low-RAM phones.
 */
@Composable
fun Modifier.tactileCardSurface(
    shape: Shape,
    elevation: Dp = 0.dp,
    accentBorder: Color? = null,
    isDarkOverride: Boolean? = null
): Modifier {
    val isDark = isDarkOverride ?: isSystemInDarkTheme()
    val bgBrush = remember(isDark) {
        val faceColors = if (isDark) {
            listOf(Color(0xFF1F2D42), Color(0xFF172233))
        } else {
            listOf(Color(0xFFFFFFFF), Color(0xFFFAF5EA))
        }
        Brush.verticalGradient(faceColors)
    }
    val borderBrush = remember(isDark, accentBorder) {
        val topRim = accentBorder?.copy(alpha = 0.55f)
            ?: if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFFFFFFFF)
        val bottomRim = accentBorder?.copy(alpha = 0.40f)
            ?: if (isDark) Color(0xFF334155) else Color(0xFFD0C5AC)
        Brush.verticalGradient(listOf(topRim, bottomRim))
    }

    return this
        .clip(shape)
        .background(bgBrush)
        .border(width = 1.1.dp, brush = borderBrush, shape = shape)
}

/**
 * Brushed metallic emerald & gold bevel surface for Hero actions & summary strips.
 */
@Composable
fun Modifier.metallicHeroSurface(
    shape: Shape,
    gradientColors: List<Color>,
    borderTopColor: Color = Color(0xFFFDE68A).copy(alpha = 0.55f),
    borderBottomColor: Color = Color(0xFF022C22).copy(alpha = 0.65f),
    elevation: Dp = 0.dp
): Modifier {
    val heroBrush = remember(gradientColors) { Brush.linearGradient(colors = gradientColors) }
    val rimBrush = remember(borderTopColor, borderBottomColor) {
        Brush.verticalGradient(listOf(borderTopColor, borderBottomColor))
    }

    return this
        .clip(shape)
        .background(heroBrush)
        .drawWithCache {
            val sheen = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent),
                startY = 0f,
                endY = size.height * 0.48f
            )
            onDrawBehind {
                drawRect(brush = sheen)
            }
        }
        .border(width = 1.25.dp, brush = rimBrush, shape = shape)
}
