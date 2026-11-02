package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = NoteAmber,
    onPrimary = PureWhite,
    primaryContainer = NoteAmberSubtleLight,
    onPrimaryContainer = Zinc900,
    secondary = Zinc800,
    onSecondary = PureWhite,
    secondaryContainer = Zinc100,
    onSecondaryContainer = Zinc900,
    tertiary = Zinc600,
    onTertiary = PureWhite,
    background = Zinc50,
    onBackground = Zinc900,
    surface = PureWhite,
    onSurface = Zinc900,
    surfaceVariant = Zinc100,
    onSurfaceVariant = Zinc500,
    outline = Zinc200,
    outlineVariant = Zinc200,
    error = DestructiveLight,
    onError = PureWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = NoteAmberDark,
    onPrimary = Zinc950,
    primaryContainer = NoteAmberSubtleDark,
    onPrimaryContainer = Zinc50,
    secondary = Zinc200,
    onSecondary = Zinc900,
    secondaryContainer = Zinc800,
    onSecondaryContainer = Zinc100,
    tertiary = Zinc300,
    onTertiary = Zinc900,
    background = Zinc950,
    onBackground = Zinc50,
    surface = Zinc900,
    onSurface = Zinc50,
    surfaceVariant = Zinc800,
    onSurfaceVariant = Zinc500,
    outline = Zinc800,
    outlineVariant = Zinc800,
    error = DestructiveDark,
    onError = Zinc950
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun NoteStudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
