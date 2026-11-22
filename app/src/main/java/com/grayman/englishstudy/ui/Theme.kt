package com.grayman.englishstudy.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Fixed game colors: they carry meaning (progress, streak, XP), so they stay the same in dark mode. */
object Game {
    val Lagoon = Color(0xFF12A594)
    val LagoonEdge = Color(0xFF0B7A6E)
    val LagoonSoft = Color(0xFFCDF3EC)
    val Coral = Color(0xFFFF7A59)
    val CoralEdge = Color(0xFFD1542F)
    val Flame = Color(0xFFFF9F1C)
    val Xp = Color(0xFF4C7DF0)
    val Good = Color(0xFF2FB344)
    val GoodEdge = Color(0xFF1F8A32)
    val GoodSoft = Color(0xFFD9F5DE)
    val OnGoodSoft = Color(0xFF14601F)
    val Bad = Color(0xFFE5484D)
    val BadSoft = Color(0xFFFCE1E2)
    val OnBadSoft = Color(0xFF8A1C20)
    val Locked = Color(0xFFB9C2C9)
}

private val LightColors = lightColorScheme(
    primary = Game.Lagoon,
    onPrimary = Color.White,
    background = Color(0xFFF8FAF8),
    onBackground = Color(0xFF17212B),
    surface = Color.White,
    onSurface = Color(0xFF17212B),
    surfaceVariant = Color(0xFFEEF2F2),
    onSurfaceVariant = Color(0xFF55636E),
    outline = Color(0xFFD5DCDF),
)

private val DarkColors = darkColorScheme(
    primary = Game.Lagoon,
    onPrimary = Color.White,
    background = Color(0xFF10181D),
    onBackground = Color(0xFFEAF0F2),
    surface = Color(0xFF1A242B),
    onSurface = Color(0xFFEAF0F2),
    surfaceVariant = Color(0xFF243038),
    onSurfaceVariant = Color(0xFFA7B5BE),
    outline = Color(0xFF34424B),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    labelLarge = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold),
)

@Composable
fun SpeakDailyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
