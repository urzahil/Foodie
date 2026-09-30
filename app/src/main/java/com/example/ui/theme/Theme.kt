package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MichelinRed,
    onPrimary = Color.White,
    primaryContainer = MichelinRedDark,
    onPrimaryContainer = Color.White,
    secondary = MichelinGold,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3E2723),
    onSecondaryContainer = MichelinGold,
    tertiary = MichelinAmber,
    onTertiary = Color.Black,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF424242),
    outlineVariant = Color(0xFF2C2C2C),
    surfaceTint = MichelinRed
)

@Composable
fun FoodieTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

// Keep alias for compatibility
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    FoodieTheme(content = content)
}
