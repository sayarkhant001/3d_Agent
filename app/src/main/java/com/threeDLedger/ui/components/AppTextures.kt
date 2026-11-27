package com.threeDLedger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight, GPU-friendly Canvas & Material Texture Modifiers for 3D Ledger.
 * Zero bitmap overhead: renders subtle radial ambient glow, warm accounting-ledger
 * dot micro-texture, and satin beveled rim lighting across light & dark modes.
 */

/**
 * Subtle warm paper / velvet obsidian ambient canvas background with a faint
 * accounting-ledger dot grid texture so large screens feel crafted and calm.
 */
fun Modifier.ledgerCanvasTexture(
    isDarkOverride: Boolean? = null
): Modifier = composed {
    val isDark = isDarkOverride ?: isSystemInDarkTheme()
    val emeraldGlow = if (isDark) Color(0xFF10B981).copy(alpha = 0.08f) else Color(0xFF047857).copy(alpha = 0.045f)
    val goldGlow = if (isDark) Color(0xFFF59E0B).copy(alpha = 0.06f) else Color(0xFFD97706).copy(alpha = 0.045f)
    val dotColor = if (isDark) Color(0xFF94A3B8).copy(alpha = 0.065f) else Color(0xFF8C7A5B).copy(alpha = 0.07f)

    this.drawBehind {
        // 1. Soft top-left emerald radiance
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(emeraldGlow, Color.Transparent),
                center = Offset(size.width * 0.12f, size.height * 0.06f),
                radius = (size.maxDimension * 0.55f).coerceAtLeast(1f)
            ),
            radius = (size.maxDimension * 0.55f).coerceAtLeast(1f),
            center = Offset(size.width * 0.12f, size.height * 0.06f)
        )
        // 2. Soft bottom-right burnished gold warmth
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(goldGlow, Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.86f),
                radius = (size.maxDimension * 0.52f).coerceAtLeast(1f)
            ),
            radius = (size.maxDimension * 0.52f).coerceAtLeast(1f),
            center = Offset(size.width * 0.88f, size.height * 0.86f)
        )

        // 3. Delicate wide-spaced ledger dot matrix (uncluttered, zero noise)
        val stepPx = 22.dp.toPx()
        if (stepPx >= 8f && size.width > 0f && size.height > 0f) {
            var y = stepPx * 0.6f
            while (y < size.height) {
                var x = stepPx * 0.6f
                while (x < size.width) {
                    drawCircle(
                        color = dotColor,
                        radius = 1.dp.toPx(),
                        center = Offset(x, y)
                    )
                    x += stepPx
                }
                y += stepPx
            }
        }
    }
}

/**
 * Embossed satin card texture: subtle warm diagonal card sheen + bright top
 * rim bevel + grounded shadow edge for superior visual separation.
 */
fun Modifier.tactileCardSurface(
    shape: Shape,
    elevation: Dp = 3.dp,
    accentBorder: Color? = null,
    isDarkOverride: Boolean? = null
): Modifier = composed {
    val isDark = isDarkOverride ?: isSystemInDarkTheme()
    val faceColors = if (isDark) {
        listOf(Color(0xFF1F2D42), Color(0xFF172233))
    } else {
        listOf(Color(0xFFFFFFFF), Color(0xFFFAF5EA))
    }
    val topRim = accentBorder?.copy(alpha = 0.55f)
        ?: if (isDark) Color.White.copy(alpha = 0.14f) else Color(0xFFFFFFFF)
    val bottomRim = accentBorder?.copy(alpha = 0.35f)
        ?: if (isDark) Color(0xFF334155) else Color(0xFFD5CBB4)

    this
        .shadow(elevation = elevation, shape = shape, clip = false)
        .clip(shape)
        .background(Brush.verticalGradient(faceColors))
        .border(
            BorderStroke(
                width = 1.1.dp,
                brush = Brush.verticalGradient(listOf(topRim, bottomRim))
            ),
            shape = shape
        )
}

/**
 * Brushed metallic emerald & gold bevel surface for Hero actions & summary strips.
 */
fun Modifier.metallicHeroSurface(
    shape: Shape,
    gradientColors: List<Color>,
    borderTopColor: Color = Color(0xFFFDE68A).copy(alpha = 0.55f),
    borderBottomColor: Color = Color(0xFF022C22).copy(alpha = 0.65f),
    elevation: Dp = 6.dp
): Modifier = this
    .shadow(elevation = elevation, shape = shape, clip = false)
    .clip(shape)
    .background(Brush.linearGradient(colors = gradientColors))
    .drawBehind {
        // Subtle silk-metallic diagonal highlight band across upper edge
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = size.height * 0.48f
            )
        )
    }
    .border(
        BorderStroke(
            width = 1.25.dp,
            brush = Brush.verticalGradient(listOf(borderTopColor, borderBottomColor))
        ),
        shape = shape
    )
