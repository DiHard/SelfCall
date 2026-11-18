package ru.ifx.selfcall.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Цвета — те же токены, что в frontend/src/index.css (пары контраста там проверены по WCAG).
 * Экран звонка всегда тёмный, остальные следуют теме телефона.
 */
internal object StageColors {
    val background = Color(0xFF101424)
    val surface = Color(0xFF1B223C)
    val controls = Color(0xFF242E4E)
    val pressed = Color(0xFF343F64)
    val mutedControl = Color(0xFF3B4262)
    val border = Color(0xFF364264)
    val text = Color(0xFFF3F5FF)
    val secondaryText = Color(0xFFBDC7E3)
    val accent = Color(0xFFA6B4FF)
    val connected = Color(0xFF70E0B5)
    val accept = Color(0xFF0B6B4C)
    val hangUp = Color(0xFFC62744)
    val waiting = Color(0xFFF2BD7B)
    val scrim = Color(0xD1101424)
    val overVideo = Color(0xE0101424)
}

private val Brand = Color(0xFF3045D8)
private val BrandLightSurface = Color(0xFFE9EEFF)
internal val BrandForeground = Color.White
internal val BrandBackground = Brand

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Brand,
    onPrimaryContainer = Color.White,
    background = Color(0xFFF3F5FC),
    onBackground = Color(0xFF171C32),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171C32),
    surfaceVariant = BrandLightSurface,
    surfaceContainer = BrandLightSurface,
    surfaceContainerHigh = Color(0xFFDCE4FA),
    secondaryContainer = BrandLightSurface,
    onSecondaryContainer = Color(0xFF171C32),
    onSurfaceVariant = Color(0xFF515D76),
    outline = Color(0xFF81899C),
    outlineVariant = Color(0xFFD5DDEC),
    error = Color(0xFFB91E39),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEF),
    onErrorContainer = Color(0xFFA51E37),
)

private val Dark = darkColorScheme(
    primary = StageColors.accent,
    onPrimary = StageColors.background,
    primaryContainer = Color(0xFF4055D8),
    onPrimaryContainer = Color.White,
    background = StageColors.background,
    onBackground = StageColors.text,
    surface = StageColors.surface,
    onSurface = StageColors.text,
    surfaceVariant = StageColors.controls,
    surfaceContainer = StageColors.controls,
    surfaceContainerHigh = StageColors.pressed,
    secondaryContainer = StageColors.controls,
    onSecondaryContainer = StageColors.text,
    onSurfaceVariant = StageColors.secondaryText,
    outline = Color(0xFF838FAE),
    outlineVariant = StageColors.border,
    error = Color(0xFFFFA2B2),
    onError = StageColors.background,
    errorContainer = Color(0xFF472435),
    onErrorContainer = Color(0xFFFFA2B2),
)

class ExtraColors(val dangerText: Color, val accentText: Color, val successText: Color, val successSurface: Color)

private val LightExtra = ExtraColors(Light.onErrorContainer, Brand, StageColors.accept, Color(0xFFDAF4EA))
private val DarkExtra = ExtraColors(Dark.onErrorContainer, StageColors.accent, StageColors.connected, Color(0xFF153B37))
val LocalExtraColors = staticCompositionLocalOf { LightExtra }

private val StandardType = Typography()
private val SelfCallType = StandardType.copy(
    headlineLarge = StandardType.headlineLarge.copy(fontWeight = FontWeight.Bold),
    headlineMedium = StandardType.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = StandardType.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = StandardType.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = StandardType.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = StandardType.labelLarge.copy(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
)

private val SelfCallShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun SelfCallTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    Themed(if (dark) Dark else Light, if (dark) DarkExtra else LightExtra, content)
}

@Composable
fun CallTheme(content: @Composable () -> Unit) = Themed(Dark, DarkExtra, content)

@Composable
private fun Themed(scheme: ColorScheme, extra: ExtraColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = SelfCallType, shapes = SelfCallShapes) {
            // Текст без явного цвета берёт цвет темы, а не чёрный по умолчанию
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}
