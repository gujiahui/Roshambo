package com.roshambo.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val RoshamboColors = lightColorScheme(
    primary = RockAccent,
    onPrimary = Surface,
    secondary = PaperAccent,
    onSecondary = Surface,
    tertiary = ScissorsAccent,
    background = Canvas,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = Hairline,
    onSurfaceVariant = InkMuted,
    outline = Hairline,
)

private val RoshamboType = Typography(
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.Bold, color = Ink),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Ink),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = Ink),
    bodyLarge = TextStyle(fontSize = 15.sp, color = InkMuted),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink),
)

@Composable
fun RoshamboTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = RoshamboColors, typography = RoshamboType, content = content)
}
