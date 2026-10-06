package com.roshambo.app.ui.theme

import androidx.compose.ui.graphics.Color

// Warm off-white base — reads as paper, keeps the camera the only dark element.
val Canvas = Color(0xFFF7F5F2)
val Surface = Color(0xFFFFFFFF)
val Ink = Color(0xFF1A1A1A)
val InkMuted = Color(0xFF8A8780)
val Hairline = Color(0xFFE8E4DD)

val RockAccent = Color(0xFF3A86FF)
val ScissorsAccent = Color(0xFFFF6B6B)
val PaperAccent = Color(0xFF2BB673)

val WinGreen = Color(0xFF2BB673)
val LoseRed = Color(0xFFE5544B)
val DrawAmber = Color(0xFFD99A2B)

fun accentFor(index: Int): Color = when (index) {
    0 -> RockAccent
    1 -> ScissorsAccent
    else -> PaperAccent
}
