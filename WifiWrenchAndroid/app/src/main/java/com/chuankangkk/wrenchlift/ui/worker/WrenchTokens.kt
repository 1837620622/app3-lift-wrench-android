package com.chuankangkk.wrenchlift.ui.worker

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计规范 §6.2 色板 token。两套择一为主题，同屏不混用；默认浅色工业中性（现场强光），设置里可切深色（室内/平板）。
 */
@Immutable
data class WrenchPalette(
    val isDark: Boolean,
    val canvas: Color,
    val panel: Color,
    val panelSunken: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val danger: Color,
    val dangerSoft: Color,
    val onDanger: Color,
    val ok: Color,
    val warn: Color,
    val disabledFill: Color,
    val disabledText: Color,
    val chartGrid: Color,
    val chartAxis: Color,
    val chartLine: Color,
    val chartLineOld: Color,
    val scrim: Color,
)

val LightIndustrialPalette = WrenchPalette(
    isDark = false,
    canvas = Color(0xFFF4F6F8),
    panel = Color(0xFFFFFFFF),
    panelSunken = Color(0xFFEEF1F4),
    hairline = Color(0xFFD7DDE3),
    textPrimary = Color(0xFF1B1F24),
    textSecondary = Color(0xFF5C6670),
    textTertiary = Color(0xFF7D8791),
    accent = Color(0xFF0D9B8A),
    accentSoft = Color(0xFFE2F4F1),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFD1433A),
    dangerSoft = Color(0xFFFBE9E7),
    onDanger = Color(0xFFFFFFFF),
    ok = Color(0xFF0D9B8A),
    warn = Color(0xFFA87410),
    disabledFill = Color(0xFFE9ECEF),
    disabledText = Color(0xFF9AA3AD),
    chartGrid = Color(0xFFE5E9ED),
    chartAxis = Color(0xFF8A96A3),
    chartLine = Color(0xFF0D9B8A),
    chartLineOld = Color(0xFFB4BEC7),
    scrim = Color(0x661B1F24),
)

val DarkIndustrialPalette = WrenchPalette(
    isDark = true,
    canvas = Color(0xFF121417),
    panel = Color(0xFF1A1E24),
    panelSunken = Color(0xFF15181D),
    hairline = Color(0xFF2A313A),
    textPrimary = Color(0xFFF2F4F7),
    textSecondary = Color(0xFF9AA3AD),
    textTertiary = Color(0xFF7A848F),
    accent = Color(0xFF2BB7A9),
    accentSoft = Color(0xFF16302D),
    onAccent = Color(0xFF0E1114),
    danger = Color(0xFFE85D4C),
    dangerSoft = Color(0xFF3A1D1A),
    onDanger = Color(0xFFFFFFFF),
    ok = Color(0xFF3DDC97),
    warn = Color(0xFFE6B84A),
    disabledFill = Color(0xFF22272E),
    disabledText = Color(0xFF5E6873),
    chartGrid = Color(0xFF242A31),
    chartAxis = Color(0xFF6E7883),
    chartLine = Color(0xFF2BB7A9),
    chartLineOld = Color(0xFF46505B),
    scrim = Color(0x99000000),
)

/** 启动页固定深炭底（SPL-01），与主题无关。 */
object SplashColors {
    val background = Color(0xFF141414)
    val title = Color(0xFFF2F4F7)
    val subtitle = Color(0xFFB8C0C8)
    val footer = Color(0xFF8A939C)
}

val LocalWrenchPalette = staticCompositionLocalOf { LightIndustrialPalette }

/** 设计规范 §6.3 字号阶梯（sp）。数字统一表格数字，避免跳动。 */
object WrenchType {
    private const val TNUM = "tnum"
    val display: TextUnit = 46.sp
    val title: TextUnit = 32.sp
    val headline: TextUnit = 17.sp
    val body: TextUnit = 15.sp
    val caption: TextUnit = 12.sp
    val button: TextUnit = 16.sp

    fun number(size: TextUnit, weight: FontWeight = FontWeight.SemiBold) =
        TextStyle(fontSize = size, fontWeight = weight, fontFeatureSettings = TNUM, lineHeight = size * 1.05f)

    val headlineStyle = TextStyle(fontSize = headline, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
    val bodyStyle = TextStyle(fontSize = body, fontWeight = FontWeight.Normal, lineHeight = 20.sp)
    val bodyStrong = TextStyle(fontSize = body, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp)
    val captionStyle = TextStyle(fontSize = caption, fontWeight = FontWeight.Normal, lineHeight = 16.sp, fontFeatureSettings = TNUM)
    val buttonStyle = TextStyle(fontSize = button, fontWeight = FontWeight.Medium, lineHeight = 20.sp)
}

/** §6.4 圆角与间距；§4.4 触控目标。 */
object WrenchDimens {
    val panelRadius = 12.dp
    val buttonRadius = 10.dp
    val sheetRadius = 14.dp
    val screenEdge = 12.dp
    val gap = 10.dp
    val controlGap = 10.dp
    val topBarHeight = 56.dp
    val touch = 48.dp
    val motionButton = 52.dp
    val stopButton = 60.dp
    val hairline = 1.dp

    val panelShape = RoundedCornerShape(panelRadius)
    val buttonShape = RoundedCornerShape(buttonRadius)
}

@Composable
fun WrenchWorkerTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkIndustrialPalette else LightIndustrialPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent, error = palette.danger,
            background = palette.canvas, surface = palette.panel, onSurface = palette.textPrimary,
            onBackground = palette.textPrimary, surfaceVariant = palette.panelSunken, onSurfaceVariant = palette.textSecondary,
            outline = palette.hairline, outlineVariant = palette.hairline, surfaceContainerHigh = palette.panel,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent, error = palette.danger,
            background = palette.canvas, surface = palette.panel, onSurface = palette.textPrimary,
            onBackground = palette.textPrimary, surfaceVariant = palette.panelSunken, onSurfaceVariant = palette.textSecondary,
            outline = palette.hairline, outlineVariant = palette.hairline, surfaceContainerHigh = palette.panel,
        )
    }
    CompositionLocalProvider(LocalWrenchPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
