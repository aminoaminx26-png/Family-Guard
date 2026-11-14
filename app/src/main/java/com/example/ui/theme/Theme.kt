package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = CobaltPrimaryLight,
    onPrimary = OnCobaltLight,
    primaryContainer = CobaltContainerLight,
    onPrimaryContainer = OnCobaltContainerLight,
    secondary = TealSecondaryLight,
    onSecondary = OnTealLight,
    secondaryContainer = TealContainerLight,
    onSecondaryContainer = OnTealContainerLight,
    tertiary = SkyTertiaryLight,
    onTertiary = OnSkyLight,
    tertiaryContainer = SkyContainerLight,
    onTertiaryContainer = OnSkyContainerLight,
    error = ErrorLight,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight
)

private val DarkColorScheme = darkColorScheme(
    primary = CobaltPrimaryDark,
    onPrimary = OnCobaltDark,
    primaryContainer = CobaltContainerDark,
    onPrimaryContainer = OnCobaltContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = OnTealDark,
    secondaryContainer = TealContainerDark,
    onSecondaryContainer = OnTealContainerDark,
    tertiary = SkyTertiaryDark,
    onTertiary = OnSkyDark,
    tertiaryContainer = SkyContainerDark,
    onTertiaryContainer = OnSkyContainerDark,
    error = ErrorDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark
)

val FamilyGuardShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FamilyGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = FamilyGuardTypography,
        shapes = FamilyGuardShapes,
        content = content
    )
}
