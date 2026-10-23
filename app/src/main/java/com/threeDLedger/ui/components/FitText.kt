package com.threeDLedger.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Auto-Fit Text — guarantees text NEVER wraps or gets truncated on any device/size.
 * ─────────────────────────────────────────────────────────────────────────────
 * Measures the available width and progressively reduces the font size
 * (from [maxFontSize] down to [minFontSize]) until the text fits within
 * the parent constraints on a single line.
 *
 * If the text cannot fit even at [minFontSize] (extremely narrow containers),
 * it gracefully falls back to ellipsis instead of wrapping.
 *
 * Ideal for: menu card titles, keypad labels, stat values, button labels,
 * table cell text — anywhere a single line must always be fully readable.
 *
 * Example:
 *   FitText(
 *       text = "ကော်မရှင်",
 *       maxFontSize = 15.5.sp,
 *       minFontSize = 10.sp,
 *       fontWeight = FontWeight.ExtraBold
 *   )
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    maxFontSize: TextUnit = 16.sp,
    minFontSize: TextUnit = 9.sp,
    fontWeight: FontWeight = FontWeight.Normal,
    fontFamily: FontFamily = FontFamily.Default,
    letterSpacing: TextUnit = 0.sp,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    style: TextStyle = TextStyle.Default
) {
    val textMeasurer = rememberTextMeasurer()
    val currentTextStyle = style.copy(
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing
    )

    // Stable inputs that trigger re-measurement
    val measuredText = rememberUpdatedState(text)
    val measuredMax = rememberUpdatedState(maxFontSize)
    val measuredMin = rememberUpdatedState(minFontSize)
    val measuredStyle = rememberUpdatedState(currentTextStyle)

    BoxWithConstraints(modifier = modifier) {
        val availableWidthPx = constraints.maxWidth
        val fittedFontSize = remember(
            text,
            availableWidthPx,
            maxFontSize.value,
            minFontSize.value,
            fontWeight,
            fontFamily,
            letterSpacing.value
        ) {
            fitFontSize(
                text = measuredText.value,
                availableWidthPx = availableWidthPx,
                maxSp = measuredMax.value.value,
                minSp = measuredMin.value.value,
                style = measuredStyle.value,
                textMeasurer = textMeasurer
            )
        }

        Text(
            text = text,
            fontSize = fittedFontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            letterSpacing = letterSpacing,
            color = color,
            textAlign = textAlign,
            style = currentTextStyle,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Binary-search for the largest font size (in "sp" raw value) that
 * fits [text] within [availableWidthPx] on a single line.
 */
private fun fitFontSize(
    text: String,
    availableWidthPx: Int,
    maxSp: Float,
    minSp: Float,
    style: TextStyle,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
): TextUnit {
    if (text.isEmpty() || availableWidthPx <= 0) return minSp.sp

    fun fitsAt(spValue: Float): Boolean {
        val layoutResult = textMeasurer.measure(
            text = androidx.compose.ui.text.AnnotatedString(text),
            style = style.copy(fontSize = spValue.sp),
            constraints = Constraints(maxWidth = availableWidthPx),
            maxLines = 1
        )
        return !layoutResult.hasVisualOverflow
    }

    // Fast path: does it fit at max size?
    if (fitsAt(maxSp)) return maxSp.sp

    // Already at/below min size and still doesn't fit → ellipsis fallback
    if (maxSp <= minSp) return minSp.sp

    // Binary search between min and max
    var low = minSp
    var high = maxSp
    var bestFit = minSp
    while (high - low > 0.5f) {
        val mid = (low + high) / 2f
        if (fitsAt(mid)) {
            bestFit = mid
            low = mid + 0.5f
        } else {
            high = mid - 0.5f
        }
    }
    return bestFit.sp
}
