package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = SurfaceSubtle,
    onPrimaryContainer = NavyPrimary,
    secondary = EmeraldDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = TealAccent,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCCFBF1),
    onTertiaryContainer = Color(0xFF134E4A),
    background = WarmOffWhite,
    onBackground = TextNavyDark,
    surface = SurfaceWhite,
    onSurface = TextNavyDark,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextSlateMuted,
    outline = OutlineLight,
    error = ErrorRed,
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = RoyalBlueBright,
    onPrimary = Color.White,
    primaryContainer = DarkNavyCard,
    onPrimaryContainer = TextWhitePure,
    secondary = EmeraldPrimary,
    onSecondary = Color(0xFF032218),
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFA7F3D0),
    tertiary = TealBright,
    onTertiary = Color(0xFF042F2E),
    tertiaryContainer = Color(0xFF115E59),
    onTertiaryContainer = Color(0xFF99F6E4),
    background = DarkNavyBackground,
    onBackground = TextWhitePure,
    surface = DarkNavySurface,
    onSurface = TextWhitePure,
    surfaceVariant = DarkNavyCard,
    onSurfaceVariant = TextDarkMuted,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SmartSchoolTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                // Top header is deep navy in both modes, so light status bar icons remain crisp;
                // adjust navigation bar icons according to theme.
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = false
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    SmartSchoolTheme(darkTheme = darkTheme, content = content)
}
