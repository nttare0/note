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
    primary = ExcalidrawIndigo,
    onPrimary = PureWhite,
    primaryContainer = ExcalidrawActiveFillLight,
    onPrimaryContainer = ExcalidrawDeepViolet,
    secondary = ExcalidrawDeepViolet,
    onSecondary = PureWhite,
    secondaryContainer = WhiteboardPillLight,
    onSecondaryContainer = ExcalidrawDeepViolet,
    tertiary = ExcalidrawMutedInkLight,
    onTertiary = PureWhite,
    background = WhiteboardCanvasLight,
    onBackground = ExcalidrawInkLight,
    surface = WhiteboardIslandLight,
    onSurface = ExcalidrawInkLight,
    surfaceVariant = WhiteboardPillLight,
    onSurfaceVariant = ExcalidrawMutedInkLight,
    outline = ExcalidrawBorderLight,
    outlineVariant = ExcalidrawSubtleBorderLight,
    error = DestructiveLight,
    onError = PureWhite
)

private val DarkColorScheme = darkColorScheme(
    primary = ExcalidrawMaximumPurple,
    onPrimary = WhiteboardCanvasDark,
    primaryContainer = ExcalidrawActiveFillDark,
    onPrimaryContainer = ExcalidrawInkDark,
    secondary = ExcalidrawMaximumPurple,
    onSecondary = WhiteboardCanvasDark,
    secondaryContainer = WhiteboardPillDark,
    onSecondaryContainer = ExcalidrawInkDark,
    tertiary = ExcalidrawMutedInkDark,
    onTertiary = WhiteboardCanvasDark,
    background = WhiteboardCanvasDark,
    onBackground = ExcalidrawInkDark,
    surface = WhiteboardIslandDark,
    onSurface = ExcalidrawInkDark,
    surfaceVariant = WhiteboardPillDark,
    onSurfaceVariant = ExcalidrawMutedInkDark,
    outline = ExcalidrawBorderDark,
    outlineVariant = ExcalidrawSubtleBorderDark,
    error = DestructiveDark,
    onError = WhiteboardCanvasDark
)

// Excalidraw floating island & tool pill corner geometry
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
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
