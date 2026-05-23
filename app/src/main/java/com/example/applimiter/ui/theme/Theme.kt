package com.example.applimiter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 雾感冰川蓝配色：安静、高级、克制、未来感。
 *
 * 用户指定主色：
 *   冰川蓝 #5B8CFF
 *   雾白   #F4F8FF
 *   浅银蓝 #DCE8FF
 *   辅助青绿 #8FDCC8
 *   深灰蓝 #2E3A59
 */
object AppPalette {
    // 主色：冰川蓝
    val PrimaryLight = Color(0xFF5B8CFF)
    val PrimaryDark = Color(0xFFA8C2FF)

    // 渐变末端：稍深的蓝（用于 PrimaryLight → PrimaryDeep 渐变按钮 / FAB）
    val PrimaryDeep = Color(0xFF3A6BE6)
    val PrimaryDeepDark = Color(0xFF6A8CFF)

    // 辅助：青绿
    val Accent = Color(0xFF8FDCC8)
    val AccentDark = Color(0xFFB8EEDC)

    // 中性
    val FogWhite = Color(0xFFF4F8FF)        // 雾白
    val SilverBlue = Color(0xFFDCE8FF)      // 浅银蓝
    val DeepInk = Color(0xFF2E3A59)         // 深灰蓝（主要文字）
    val InkSoft = Color(0xFF6A7A99)         // 次级文字

    // 警示（柔克制橙）
    val WarnLight = Color(0xFFE3725D)
    val WarnDark = Color(0xFFFFA68A)

    // 液态背景渐变锚点（冰川蓝系）
    val BgGradLight = listOf(
        Color(0xFFE6F0FF),   // 浅冰
        Color(0xFFD9E4FB),   // 雾蓝
        Color(0xFFEEF6F2),   // 一抹青绿
        Color(0xFFE0EAFA)    // 银蓝
    )
    val BgGradDark = listOf(
        Color(0xFF0E1626),
        Color(0xFF101F37),
        Color(0xFF0E2230),
        Color(0xFF12182A)
    )

    // 玻璃卡：雾白半透 + 冰川蓝细描边
    val GlassLightFill = Color(0xF2F8FAFF)
    val GlassLightStroke = Color(0x335B8CFF)
    val GlassLightHighlight = Color(0xCCFFFFFF)
    val GlassDarkFill = Color(0xCC1A2438)
    val GlassDarkStroke = Color(0x335B8CFF)
    val GlassDarkHighlight = Color(0x33FFFFFF)
}

private val LightColors = lightColorScheme(
    primary = AppPalette.PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = AppPalette.SilverBlue,
    onPrimaryContainer = AppPalette.PrimaryDeep,
    secondary = AppPalette.Accent,
    onSecondary = Color(0xFF003B30),
    secondaryContainer = Color(0xFFDFF3EC),
    onSecondaryContainer = Color(0xFF12342B),
    error = AppPalette.WarnLight,
    onError = Color.White,
    background = AppPalette.FogWhite,
    onBackground = AppPalette.DeepInk,
    surface = Color.White,
    onSurface = AppPalette.DeepInk,
    surfaceVariant = AppPalette.SilverBlue,
    onSurfaceVariant = AppPalette.InkSoft,
    outline = Color(0xFFB6C5DD)
)

private val DarkColors = darkColorScheme(
    primary = AppPalette.PrimaryDark,
    onPrimary = Color(0xFF0F1B33),
    primaryContainer = Color(0xFF1F3266),
    onPrimaryContainer = AppPalette.SilverBlue,
    secondary = AppPalette.AccentDark,
    onSecondary = Color(0xFF002B22),
    error = AppPalette.WarnDark,
    onError = Color(0xFF3A0E04),
    background = Color(0xFF0A1020),
    onBackground = Color(0xFFE8EEFB),
    surface = Color(0xFF11192C),
    onSurface = Color(0xFFE8EEFB),
    surfaceVariant = Color(0xFF1A2238),
    onSurfaceVariant = Color(0xFFB7C2DA),
    outline = Color(0xFF3D4A66)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
)

@Composable
fun AppLimiterTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
