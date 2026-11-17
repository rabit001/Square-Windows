package dev.lelonio.square.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Square's original neutral palette and artwork-led accent system, adapted
 * from the Android theme without Android Configuration or LocalView APIs.
 */
object SquareColors {
    val DarkBase = Color(0xFF0D0E11)
    val LightBase = Color(0xFFF1F2F6)
    val DarkInk = Color(0xFFF7F8FA)
    val LightInk = Color(0xFF15161A)
    val DarkFilm = Color.White.copy(alpha = 0.10f)
    val LightFilm = Color.White.copy(alpha = 0.55f)
    val DefaultAccent = Color(0xFF7C5CE6)
    val MutedDark = Color(0xFFA5A6AF)
    val MutedLight = Color(0xFF686A73)
    val BorderDark = Color.White.copy(alpha = 0.10f)
    val BorderLight = Color.Black.copy(alpha = 0.09f)
}

private val InstrumentSerif = FontFamily.Serif

private fun squareTypography() = Typography(
    displayLarge = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InstrumentSerif,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 29.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.8).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 23.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
    ),
)

@Composable
fun SquareDesktopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = remember(darkTheme) {
        if (darkTheme) {
            darkColorScheme(
                primary = SquareColors.DefaultAccent,
                onPrimary = Color.White,
                background = SquareColors.DarkBase,
                onBackground = SquareColors.DarkInk,
                surface = Color(0xFF17181D),
                onSurface = SquareColors.DarkInk,
                surfaceVariant = Color(0xFF202127),
                onSurfaceVariant = SquareColors.MutedDark,
                outline = SquareColors.BorderDark,
                outlineVariant = SquareColors.BorderDark,
            )
        } else {
            lightColorScheme(
                primary = SquareColors.DefaultAccent,
                onPrimary = Color.White,
                background = SquareColors.LightBase,
                onBackground = SquareColors.LightInk,
                surface = Color(0xFFF8F8FA),
                onSurface = SquareColors.LightInk,
                surfaceVariant = Color(0xFFEDEEF2),
                onSurfaceVariant = SquareColors.MutedLight,
                outline = SquareColors.BorderLight,
                outlineVariant = SquareColors.BorderLight,
            )
        }
    }

    val animatedAccent by animateColorAsState(
        targetValue = scheme.primary,
        animationSpec = tween(450),
        label = "squareAccent",
    )
    val animatedScheme = remember(scheme, animatedAccent) {
        scheme.copy(primary = animatedAccent)
    }

    MaterialTheme(
        colorScheme = animatedScheme,
        typography = squareTypography(),
        content = content,
    )
}

@Composable
fun squareInk(darkTheme: Boolean): Color =
    if (darkTheme) SquareColors.DarkInk else SquareColors.LightInk

@Composable
fun squareMuted(darkTheme: Boolean): Color =
    if (darkTheme) SquareColors.MutedDark else SquareColors.MutedLight
