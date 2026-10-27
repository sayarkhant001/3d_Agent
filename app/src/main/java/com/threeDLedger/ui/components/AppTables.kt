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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.threeDLedger.ui.theme.themeTableBorder

/**
 * ── UNIFIED TABLE LANGUAGE ───────────────────────────────────────────────────
 * Every number/amount table in the app follows one visual contract:
 *   1. Solid background — no zebra stripes, one clean colour per table body.
 *   2. Distinct borders — outer border + solid header band + straight lines
 *      dividing every row, visible left to right.
 *   3. Straight columns — the index (စဉ်) key runs straight top to bottom in a
 *      fixed-width centred column; numbers sit in their own centred column;
 *      amounts right-align against the same edge with "Ks" suffix.
 * Use AppTableShell + AppTableHeader + AppTableBodyRow + AppTableLine for new
 * tables, or apply the same recipe when editing existing screens.
 */

/** Solid table container with a distinct outer border. */
@Composable
fun AppTableShell(
    modifier: Modifier = Modifier,
    borderColor: Color = themeTableBorder(),
    borderWidth: Dp = 1.5.dp,
    corner: Dp = 12.dp,
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

/** Solid header band with a distinct bottom border separating it from rows. */
@Composable
fun AppTableHeader(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeEmeraldContainer())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
        HorizontalDivider(color = themeEmeraldBorder(), thickness = 1.25.dp)
    }
}

/** One solid table row: plain background, centre-aligned vertically. */
@Composable
fun AppTableBodyRow(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 7.dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
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
            color = themeTableBorder().copy(alpha = 0.5f),
            thickness = 1.dp
        )
    }
}

/** Vertical cell divider separating the number column from the amount column. */
@Composable
fun AppTableCellDivider(height: Dp = 20.dp, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.width(1.25.dp).height(height),
        color = themeTableBorder().copy(alpha = 0.65f)
    ) {}
}

/** Index (စဉ်) key — straight top to bottom in a fixed-width centred column. */
@Composable
fun AppTableIndexText(
    index: Int,
    modifier: Modifier = Modifier,
    width: Dp = 34.dp,
    fontSize: TextUnit = 12.5.sp
) {
    Text(
        "$index.",
        textAlign = TextAlign.Center,
        fontSize = fontSize,
        fontWeight = FontWeight.SemiBold,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.width(width)
    )
/** Number cell — centred, monospace, bold, never wraps. */
@Composable
fun AppTableNumberText(
    number: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 17.sp,
    color: Color = MaterialTheme.colorScheme.primary
) {
    Text(
        number,
        textAlign = TextAlign.Center,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        fontFamily = FontFamily.Monospace,
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
    fontSize: TextUnit = 16.sp,
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
            color = color,
            maxLines = 1
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "Ks",
            fontSize = (fontSize.value - 3.5f).sp,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

}
