package com.threeDLedger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.threeDLedger.ui.theme.themeEmeraldBorder
import com.threeDLedger.ui.theme.themeEmeraldContainer
import com.threeDLedger.ui.theme.themeGold
import com.threeDLedger.ui.theme.themeTableBorder
import com.threeDLedger.ui.theme.themeTableRowEven
import com.threeDLedger.ui.theme.themeTableRowOdd

/**
 * ── UNIFIED LEDGER TABLE LANGUAGE ──────────────────────────────────────────────
 * Every number/amount table follows one crisp accounting ledger contract:
 *   1. Embossed outer frame + double emerald/gold ledger header rule.
 *   2. Gentle alternating warm-paper row depth so senior eyes never lose a row.
 *   3. Straight monospace columns — fixed index, centred bold 3D number,
 *      and right-aligned amount.
 */

/** Embossed table container with a crisp outer border and soft elevation. */
@Composable
fun AppTableShell(
    modifier: Modifier = Modifier,
    borderColor: Color = themeTableBorder(),
    borderWidth: Dp = 1.5.dp,
    corner: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(corner),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(borderWidth, borderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

/** Brushed header band with a classic double-rule emerald + burnished-gold accent. */
@Composable
fun AppTableHeader(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            themeEmeraldContainer(),
                            themeEmeraldContainer().copy(alpha = 0.78f)
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        HorizontalDivider(color = themeEmeraldBorder(), thickness = 1.25.dp)
        HorizontalDivider(color = themeGold().copy(alpha = 0.25f), thickness = 0.75.dp)
    }
}

/** Ledger row with ultra-gentle alternating paper warmth for effortless row tracking. */
@Composable
fun AppTableBodyRow(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 8.5.dp,
    isAlternate: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isAlternate) themeTableRowEven() else themeTableRowOdd())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Straight full-width line between rows — distinct and neat. */
@Composable
fun AppTableLine(show: Boolean = true) {
    if (show) {
        HorizontalDivider(
            color = themeTableBorder().copy(alpha = 0.55f),
            thickness = 1.dp
        )
    }
}

/** Vertical cell divider separating the number column from the amount column. */
@Composable
fun AppTableCellDivider(height: Dp = 22.dp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.width(1.25.dp).height(height),
        color = themeTableBorder().copy(alpha = 0.72f)
    ) {}
}

/** Brushed footer band with a top ledger accent rule for table totals. */
@Composable
fun AppTableFooter(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = themeGold().copy(alpha = 0.25f), thickness = 0.75.dp)
        HorizontalDivider(color = themeEmeraldBorder(), thickness = 1.25.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeEmeraldContainer().copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** Index (စဉ်) key — straight top to bottom in a fixed-width centred column. */
@Composable
fun AppTableIndexText(
    index: Int,
    modifier: Modifier = Modifier,
    width: Dp = 36.dp,
    fontSize: TextUnit = 13.5.sp
) {
    Text(
        "$index.",
        textAlign = TextAlign.Center,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.width(width)
    )
}

/** Number cell — centred, monospace, bold, never wraps. */
@Composable
fun AppTableNumberText(
    number: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        number,
        textAlign = TextAlign.Center,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace,
        style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        letterSpacing = 1.5.sp,
        color = color,
        maxLines = 1,
        modifier = modifier
    )
}

/** Amount cell — right-aligned on the same edge for every row, with "Ks". */
@Composable
fun AppTableAmountText(
    amount: Long,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            "%,d".format(amount),
            textAlign = TextAlign.End,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            color = color,
            maxLines = 1
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "Ks",
            fontSize = (fontSize.value - 3.5f).coerceAtLeast(11f).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
