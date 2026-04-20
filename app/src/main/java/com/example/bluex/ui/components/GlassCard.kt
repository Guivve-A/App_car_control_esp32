package com.example.bluex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.bluex.ui.theme.GlassBorder
import com.example.bluex.ui.theme.GlassHighlight
import com.example.bluex.ui.theme.GlassWhite

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    backgroundColor: Color = GlassWhite,
    borderColor: Color = GlassBorder,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        GlassHighlight,
                        backgroundColor,
                        Color.Transparent
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = borderColor,
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}

@Composable
fun GlassCardAccent(
    accentColor: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.15f),
                        accentColor.copy(alpha = 0.05f),
                        Color.Transparent
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = accentColor.copy(alpha = 0.3f),
                shape = shape
            )
            .padding(contentPadding),
        content = content
    )
}
