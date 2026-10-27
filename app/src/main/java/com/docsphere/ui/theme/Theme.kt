package com.docsphere.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DocSphereDarkColorScheme = darkColorScheme(
    primary = ElectricBlue,
    onPrimary = TextPrimary,
    primaryContainer = NavySurface,
    onPrimaryContainer = ElectricBlueLight,

    secondary = TealEmerald,
    onSecondary = NavyDeep,
    secondaryContainer = TealDim,
    onSecondaryContainer = TealLight,

    tertiary = AmberGold,
    onTertiary = NavyDeep,
    tertiaryContainer = AmberDim,
    onTertiaryContainer = AmberLight,

    background = NavyDeep,
    onBackground = TextPrimary,

    surface = NavyDark,
    onSurface = TextPrimary,
    surfaceVariant = NavyMedium,
    onSurfaceVariant = TextSecondary,

    error = ErrorRed,
    onError = TextPrimary,
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),

    outline = GlassBorder,
    outlineVariant = GlassSurface,
    scrim = Color(0xFF000000),
    inverseSurface = TextPrimary,
    inverseOnSurface = NavyDeep,
    inversePrimary = ElectricBlueDim,
)

@Composable
fun DocsphereTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DocSphereDarkColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = GoogleBackgroundDark.toArgb()
            window.navigationBarColor = GoogleSurfaceDark.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DocSphereTypography,
        content = content
    )
}