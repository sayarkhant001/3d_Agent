package com.threeDLedger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.threeDLedger.logic.SoundManager
import com.threeDLedger.ui.theme.themeButtonBorder
import com.threeDLedger.ui.theme.themePrimaryGradient

/**
 * App-wide signature buttons.
 * ────────────────────────────────────────────────────────────────────────
 * PrimaryGradientButton — the dominant CTA: rich emerald gradient face,
 * strong 1.5dp brand border, tactile press-scale animation and click sound.
 * SecondaryOutlineButton — tonal companion with a dominant border for
 * less-prominent actions.
 */
@Composable
fun PrimaryGradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val borderColor = themeButtonBorder()

    val background = if (enabled) {
        Brush.horizontalGradient(themePrimaryGradient())
    } else {
        val disabled = MaterialTheme.colorScheme.surfaceVariant
        Brush.horizontalGradient(listOf(disabled, disabled))
    }

    Box(
        modifier = modifier
            .shadow(elevation = if (enabled) 5.dp else 0.dp, shape = shape)
            .clip(shape)
            .background(background)
            .border(
                BorderStroke(1.5.dp, borderColor.copy(alpha = if (enabled) 1f else 0.35f)),
                shape
            )
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                SoundManager.getInstance(context).playClick()
                onClick()
            }
            .padding(contentPadding)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                content()
            }
        }
    }
}

@Composable
fun SecondaryOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(14.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val borderColor = themeButtonBorder()

    Box(
        modifier = modifier
            .shadow(elevation = if (enabled) 3.dp else 0.dp, shape = shape)
            .clip(shape)
            .background(
                if (enabled) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                BorderStroke(1.25.dp, borderColor.copy(alpha = if (enabled) 0.85f else 0.3f)),
                shape
            )
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled
            ) {
                SoundManager.getInstance(context).playSoftTap()
                onClick()
            }
            .padding(contentPadding)
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (enabled) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                content()
            }
        }
    }
}
