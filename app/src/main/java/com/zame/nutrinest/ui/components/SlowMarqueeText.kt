package com.zame.nutrinest.ui.components

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A single-line title that stays still when it fits and gently scrolls only
 * when the available width is too small to show the complete text.
 */
@Composable
fun SlowMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    style: TextStyle = LocalTextStyle.current
) {
    Text(
        text = text,
        modifier = modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            repeatDelayMillis = 2_000,
            initialDelayMillis = 1_200,
            spacing = MarqueeSpacing(24.dp),
            velocity = 18.dp
        ),
        color = color,
        fontWeight = fontWeight,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip
    )
}
