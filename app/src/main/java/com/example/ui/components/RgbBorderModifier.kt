package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Animated Google AI / Cyber RGB Palette
val RgbBorderGradientColors = listOf(
    Color(0xFF00F0FF), // Neon Cyan
    Color(0xFF4361EE), // Royal Blue
    Color(0xFF7209B7), // Violet
    Color(0xFFF72585), // Neon Magenta
    Color(0xFFFF9E00), // Electric Orange
    Color(0xFF00FF87), // Matrix Green
    Color(0xFF00F0FF)  // Loop Cyan
)

/**
 * Applies an animated rotating RGB sweep gradient border around any Composable
 * using Jetpack Compose's Modifier.drawBehind with a rotating Brush.sweepGradient.
 */
fun Modifier.animatedRgbSweepBorder(
    borderWidth: Dp = 2.dp,
    cornerRadius: Dp = 16.dp,
    durationMillis: Int = 4000
): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "rgb_sweep_rotation")
    val degrees by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation_degrees"
    )

    this.drawBehind {
        val strokeWidthPx = borderWidth.toPx()
        val cornerRadiusPx = cornerRadius.toPx()
        val halfStroke = strokeWidthPx / 2f

        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = halfStroke,
                    top = halfStroke,
                    right = size.width - halfStroke,
                    bottom = size.height - halfStroke,
                    cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                )
            )
        }

        val sweepBrush = Brush.sweepGradient(
            colors = RgbBorderGradientColors,
            center = Offset(size.width / 2f, size.height / 2f)
        )

        // Rotate the brush sweep around the container's center
        rotate(degrees = degrees, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawPath(
                path = path,
                brush = sweepBrush,
                style = Stroke(width = strokeWidthPx)
            )
        }
    }
}
