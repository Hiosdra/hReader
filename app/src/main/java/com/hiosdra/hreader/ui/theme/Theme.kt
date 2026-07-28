package com.hiosdra.hreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class CredibilityColors(
    val high: Color,
    val highContainer: Color,
    val mixed: Color,
    val mixedContainer: Color,
    val low: Color,
    val lowContainer: Color
)

private val DarkCredibilityColors = CredibilityColors(
    high = Green80,
    highContainer = Green20,
    mixed = Amber80,
    mixedContainer = Amber20,
    low = Red80,
    lowContainer = Red20
)

val LocalCredibilityColors = staticCompositionLocalOf { DarkCredibilityColors }

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue10,
    primaryContainer = Blue20,
    onPrimaryContainer = Blue90,
    inversePrimary = Blue30,
    secondary = BlueGrey80,
    onSecondary = BlueGrey20,
    secondaryContainer = BlueGrey30,
    onSecondaryContainer = BlueGrey90,
    tertiary = Green80,
    onTertiary = Green10,
    tertiaryContainer = Green30,
    onTertiaryContainer = Green90,
    error = Red80,
    onError = Red10,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = Neutral06,
    onBackground = NeutralOn90,
    surface = Neutral06,
    onSurface = NeutralOn90,
    surfaceVariant = Neutral19,
    onSurfaceVariant = NeutralOnVariant80,
    surfaceContainerLowest = Neutral04,
    surfaceContainerLow = Neutral11,
    surfaceContainer = Neutral15,
    surfaceContainerHigh = Neutral19,
    surfaceContainerHighest = Neutral24,
    outline = NeutralOutline60,
    outlineVariant = NeutralOutline30,
    scrim = Color.Black
)

@Composable
fun HReaderTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalCredibilityColors provides DarkCredibilityColors) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
