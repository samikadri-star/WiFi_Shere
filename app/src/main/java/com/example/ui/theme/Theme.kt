package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val EmeraldObsidianColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = OnEmeraldPrimaryContainer,
    secondary = EmeraldSecondary,
    onSecondary = OnEmeraldSecondary,
    secondaryContainer = EmeraldSecondaryContainer,
    onSecondaryContainer = OnEmeraldSecondaryContainer,
    tertiary = EmeraldTertiary,
    onTertiary = OnEmeraldTertiary,
    tertiaryContainer = EmeraldTertiaryContainer,
    onTertiaryContainer = OnEmeraldTertiaryContainer,
    error = EmeraldError,
    onError = OnEmeraldError,
    errorContainer = EmeraldErrorContainer,
    onErrorContainer = OnEmeraldErrorContainer,
    background = ObsidianBackground,
    onBackground = ObsidianOnBackground,
    surface = ObsidianSurface,
    onSurface = ObsidianOnSurface,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = ObsidianOnSurfaceVariant,
    surfaceDim = ObsidianSurfaceDim,
    surfaceBright = ObsidianSurfaceBright,
    surfaceContainerLowest = ObsidianSurfaceContainerLowest,
    surfaceContainerLow = ObsidianSurfaceContainerLow,
    surfaceContainer = ObsidianSurfaceContainer,
    surfaceContainerHigh = ObsidianSurfaceContainerHigh,
    surfaceContainerHighest = ObsidianSurfaceContainerHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant,
    inverseSurface = ObsidianInverseSurface,
    inverseOnSurface = ObsidianInverseOnSurface,
    inversePrimary = ObsidianInversePrimary,
)

val WiFiShareShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun WiFiShareTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = EmeraldObsidianColorScheme,
            typography = WiFiShareTypography,
            shapes = WiFiShareShapes,
            content = content,
        )
    }
}
