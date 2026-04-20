package com.example.bluex.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.bluex.ui.theme.BluexAccent
import com.example.bluex.ui.theme.GlassBorder
import com.example.bluex.ui.theme.GlassWhite
import kotlin.math.atan2
import kotlin.math.sqrt

enum class JoystickDirection { NONE, FORWARD, BACKWARD, LEFT, RIGHT }

@Composable
fun JoystickControl(
    modifier: Modifier = Modifier,
    onDirectionChanged: (JoystickDirection) -> Unit,
    onRelease: () -> Unit
) {
    val view = LocalView.current
    var handleOffset by remember { mutableStateOf(Offset.Zero) }
    var currentDirection by remember { mutableStateOf(JoystickDirection.NONE) }
    val joystickSize = 220.dp
    val handleRadius = 35.dp

    Box(
        modifier = modifier.size(joystickSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .size(joystickSize)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            // Haptic on first touch
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        },
                        onDragEnd = {
                            handleOffset = Offset.Zero
                            currentDirection = JoystickDirection.NONE
                            onRelease()
                        },
                        onDragCancel = {
                            handleOffset = Offset.Zero
                            currentDirection = JoystickDirection.NONE
                            onRelease()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val rawOffset = change.position - center
                            val maxRadius = size.width / 2f - handleRadius.toPx()

                            val distance = sqrt(
                                rawOffset.x * rawOffset.x + rawOffset.y * rawOffset.y
                            )
                            val clampedOffset = if (distance > maxRadius) {
                                rawOffset * (maxRadius / distance)
                            } else {
                                rawOffset
                            }

                            handleOffset = clampedOffset

                            val deadZone = maxRadius * 0.2f
                            val newDirection = if (distance < deadZone) {
                                JoystickDirection.NONE
                            } else {
                                val angle = Math.toDegrees(
                                    atan2(
                                        clampedOffset.y.toDouble(),
                                        clampedOffset.x.toDouble()
                                    )
                                )
                                when {
                                    angle in -45.0..45.0 -> JoystickDirection.RIGHT
                                    angle in 45.0..135.0 -> JoystickDirection.BACKWARD
                                    angle in -135.0..-45.0 -> JoystickDirection.FORWARD
                                    else -> JoystickDirection.LEFT
                                }
                            }

                            if (newDirection != currentDirection) {
                                currentDirection = newDirection
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onDirectionChanged(newDirection)
                            }
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.width / 2f

            // Outer ring - subtle glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        BluexAccent.copy(alpha = 0.05f),
                        BluexAccent.copy(alpha = 0.1f)
                    ),
                    center = center,
                    radius = outerRadius
                ),
                center = center,
                radius = outerRadius
            )

            // Outer border circle
            drawCircle(
                color = GlassBorder,
                center = center,
                radius = outerRadius - 2f,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
            )

            // Cross-hair lines (subtle direction guides)
            val guideAlpha = 0.08f
            val guideColor = Color.White.copy(alpha = guideAlpha)
            val innerGuideRadius = outerRadius * 0.25f
            val outerGuideRadius = outerRadius * 0.85f

            // Vertical line (up/down)
            drawLine(
                guideColor,
                Offset(center.x, center.y - outerGuideRadius),
                Offset(center.x, center.y - innerGuideRadius),
                strokeWidth = 1f
            )
            drawLine(
                guideColor,
                Offset(center.x, center.y + innerGuideRadius),
                Offset(center.x, center.y + outerGuideRadius),
                strokeWidth = 1f
            )
            // Horizontal line (left/right)
            drawLine(
                guideColor,
                Offset(center.x - outerGuideRadius, center.y),
                Offset(center.x - innerGuideRadius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                guideColor,
                Offset(center.x + innerGuideRadius, center.y),
                Offset(center.x + outerGuideRadius, center.y),
                strokeWidth = 1f
            )

            // Inner background circle
            drawCircle(
                color = GlassWhite,
                center = center,
                radius = outerRadius * 0.35f
            )

            // Handle position
            val handleCenter = center + handleOffset

            // Handle shadow
            drawCircle(
                color = BluexAccent.copy(alpha = 0.3f),
                center = handleCenter,
                radius = handleRadius.toPx() + 4f
            )

            // Handle gradient fill
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BluexAccent.copy(alpha = 0.9f),
                        BluexAccent.copy(alpha = 0.6f)
                    ),
                    center = handleCenter,
                    radius = handleRadius.toPx()
                ),
                center = handleCenter,
                radius = handleRadius.toPx()
            )

            // Handle highlight (top-left shine)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.Transparent
                    ),
                    center = Offset(
                        handleCenter.x - handleRadius.toPx() * 0.3f,
                        handleCenter.y - handleRadius.toPx() * 0.3f
                    ),
                    radius = handleRadius.toPx() * 0.6f
                ),
                center = handleCenter,
                radius = handleRadius.toPx()
            )
        }
    }
}
