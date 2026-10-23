package com.threeDLedger.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
/**
 * Reusable app-wide UI animations for a tactile, old-school-friendly feel.
 * ────────────────────────────────────────────────────────────────────────
 */

/**
 * Applies a subtle press-scale effect to any clickable component.
 * The component shrinks to [pressedScale] when pressed and springs back
 * on release, giving a tactile "physical button" sensation.
 *
 * Example:
 *   Modifier.pressScale(interactionSource = interactionSource)
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource? = null,
    pressedScale: Float = 0.95f
): Modifier = composed(
    factory = {
        val source = interactionSource ?: remember { MutableInteractionSource() }
        val isPressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) pressedScale else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            label = "press_scale"
        )
        this.scale(scale)
    }
)

/**
 * Staggered entrance animation for list items or cards.
 * Each element fades in and slides up from [initialOffsetDp] with a
 * short delay based on [index], producing a pleasing cascade effect.
 *
 * Example:
 *   AnimatedEntrance(index = itemPosition) {
 *       MenuCard(...)
 *   }
 */
@Composable
fun AnimatedEntrance(
    index: Int,
    initialOffsetDp: Float = 20f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val animationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val animationState = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceAtLeast(0) * 40L).coerceAtMost(400L))
        animationState.animateTo(1f, animationSpec)
    }
    val offsetY = (1f - animationState.value) * initialOffsetDp * density.density
    Box(modifier = Modifier.graphicsLayer {
        alpha = animationState.value
        translationY = offsetY
    }) {
        content()
    }
}

/**
 * Gentle "pulse" animation (breathing effect) used to draw attention
 * to important UI elements, e.g., the primary "Submit" button.
 *
 * Example:
 *   Modifier.pulseEffect()
 */
fun Modifier.pulseEffect(): Modifier = composed(
    factory = {
        val transition = rememberInfiniteTransition(label = "pulse")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.03f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse_scale"
        )
        this.scale(scale)
    }
)

/**
 * Shimmer effect for loading placeholders (subtle sliding highlight).
 *
 * Example:
 *   Modifier.shimmerEffect()
 */
fun Modifier.shimmerEffect(): Modifier = composed(
    factory = {
        val transition = rememberInfiniteTransition(label = "shimmer")
        val progress by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_progress"
        )
        this.graphicsLayer { alpha = 0.6f + 0.4f * progress }
    }
)
