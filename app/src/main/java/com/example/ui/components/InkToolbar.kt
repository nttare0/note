package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AutoFixNormal
import androidx.compose.material.icons.outlined.BorderColor
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.InkTool
import com.example.data.StrokePoint
import com.example.util.InkPaletteHelper
import kotlin.math.abs

/**
 * Clean, minimal drawing canvas toolbar allowing users to toggle between Pen, Marker, Eraser,
 * and Lasso Selection modes, customize their ink color palette, adjust line thickness and stylus
 * pressure, undo/redo canvas actions cleanly, and save the canvas as a PNG to the device gallery.
 */
@Composable
fun DrawingCanvasToolbar(
    activeTool: InkTool,
    activeColorKey: String,
    activeStrokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    undoCount: Int = 0,
    redoCount: Int = 0,
    canClear: Boolean = canUndo,
    customColorKeys: List<String> = emptyList(),
    isGridEnabled: Boolean = false,
    isPressureSensitive: Boolean = true,
    lassoSelectedCount: Int = 0,
    isDarkTheme: Boolean,
    onSelectTool: (InkTool) -> Unit,
    onSelectColor: (String) -> Unit,
    onAddCustomColor: ((String) -> Unit)? = null,
    onRemoveCustomColor: ((String) -> Unit)? = null,
    onSelectWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onToggleGrid: (() -> Unit)? = null,
    onTogglePressureSensitivity: (() -> Unit)? = null,
    onSavePng: (() -> Unit)? = null,
    onRotateSelectionLeft: (() -> Unit)? = null,
    onRotateSelectionRight: (() -> Unit)? = null,
    onScaleSelectionDown: (() -> Unit)? = null,
    onScaleSelectionUp: (() -> Unit)? = null,
    onDeleteSelection: (() -> Unit)? = null,
    onClearSelection: (() -> Unit)? = null,
    onAddCanvasImage: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showColorPickerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = MaterialTheme.shapes.large,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                spotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)
            )
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("drawing_canvas_toolbar")
    ) {
        // Top Row: Mode Switcher (Pen | Marker | Eraser | Lasso) + Island Divider + Undo/Redo & Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ToolModePill(
                    icon = Icons.Outlined.Edit,
                    label = stringResource(R.string.tool_fountain),
                    selected = activeTool == InkTool.PEN,
                    onClick = { onSelectTool(InkTool.PEN) },
                    tag = "tool_pen_button"
                )
                ToolModePill(
                    icon = Icons.Outlined.BorderColor,
                    label = stringResource(R.string.tool_highlighter),
                    selected = activeTool == InkTool.MARKER,
                    onClick = { onSelectTool(InkTool.MARKER) },
                    tag = "tool_marker_button"
                )
                ToolModePill(
                    icon = Icons.Outlined.AutoFixNormal,
                    label = stringResource(R.string.tool_eraser),
                    selected = activeTool == InkTool.ERASER,
                    onClick = { onSelectTool(InkTool.ERASER) },
                    tag = "tool_eraser_button"
                )
                ToolModePill(
                    icon = Icons.Outlined.Gesture,
                    label = stringResource(R.string.tool_lasso),
                    selected = activeTool == InkTool.LASSO,
                    onClick = { onSelectTool(InkTool.LASSO) },
                    tag = "tool_lasso_button"
                )
            }

            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
            Spacer(modifier = Modifier.width(10.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onToggleGrid != null) {
                    GridBackgroundTogglePill(
                        isGridEnabled = isGridEnabled,
                        onToggleGrid = onToggleGrid
                    )
                }

                // Minimal Excalidraw Undo / Redo History Island
                UndoRedoHistoryIsland(
                    canUndo = canUndo,
                    canRedo = canRedo,
                    undoCount = undoCount,
                    redoCount = redoCount,
                    onUndo = onUndo,
                    onRedo = onRedo
                )

                if (onSavePng != null) {
                    IconButton(
                        onClick = onSavePng,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .testTag("ink_export_png_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = stringResource(R.string.png_export_desc),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                IconButton(
                    onClick = onClear,
                    enabled = canClear,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .testTag("ink_clear_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = stringResource(R.string.tool_clear_canvas),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom Row: Contextual controls (Pen/Marker Swatches + Custom Palette, Eraser Radii, or Lasso Bar)
        AnimatedContent(
            targetState = activeTool,
            transitionSpec = {
                fadeIn(tween(150)) togetherWith fadeOut(tween(150))
            },
            label = "toolbar_secondary_controls"
        ) { currentTool ->
            when (currentTool) {
                InkTool.ERASER -> {
                    EraserSizeRow(
                        activeEraserRadius = activeStrokeWidth,
                        onSelectRadius = onSelectWidth
                    )
                }
                InkTool.LASSO -> {
                    LassoControlsRow(
                        selectedCount = lassoSelectedCount,
                        activeColorKey = activeColorKey,
                        customColorKeys = customColorKeys,
                        isDarkTheme = isDarkTheme,
                        onSelectColor = onSelectColor,
                        onOpenColorPicker = { showColorPickerDialog = true },
                        onRotateLeft = onRotateSelectionLeft,
                        onRotateRight = onRotateSelectionRight,
                        onScaleDown = onScaleSelectionDown,
                        onScaleUp = onScaleSelectionUp,
                        onDeleteSelection = onDeleteSelection,
                        onClearSelection = onClearSelection,
                        onAddCanvasImage = onAddCanvasImage
                    )
                }
                InkTool.PEN, InkTool.MARKER -> {
                    ColorAndThicknessRow(
                        activeTool = currentTool,
                        activeColorKey = activeColorKey,
                        customColorKeys = customColorKeys,
                        activeStrokeWidth = activeStrokeWidth,
                        isPressureSensitive = isPressureSensitive,
                        isDarkTheme = isDarkTheme,
                        onSelectColor = onSelectColor,
                        onRemoveCustomColor = onRemoveCustomColor,
                        onOpenColorPicker = { showColorPickerDialog = true },
                        onSelectWidth = onSelectWidth,
                        onTogglePressureSensitivity = onTogglePressureSensitivity
                    )
                }
            }
        }
    }

    if (showColorPickerDialog) {
        InkColorPickerDialog(
            initialColorKey = activeColorKey,
            savedCustomColors = customColorKeys,
            onConfirmCustomColor = { chosenHex ->
                onAddCustomColor?.invoke(chosenHex)
                onSelectColor(chosenHex)
                showColorPickerDialog = false
            },
            onRemoveCustomColor = onRemoveCustomColor,
            onDismiss = { showColorPickerDialog = false }
        )
    }
}

@Composable
private fun UndoRedoHistoryIsland(
    canUndo: Boolean,
    canRedo: Boolean,
    undoCount: Int,
    redoCount: Int,
    onUndo: () -> Unit,
    onRedo: () -> Unit
) {
    val undoDesc = stringResource(R.string.tool_undo_desc)
    val redoDesc = stringResource(R.string.tool_redo_desc)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.background)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .testTag("undo_redo_island")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .height(48.dp)
                .clickable(enabled = canUndo, onClick = onUndo)
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = undoDesc }
                .testTag("ink_undo_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Undo,
                contentDescription = null,
                tint = if (canUndo) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(18.dp)
            )
            if (undoCount > 0) {
                Text(
                    text = undoCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(22.dp)
                .background(MaterialTheme.colorScheme.outline)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .height(48.dp)
                .clickable(enabled = canRedo, onClick = onRedo)
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = redoDesc }
                .testTag("ink_redo_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Redo,
                contentDescription = null,
                tint = if (canRedo) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                modifier = Modifier.size(18.dp)
            )
            if (redoCount > 0) {
                Text(
                    text = redoCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ColorAndThicknessRow(
    activeTool: InkTool,
    activeColorKey: String,
    customColorKeys: List<String>,
    activeStrokeWidth: Float,
    isPressureSensitive: Boolean,
    isDarkTheme: Boolean,
    onSelectColor: (String) -> Unit,
    onRemoveCustomColor: ((String) -> Unit)?,
    onOpenColorPicker: () -> Unit,
    onSelectWidth: (Float) -> Unit,
    onTogglePressureSensitivity: (() -> Unit)?
) {
    val activeInkColor = resolveInkColor(activeColorKey, isDarkTheme)
    val allPaletteKeys = remember(customColorKeys, activeColorKey) {
        val base = InkPaletteHelper.DEFAULT_PALETTE_KEYS + customColorKeys
        if (activeColorKey !in base && InkPaletteHelper.isCustomHexKey(activeColorKey)) {
            base + activeColorKey
        } else {
            base.distinct()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Color Swatch Toggles + Customizable Palette Button (48dp accessible touch targets)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allPaletteKeys.forEach { key ->
                val swatchColor = resolveInkColor(key, isDarkTheme)
                val isSelected = key.equals(activeColorKey, ignoreCase = true)
                val isCustom = InkPaletteHelper.isCustomHexKey(key)
                val desc = stringResource(R.string.color_swatch_desc, key.lowercase())
                val tagSuffix = key.removePrefix("#")

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = { onSelectColor(key) },
                            onLongClick = if (isCustom && onRemoveCustomColor != null) {
                                { onRemoveCustomColor(key) }
                            } else {
                                null
                            }
                        )
                        .semantics { contentDescription = desc }
                        .testTag("ink_color_$tagSuffix")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(if (isSelected) 30.dp else 22.dp)
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .padding(if (isSelected) 3.dp else 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }
            }

            // Minimal Customize Color Palette Trigger Button
            val customizeDesc = stringResource(R.string.palette_customize_desc)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onOpenColorPicker)
                    .semantics { contentDescription = customizeDesc }
                    .testTag("open_color_palette_picker_button")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Line Thickness Toggles with visual stroke preview + Stylus Pressure Toggle
        val presets = if (activeTool == InkTool.MARKER) {
            listOf(
                10f to R.string.stroke_fine,
                16f to R.string.stroke_medium,
                24f to R.string.stroke_bold
            )
        } else {
            listOf(
                1.8f to R.string.stroke_fine,
                3.4f to R.string.stroke_medium,
                6.0f to R.string.stroke_bold
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            presets.forEach { (widthVal, labelRes) ->
                val selected = abs(activeStrokeWidth - widthVal) < 0.6f
                val labelText = stringResource(labelRes)
                val desc = stringResource(R.string.thickness_preset_desc, labelText)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.background
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.small
                        )
                        .clickable { onSelectWidth(widthVal) }
                        .padding(horizontal = 10.dp)
                        .semantics { contentDescription = desc }
                        .testTag("stroke_thickness_${labelText.lowercase()}")
                ) {
                    ThicknessPreviewIndicator(
                        strokeWidthDp = widthVal,
                        isHighlighter = activeTool == InkTool.MARKER,
                        isPressureSensitive = isPressureSensitive,
                        color = activeInkColor
                    )
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (onTogglePressureSensitivity != null) {
                PressureSensitivityTogglePill(
                    isPressureSensitive = isPressureSensitive,
                    onToggle = onTogglePressureSensitivity
                )
            }
        }
    }
}

@Composable
private fun ThicknessPreviewIndicator(
    strokeWidthDp: Float,
    isHighlighter: Boolean,
    isPressureSensitive: Boolean,
    color: Color
) {
    val previewStroke = if (isHighlighter) {
        (strokeWidthDp * 0.42f).coerceIn(3f, 10f)
    } else {
        strokeWidthDp.coerceIn(1.8f, 6.2f)
    }
    Canvas(modifier = Modifier.size(width = 18.dp, height = 14.dp)) {
        if (isPressureSensitive && !isHighlighter) {
            drawSmoothStroke(
                points = listOf(
                    StrokePoint(x = 2f, y = 10.5f, pressure = 0.14f),
                    StrokePoint(x = 9f, y = 4.0f, pressure = 0.92f),
                    StrokePoint(x = 16f, y = 9.0f, pressure = 0.16f)
                ),
                color = color,
                widthDp = previewStroke * 0.85f,
                isHighlighter = false,
                isStylus = true
            )
        } else {
            drawLine(
                color = if (isHighlighter) color.copy(alpha = 0.55f) else color,
                start = Offset(2.dp.toPx(), size.height / 2f),
                end = Offset(size.width - 2.dp.toPx(), size.height / 2f),
                strokeWidth = previewStroke.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun PressureSensitivityTogglePill(
    isPressureSensitive: Boolean,
    onToggle: () -> Unit
) {
    val bg = if (isPressureSensitive) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.background
    }
    val borderCol = if (isPressureSensitive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val fg = if (isPressureSensitive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val desc = stringResource(
        if (isPressureSensitive) R.string.tool_pressure_disable_desc
        else R.string.tool_pressure_enable_desc
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .border(width = 1.dp, color = borderCol, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp)
            .semantics { contentDescription = desc }
            .testTag("pressure_sensitivity_toggle_button")
    ) {
        Canvas(modifier = Modifier.size(width = 15.dp, height = 14.dp)) {
            drawSmoothStroke(
                points = listOf(
                    StrokePoint(x = 1.5f, y = 11f, pressure = if (isPressureSensitive) 0.12f else 1f),
                    StrokePoint(x = 7.5f, y = 3.5f, pressure = if (isPressureSensitive) 0.96f else 1f),
                    StrokePoint(x = 13.5f, y = 9.5f, pressure = if (isPressureSensitive) 0.14f else 1f)
                ),
                color = fg,
                widthDp = 2.4f,
                isHighlighter = false,
                isStylus = isPressureSensitive
            )
        }
        Text(
            text = stringResource(R.string.tool_pressure_label),
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}

@Composable
private fun EraserSizeRow(
    activeEraserRadius: Float,
    onSelectRadius: (Float) -> Unit
) {
    val eraserPresets = listOf(
        10f to R.string.eraser_small,
        18f to R.string.eraser_medium,
        30f to R.string.eraser_large
    )
    val ringColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.eraser_radius_label),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, end = 12.dp)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            eraserPresets.forEach { (radiusDp, labelRes) ->
                val selected = abs(activeEraserRadius - radiusDp) < 1.5f
                val label = stringResource(labelRes)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.background
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline,
                            shape = MaterialTheme.shapes.small
                        )
                        .clickable { onSelectRadius(radiusDp) }
                        .padding(horizontal = 12.dp)
                        .testTag("eraser_size_${label.lowercase()}")
                ) {
                    val iconDiameter = (radiusDp * 0.55f).coerceIn(6f, 16f)
                    Canvas(modifier = Modifier.size(18.dp)) {
                        drawCircle(
                            color = if (selected) ringColor else ringColor.copy(alpha = 0.7f),
                            radius = (iconDiameter / 2f).dp.toPx(),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolModePill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

@Composable
private fun GridBackgroundTogglePill(
    isGridEnabled: Boolean,
    onToggleGrid: () -> Unit
) {
    val bg = if (isGridEnabled) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.background
    }
    val borderColor = if (isGridEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val fg = if (isGridEnabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val desc = stringResource(
        if (isGridEnabled) R.string.tool_grid_disable_desc else R.string.tool_grid_enable_desc
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .border(width = 1.dp, color = borderColor, shape = MaterialTheme.shapes.small)
            .clickable(onClick = onToggleGrid)
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = desc }
            .testTag("canvas_grid_toggle_button")
    ) {
        Icon(
            imageVector = if (isGridEnabled) Icons.Outlined.GridOn else Icons.Outlined.GridOff,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = stringResource(R.string.tool_grid_toggle_label),
            style = MaterialTheme.typography.labelMedium,
            color = fg
        )
    }
}

@Composable
private fun LassoControlsRow(
    selectedCount: Int,
    activeColorKey: String,
    customColorKeys: List<String>,
    isDarkTheme: Boolean,
    onSelectColor: (String) -> Unit,
    onOpenColorPicker: () -> Unit,
    onRotateLeft: (() -> Unit)?,
    onRotateRight: (() -> Unit)?,
    onScaleDown: (() -> Unit)?,
    onScaleUp: (() -> Unit)?,
    onDeleteSelection: (() -> Unit)?,
    onClearSelection: (() -> Unit)?,
    onAddCanvasImage: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (selectedCount <= 0) {
            Text(
                text = stringResource(R.string.lasso_hint_draw),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp, end = 12.dp)
                    .testTag("lasso_empty_hint")
            )

            if (onAddCanvasImage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable(onClick = onAddCanvasImage)
                        .padding(horizontal = 12.dp)
                        .testTag("lasso_add_canvas_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AddPhotoAlternate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.lasso_add_canvas_image),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            Text(
                text = stringResource(R.string.lasso_selected_count_format, selectedCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(start = 4.dp, end = 12.dp)
                    .testTag("lasso_selected_badge")
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Quick recolor swatches for selected strokes + Palette button
                val lassoSwatches = (InkPaletteHelper.DEFAULT_PALETTE_KEYS + customColorKeys).distinct()
                lassoSwatches.forEach { key ->
                    val swatchColor = resolveInkColor(key, isDarkTheme)
                    val isSelected = key.equals(activeColorKey, ignoreCase = true)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable { onSelectColor(key) }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 22.dp else 18.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                        )
                    }
                }
                IconButton(
                    onClick = onOpenColorPicker,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = stringResource(R.string.palette_customize_desc),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (onRotateLeft != null) {
                    IconButton(
                        onClick = onRotateLeft,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .testTag("lasso_rotate_left_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RotateLeft,
                            contentDescription = stringResource(R.string.lasso_rotate_left_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onRotateRight != null) {
                    IconButton(
                        onClick = onRotateRight,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .testTag("lasso_rotate_right_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.RotateRight,
                            contentDescription = stringResource(R.string.lasso_rotate_right_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onScaleDown != null) {
                    IconButton(
                        onClick = onScaleDown,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .testTag("lasso_scale_down_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ZoomOut,
                            contentDescription = stringResource(R.string.lasso_scale_down_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onScaleUp != null) {
                    IconButton(
                        onClick = onScaleUp,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .testTag("lasso_scale_up_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ZoomIn,
                            contentDescription = stringResource(R.string.lasso_scale_up_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onDeleteSelection != null) {
                    IconButton(
                        onClick = onDeleteSelection,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .testTag("lasso_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = stringResource(R.string.lasso_delete_selection_desc),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                if (onClearSelection != null) {
                    IconButton(
                        onClick = onClearSelection,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.background)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .testTag("lasso_deselect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.lasso_clear_selection_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InkToolbar(
    activeTool: InkTool,
    activeColorKey: String,
    activeStrokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    isDarkTheme: Boolean,
    onSelectTool: (InkTool) -> Unit,
    onSelectColor: (String) -> Unit,
    onSelectWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    DrawingCanvasToolbar(
        activeTool = activeTool,
        activeColorKey = activeColorKey,
        activeStrokeWidth = activeStrokeWidth,
        canUndo = canUndo,
        canRedo = canRedo,
        canClear = canUndo,
        isDarkTheme = isDarkTheme,
        onSelectTool = onSelectTool,
        onSelectColor = onSelectColor,
        onSelectWidth = onSelectWidth,
        onUndo = onUndo,
        onRedo = onRedo,
        onClear = onClear,
        modifier = modifier
    )
}
