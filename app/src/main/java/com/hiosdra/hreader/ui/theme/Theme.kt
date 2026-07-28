package com.hiosdra.hreader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

data class ExtendedColors(
    val cardBackground: Color,
    val divider: Color,
    val author: Color,
    val date: Color,
    val preview: Color,
    val checked: Color,
    val unchecked: Color,
    val header: Color,
    val credibilityHigh: Color,
    val credibilityMixed: Color,
    val credibilityLow: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        cardBackground = Color.Unspecified,
        divider = Color.Unspecified,
        author = Color.Unspecified,
        date = Color.Unspecified,
        preview = Color.Unspecified,
        checked = Color.Unspecified,
        unchecked = Color.Unspecified,
        header = Color.Unspecified,
        credibilityHigh = Color.Unspecified,
        credibilityMixed = Color.Unspecified,
        credibilityLow = Color.Unspecified
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = Blue40,
    secondary = Blue60,
    tertiary = Green40,
    error = Red40,
    background = MainBackground,
    surface = MainSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onError = Color.White,
    onBackground = MainTitle,
    onSurface = MainTitle,
    outline = MainDivider,
    primaryContainer = Blue30,
    secondaryContainer = Blue20,
    tertiaryContainer = Green40,
    surfaceVariant = Gray20
)

private val DarkExtendedColors = ExtendedColors(
    cardBackground = MainCardBackground,
    divider = MainDivider,
    author = MainAuthor,
    date = MainDate,
    preview = MainPreview,
    checked = MainChecked,
    unchecked = MainUnchecked,
    header = MainHeader,
    credibilityHigh = GreenDark,
    credibilityMixed = AmberDark,
    credibilityLow = RedDark
)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    secondary = Blue30,
    tertiary = Green40,
    error = Red40,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightOnPrimary,
    onSecondary = LightOnSecondary,
    onTertiary = LightOnTertiary,
    onError = Color.White,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
    outline = LightOutline,
    primaryContainer = LightPrimaryContainer,
    secondaryContainer = LightSecondaryContainer,
    tertiaryContainer = LightTertiaryContainer,
    surfaceVariant = Gray80
)

private val LightExtendedColors = ExtendedColors(
    cardBackground = LightCardBackground,
    divider = LightDivider,
    author = Blue30,
    date = Gray40,
    preview = Gray30,
    checked = Blue40,
    unchecked = Gray50,
    header = Gray40,
    credibilityHigh = GreenLight,
    credibilityMixed = AmberLight,
    credibilityLow = RedLight
)

@Composable
fun HReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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

    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors

    CompositionLocalProvider(LocalExtendedColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
