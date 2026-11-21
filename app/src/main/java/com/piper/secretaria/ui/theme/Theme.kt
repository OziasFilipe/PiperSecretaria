package com.piper.secretaria.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PiperColors {
    val Purple = Color(0xFF7C3AED)
    val PurpleLight = Color(0xFFA78BFA)
    val PurpleSoft = Color(0xFFEDE9FE)
    val Night = Color(0xFF1E1036)
    val NightDeep = Color(0xFF120A24)
    val Green = Color(0xFF22C55E)
    val Orange = Color(0xFFF59E0B)
    val Blue = Color(0xFF3B82F6)
    val Pink = Color(0xFFEC4899)

    val headerGradient = Brush.verticalGradient(listOf(Color(0xFF1B0F3B), Color(0xFF3B1A7A), Color(0xFF6D28D9)))
    val buttonGradient = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9)))
}

private val Light = lightColorScheme(
    primary = PiperColors.Purple,
    onPrimary = Color.White,
    primaryContainer = PiperColors.PurpleSoft,
    onPrimaryContainer = Color(0xFF2E1065),
    secondary = Color(0xFF8B5CF6),
    background = Color(0xFFF7F5FC),
    onBackground = Color(0xFF1C1530),
    surface = Color.White,
    onSurface = Color(0xFF1C1530),
    surfaceVariant = Color(0xFFF1EDFB),
    onSurfaceVariant = Color(0xFF5E5675),
    outlineVariant = Color(0xFFE6E0F5),
)

private val Dark = darkColorScheme(
    primary = PiperColors.PurpleLight,
    onPrimary = Color(0xFF2E1065),
    primaryContainer = Color(0xFF3B1F70),
    onPrimaryContainer = PiperColors.PurpleSoft,
    secondary = PiperColors.PurpleLight,
    background = PiperColors.NightDeep,
    onBackground = Color(0xFFEDE9FE),
    surface = Color(0xFF1C1433),
    onSurface = Color(0xFFEDE9FE),
    surfaceVariant = Color(0xFF261B42),
    onSurfaceVariant = Color(0xFFB9AFD6),
    outlineVariant = Color(0xFF332656),
)

private val Type = Typography(
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
)

private val PiperShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun PiperTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = Type,
        shapes = PiperShapes,
        content = content,
    )
}
