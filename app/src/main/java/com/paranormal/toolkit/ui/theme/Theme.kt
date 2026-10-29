package com.paranormal.toolkit.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ParanormalColorScheme = darkColorScheme(
    primary = SpectralGreen,
    onPrimary = VoidBlack,
    secondary = SpectralGreenDim,
    background = VoidBlack,
    onBackground = TextPrimary,
    surface = PanelDark,
    onSurface = TextPrimary,
    surfaceVariant = PanelDarkAlt,
    onSurfaceVariant = TextSecondary,
    error = AlertRed,
)

@Composable
fun ParanormalToolkitTheme(content: @Composable () -> Unit) {
    // The app always runs in a dark, "field kit at night" theme regardless
    // of system setting - readability in the dark matters more here than
    // following the OS light/dark preference.
    MaterialTheme(
        colorScheme = ParanormalColorScheme,
        typography = ParanormalTypography,
        content = content,
    )
}
