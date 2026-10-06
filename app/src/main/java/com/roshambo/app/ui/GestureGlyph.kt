package com.roshambo.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.roshambo.app.gesture.Gesture

/**
 * Hand gestures drawn as vector paths — no image assets, scales cleanly to any density.
 */
@Composable
fun GestureGlyph(
    gesture: Gesture,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    strokeWidth: Dp = 6.dp,
) {
    Canvas(modifier.size(size)) {
        val w = this.size.minDimension
        val sw = strokeWidth.toPx()
        drawGesture(gesture, tint, w, sw)
    }
}

private fun DrawScope.drawGesture(gesture: Gesture, tint: Color, w: Float, sw: Float) {
    // All coordinates are fractions of the canvas min-dimension.
    fun p(fx: Float, fy: Float) = Offset(w * fx, w * fy)

    val palm = Path().apply {
        moveTo(w * 0.30f, w * 0.92f)
        lineTo(w * 0.24f, w * 0.52f)
        quadraticBezierTo(w * 0.22f, w * 0.38f, w * 0.36f, w * 0.36f)
        lineTo(w * 0.64f, w * 0.36f)
        quadraticBezierTo(w * 0.78f, w * 0.38f, w * 0.76f, w * 0.52f)
        lineTo(w * 0.70f, w * 0.92f)
        close()
    }
    drawPath(palm, tint.copy(alpha = 0.16f))
    drawPath(palm, tint, style = Stroke(width = sw, cap = StrokeCap.Round))

    fun finger(x: Float, top: Float) {
        drawLine(
            color = tint,
            start = p(x, 0.40f),
            end = p(x, top),
            strokeWidth = sw,
            cap = StrokeCap.Round,
        )
    }

    when (gesture) {
        // Fist — no fingers out, knuckle marks only.
        Gesture.ROCK -> {
            drawLine(tint, p(0.38f, 0.50f), p(0.46f, 0.56f), sw * 0.7f, StrokeCap.Round)
            drawLine(tint, p(0.50f, 0.48f), p(0.58f, 0.54f), sw * 0.7f, StrokeCap.Round)
            drawLine(tint, p(0.62f, 0.50f), p(0.68f, 0.57f), sw * 0.7f, StrokeCap.Round)
        }

        // Two fingers up.
        Gesture.SCISSORS -> {
            finger(0.42f, 0.10f)
            finger(0.58f, 0.10f)
        }

        // Open palm — four fingers plus thumb.
        Gesture.PAPER -> {
            finger(0.34f, 0.24f)
            finger(0.45f, 0.14f)
            finger(0.57f, 0.16f)
            finger(0.68f, 0.28f)
            drawLine(tint, p(0.26f, 0.60f), p(0.14f, 0.46f), sw, StrokeCap.Round)
        }

        Gesture.UNKNOWN -> {
            drawCircle(tint.copy(alpha = 0.5f), radius = w * 0.06f, center = p(0.5f, 0.5f))
            drawCircle(
                tint.copy(alpha = 0.35f),
                radius = w * 0.30f,
                center = p(0.5f, 0.5f),
                style = Stroke(width = sw * 0.6f),
            )
        }
    }
}

/** Simple outlined card background with a soft border, used across panels. */
fun DrawScope.drawPanelBorder(topLeft: Offset, size: Size, corner: Float, color: Color, sw: Float) {
    drawRoundRect(
        color = color,
        topLeft = topLeft,
        size = size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner),
        style = Stroke(width = sw),
    )
}
