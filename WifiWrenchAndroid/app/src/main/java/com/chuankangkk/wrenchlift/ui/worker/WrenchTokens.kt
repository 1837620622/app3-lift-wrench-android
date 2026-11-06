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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设计规范 §6.2 色板 token。两套择一为主题，同屏不混用。
 * 默认深色工业（与产品方 T 手把工业手机渲染一致）；强光现场可在设置里切浅色工业中性。
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
    /** 斜率曲线用蓝色，与扭矩青绿区分；与导出图同色系。 */
    val chartAngle: Color,
    /** 转折候选（未验证）专用紫色：与扭矩青绿、斜率蓝、目标琥珀、报错红都区分开。 */
    val chartCandidate: Color,
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
    chartAngle = Color(0xFF3A6EA5),
    chartCandidate = Color(0xFF9A4FC2),
    scrim = Color(0x661B1F24),
)

/** 默认主题：对齐产品方工作台渲染（近黑底、深色面板、发丝描边、青绿强调），色值取自设计规范 §6.2 深色工业。 */
val DarkIndustrialPalette = WrenchPalette(
    isDark = true,
    canvas = Color(0xFF0D0F12),
    panel = Color(0xFF161B20),
    panelSunken = Color(0xFF1C242B),
    hairline = Color(0xFF262E36),
    textPrimary = Color(0xFFF2F4F7),
    textSecondary = Color(0xFF9AA3AD),
    textTertiary = Color(0xFF6E7883),
    accent = Color(0xFF2BB7A9),
    accentSoft = Color(0xFF14302D),
    onAccent = Color(0xFF071311),
    danger = Color(0xFFE85D4C),
    dangerSoft = Color(0xFF3A1D1A),
    onDanger = Color(0xFFFFFFFF),
    ok = Color(0xFF3DDC97),
    warn = Color(0xFFE6B84A),
    disabledFill = Color(0xFF1A1F25),
    disabledText = Color(0xFF55606B),
    chartGrid = Color(0xFF1F262D),
    chartAxis = Color(0xFF56616C),
    chartLine = Color(0xFF2BB7A9),
    chartLineOld = Color(0xFF3B4650),
    chartAngle = Color(0xFF6FA8DC),
    chartCandidate = Color(0xFFC79BF2),
    scrim = Color(0xB3000000),
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

    /** 标签 / 辅助行（顶栏格名、读数标签、峰值、新鲜度、状态行）：统一 13sp。 */
    val labelStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp, fontFeatureSettings = TNUM)

    /** 顶栏格值：16sp 半粗、表格数字。 */
    val valueStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 20.sp, fontFeatureSettings = TNUM)
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
    val config = LocalConfiguration.current
    val base = LocalDensity.current
    val scale = largeScreenScale(config.screenWidthDp, config.screenHeightDp)
    val density = if (scale == 1f) base else Density(base.density * scale, base.fontScale)
    CompositionLocalProvider(LocalWrenchPalette provides palette, LocalDensity provides density) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/**
 * 平板（短边 ≥ 600dp）整体放大界面，按 960×450dp 的横屏工作台比例取较小倍数，最多 1.3 倍：
 * 字号、按钮、间距一起放大，避免大屏上字小、留白大。手机（三防机 800×360 / 914×411 等）保持 1 倍。
 */
fun largeScreenScale(widthDp: Int, heightDp: Int): Float {
    if (minOf(widthDp, heightDp) < 600) return 1f
    return minOf(widthDp / 960f, heightDp / 450f).coerceIn(1f, 1.3f)
}
