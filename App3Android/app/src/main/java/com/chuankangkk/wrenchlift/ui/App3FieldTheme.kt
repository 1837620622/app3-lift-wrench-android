package com.chuankangkk.wrenchlift.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun App3FieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = app3DarkColorScheme,
        typography = app3Typography,
        shapes = app3Shapes,
        content = content,
    )
}

object AppDimens {
    val space2 = 2.dp
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp

    val controlHeight = 56.dp
    val compactControlHeight = 48.dp
    val statusHeight = 40.dp
    val panelRadius = 16.dp
    val groupRadius = 12.dp
    val controlRadius = 8.dp
}

object AppBreakpoints {
    val compactWidth = 600.dp
    val mediumWidth = 840.dp
    val expandedWidth = 1200.dp
    val compactHeight = 480.dp
    val shortHeight = 520.dp
    val comfortableHeight = 650.dp
    val tallHeight = 760.dp
}

object AppColors {
    val background = Color(0xFFF5F7F8)
    val backgroundAlt = Color(0xFFEDF1F3)
    val panel = Color(0xFFFFFFFF)
    val panelStrong = Color(0xFFE8EEF1)
    val panelInset = Color(0xFFFFFFFF)
    val panelRaised = Color(0xFFF0F3F5)
    val panelLine = Color(0xFFC9D3D9)
    val textPrimary = Color(0xFF172B36)
    val textSecondary = Color(0xFF526773)
    val textMuted = Color(0xFF637A86)
    val grid = Color(0xFFCFD9DF)
    val axis = Color(0xFF647985)
    val signal = Color(0xFF263F4C)
    val running = Color(0xFF087A60)
    val warning = Color(0xFF996500)
    val danger = Color(0xFFBD2734)
    val info = Color(0xFF176998)
    val torqueCurve = Color(0xFF006B9D)
    val forceCurve = warning
    val pressureCurve = Color(0xFF007F77)
    val teal = running
    val blue = info
    val amber = warning
    val magenta = danger
}

private val app3Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val app3Typography = Typography(
    displaySmall = TextStyle(
        fontSize = 42.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Black,
    ),
    headlineLarge = TextStyle(
        fontSize = 30.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 23.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = TextStyle(
        fontSize = 11.sp,
        lineHeight = 15.sp,
        fontWeight = FontWeight.Medium,
    ),
)

private val app3DarkColorScheme = lightColorScheme(
    primary = AppColors.teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF153B2B),
    onPrimaryContainer = Color(0xFFBDF3D1),
    secondary = AppColors.blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF19384E),
    onSecondaryContainer = Color(0xFFC8E6FA),
    tertiary = AppColors.amber,
    onTertiary = Color(0xFF161006),
    tertiaryContainer = Color(0xFF493512),
    onTertiaryContainer = Color(0xFFFFE2A6),
    error = AppColors.danger,
    onError = Color(0xFF180A0B),
    background = AppColors.background,
    onBackground = AppColors.textPrimary,
    surface = AppColors.panel,
    onSurface = AppColors.textPrimary,
    surfaceVariant = AppColors.panelStrong,
    onSurfaceVariant = AppColors.textSecondary,
    outline = AppColors.panelLine,
    outlineVariant = AppColors.grid,
    inverseSurface = AppColors.textPrimary,
    inverseOnSurface = AppColors.background,
)
