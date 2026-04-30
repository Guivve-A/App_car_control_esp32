package com.example.bluex.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.bluex.ui.theme.BluexAccent
import com.example.bluex.ui.theme.GlassBorder
import com.example.bluex.ui.theme.GlassWhite

enum class JoystickDirection { NONE, FORWARD, BACKWARD, LEFT, RIGHT }

@Composable
fun JoystickControl(
    modifier: Modifier = Modifier,
    onDirectionChanged: (JoystickDirection) -> Unit,
    onRelease: () -> Unit
) {
    val view = LocalView.current
    var pressedDirection by remember { mutableStateOf(JoystickDirection.NONE) }

    Canvas(
        modifier = modifier
            .size(220.dp)
            .pointerInput(Unit) {
                val gap = 4.dp.toPx()
                val cell = (size.width.toFloat() - 2f * gap) / 3f

                // Returns which grid column (0,1,2) or -1 if inside a gap
                fun cellIndex(coord: Float): Int = when {
                    coord < cell              -> 0
                    coord < cell + gap        -> -1
                    coord < 2f * cell + gap   -> 1
                    coord < 2f * cell + 2f * gap -> -1
                    else                      -> 2
                }

                fun directionAt(pos: Offset): JoystickDirection {
                    val col = cellIndex(pos.x)
                    val row = cellIndex(pos.y)
                    if (col < 0 || row < 0) return JoystickDirection.NONE
                    return when {
                        col == 1 && row == 0 -> JoystickDirection.FORWARD
                        col == 0 && row == 1 -> JoystickDirection.LEFT
                        col == 2 && row == 1 -> JoystickDirection.RIGHT
                        col == 1 && row == 2 -> JoystickDirection.BACKWARD
                        else                 -> JoystickDirection.NONE  // corners + center
                    }
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val initial = directionAt(down.position)
                    pressedDirection = initial
                    if (initial != JoystickDirection.NONE) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onDirectionChanged(initial)
                    }

                    var current = initial
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id }
                        if (pointer == null || !pointer.pressed) break
                        pointer.consume()
                        val newDir = directionAt(pointer.position)
                        if (newDir != current) {
                            current = newDir
                            pressedDirection = newDir
                            if (newDir != JoystickDirection.NONE) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onDirectionChanged(newDir)
                            }
                        }
                    }

                    pressedDirection = JoystickDirection.NONE
                    onRelease()
                }
            }
    ) {
        val gap = 4.dp.toPx()
        val cell = (size.width - 2f * gap) / 3f
        val cr = 20f   // outer corner radius

        // Column / row start positions
        val x0 = 0f;         val x1 = cell + gap;           val x2 = 2f * cell + 2f * gap
        val y0 = 0f;         val y1 = cell + gap;           val y2 = 2f * cell + 2f * gap

        // Build a rounded-rect path with per-corner radii (tl, tr, br, bl)
        fun armPath(rect: Rect, tl: Float, tr: Float, br: Float, bl: Float) = Path().apply {
            addRoundRect(
                RoundRect(
                    rect        = rect,
                    topLeft     = CornerRadius(tl),
                    topRight    = CornerRadius(tr),
                    bottomRight = CornerRadius(br),
                    bottomLeft  = CornerRadius(bl)
                )
            )
        }

        fun drawArm(
            dir: JoystickDirection,
            rect: Rect,
            tl: Float, tr: Float, br: Float, bl: Float
        ) {
            val isPressed = dir != JoystickDirection.NONE && pressedDirection == dir
            val path = armPath(rect, tl, tr, br, bl)
            drawPath(path, color = if (isPressed) BluexAccent else GlassWhite)
            if (!isPressed) drawPath(path, color = GlassBorder, style = Stroke(width = 1.5f))
            if (dir != JoystickDirection.NONE) {
                drawArrow(rect, dir, if (isPressed) Color.White else BluexAccent)
            }
        }

        //           ┌──────────────────── corner radii (tl, tr, br, bl) ───────────────────┐
        drawArm(JoystickDirection.FORWARD,  Rect(x1, y0, x1+cell, y0+cell), cr, cr,  0f,  0f)
        drawArm(JoystickDirection.LEFT,     Rect(x0, y1, x0+cell, y1+cell), cr,  0f,  0f, cr)
        drawArm(JoystickDirection.NONE,     Rect(x1, y1, x1+cell, y1+cell),  0f,  0f,  0f,  0f)
        drawArm(JoystickDirection.RIGHT,    Rect(x2, y1, x2+cell, y1+cell),  0f, cr, cr,  0f)
        drawArm(JoystickDirection.BACKWARD, Rect(x1, y2, x1+cell, y2+cell),  0f,  0f, cr, cr)
    }
}

// Draws a filled triangle pointing in the given direction, centered inside rect
private fun DrawScope.drawArrow(rect: Rect, direction: JoystickDirection, color: Color) {
    val cx = rect.left + rect.width  / 2f
    val cy = rect.top  + rect.height / 2f
    val s  = minOf(rect.width, rect.height) * 0.30f

    val path = Path()
    when (direction) {
        JoystickDirection.FORWARD -> {
            path.moveTo(cx,              cy - s)
            path.lineTo(cx + s * 0.85f,  cy + s * 0.55f)
            path.lineTo(cx - s * 0.85f,  cy + s * 0.55f)
        }
        JoystickDirection.BACKWARD -> {
            path.moveTo(cx,              cy + s)
            path.lineTo(cx + s * 0.85f,  cy - s * 0.55f)
            path.lineTo(cx - s * 0.85f,  cy - s * 0.55f)
        }
        JoystickDirection.LEFT -> {
            path.moveTo(cx - s,          cy)
            path.lineTo(cx + s * 0.55f,  cy - s * 0.85f)
            path.lineTo(cx + s * 0.55f,  cy + s * 0.85f)
        }
        JoystickDirection.RIGHT -> {
            path.moveTo(cx + s,          cy)
            path.lineTo(cx - s * 0.55f,  cy - s * 0.85f)
            path.lineTo(cx - s * 0.55f,  cy + s * 0.85f)
        }
        else -> return
    }
    path.close()
    drawPath(path, color = color)
}
