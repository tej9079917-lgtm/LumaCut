package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ColorTokensOutlineSubtle = androidx.compose.ui.graphics.Color(0xFF20242E)

private val LumaCutDarkColorScheme = darkColorScheme(
    primary = CinemaAmber,
    onPrimary = OnAmber,
    primaryContainer = AmberContainer,
    onPrimaryContainer = AmberBright,
    secondary = SilverMuted,
    onSecondary = ObsidianBg,
    secondaryContainer = ActiveRail,
    onSecondaryContainer = ChalkWhite,
    tertiary = TrackOverlayEmerald,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = ChalkWhite,
    surface = StudioSlate,
    onSurface = ChalkWhite,
    surfaceVariant = InspectorCharcoal,
    onSurfaceVariant = SilverMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = StudioSlate,
    surfaceContainer = StudioSlate,
    surfaceContainerHigh = InspectorCharcoal,
    surfaceContainerHighest = ActiveRail,
    outline = BorderHairline,
    outlineVariant = ColorTokensOutlineSubtle,
    error = ErrorCrimson,
    onError = ChalkWhite
)

@Composable
fun LumaCutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LumaCutDarkColorScheme,
        typography = LumaCutTypography,
        content = content
    )
}
