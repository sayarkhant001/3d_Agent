package com.threeDLedger.ui.components

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import kotlinx.coroutines.delay
/**
 * Reusable app-wide UI animations tuned for zero-lag perceived speed
 * and calm, senior-friendly legibility.
 * ────────────────────────────────────────────────────────────────────────
 */

/** Brisk expand transition (~180ms) for accordions & collapsible cards. */
fun fastExpandTransition(): EnterTransition =
    expandVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
        fadeIn(animationSpec = tween(150, easing = FastOutSlowInEasing))

/** Brisk shrink transition (~160ms) for accordions & collapsible cards. */
fun fastShrinkTransition(): ExitTransition =
    shrinkVertically(animationSpec = tween(160, easing = FastOutSlowInEasing)) +
        fadeOut(animationSpec = tween(120, easing = FastOutSlowInEasing))

/**
 * Applies a crisp press-scale feedback to any clickable component.
 * Shrinks cleanly on touch and snaps back immediately so taps register fast.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource? = null,
    pressedScale: Float = 0.96f
): Modifier = composed(
    factory = {
        val source = interactionSource ?: remember { MutableInteractionSource() }
        val isPressed by source.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) pressedScale else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessHigh
            ),
            label = "press_scale"
        )
        this.scale(scale)
    }
)

/**
 * Snappy staggered entrance animation for list items or cards.
 * Keeps total stagger tight (~96ms max) so long lists never feel sluggish.
 */
@Composable
fun AnimatedEntrance(
    index: Int,
    initialOffsetDp: Float = 12f,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val animationSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    // Animate only the FIRST time the item appears. rememberSaveable keeps the
    // flag alive across LazyColumn item disposal, so scrolling away and back
    // never re-triggers the animation (prevents flicker on long lists).
    val hasAnimated = rememberSaveable { mutableStateOf(false) }
    val animationState = remember { Animatable(if (hasAnimated.value) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (hasAnimated.value) return@LaunchedEffect
        delay((index.coerceAtLeast(0) * 16L).coerceAtMost(96L))
        animationState.animateTo(1f, animationSpec)
        hasAnimated.value = true
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
 * Rolling numeric readout for currency & counter totals with exact final Long precision
 * and tabular figures (`tnum`) so columns never jitter horizontally while animating.
 */
@Composable
fun AnimatedNumberText(
    value: Long,
    modifier: Modifier = Modifier,
    formatter: (Long) -> String = { "%,d".format(it) },
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = FontFamily.Monospace,
    textAlign: TextAlign? = null,
    maxLines: Int = 1,
    softWrap: Boolean = false,
    style: TextStyle = LocalTextStyle.current
) {
    var startValue by remember { mutableLongStateOf(value) }
    var settledValue by remember { mutableLongStateOf(value) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(value) {
        if (settledValue == value) return@LaunchedEffect
        startValue = settledValue
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 190, easing = FastOutSlowInEasing))
        settledValue = value
    }

    val p = progress.value
    val displayed = if (p >= 0.999f) {
        value
    } else {
        val current = startValue + ((value - startValue) * p).toLong()
        settledValue = current
        current
    }

    Text(
        text = formatter(displayed),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        maxLines = maxLines,
        softWrap = softWrap,
        style = style.copy(fontFeatureSettings = "tnum")
    )
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
