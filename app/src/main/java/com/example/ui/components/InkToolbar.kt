package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import kotlin.math.abs

private val PaletteKeys = listOf("INK", "AMBER", "SLATE", "BLUE", "GREEN", "CORAL")

/**
 * Clean, minimal drawing canvas toolbar allowing users to toggle between Pen, Marker, Eraser,
 * and Lasso Selection modes, transform selected handwriting or canvas images, switch pen colors,
 * adjust line thickness, undo/redo strokes, and save the canvas as a PNG to the device gallery.
 */
@Composable
fun DrawingCanvasToolbar(
    activeTool: InkTool,
    activeColorKey: String,
    activeStrokeWidth: Float,
    canUndo: Boolean,
    canRedo: Boolean,
    canClear: Boolean = canUndo,
    isGridEnabled: Boolean = false,
    lassoSelectedCount: Int = 0,
    isDarkTheme: Boolean,
    onSelectTool: (InkTool) -> Unit,
    onSelectColor: (String) -> Unit,
    onSelectWidth: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
    onToggleGrid: (() -> Unit)? = null,
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.large)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("drawing_canvas_toolbar")
    ) {
        // Top Row: Mode Switcher (Pen | Marker | Eraser | Lasso) + Grid / Undo / Redo / Save PNG / Clear
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

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onToggleGrid != null) {
                    GridBackgroundTogglePill(
                        isGridEnabled = isGridEnabled,
                        onToggleGrid = onToggleGrid
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(
                    onClick = onUndo,
                    enabled = canUndo,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("ink_undo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Undo,
                        contentDescription = stringResource(R.string.tool_undo_desc),
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onRedo,
                    enabled = canRedo,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("ink_redo_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Redo,
                        contentDescription = stringResource(R.string.tool_redo_desc),
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (onSavePng != null) {
                    IconButton(
                        onClick = onSavePng,
                        modifier = Modifier
                            .size(48.dp)
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

        // Bottom Row: Contextual controls (Pen/Marker Swatches, Eraser Radii, or Lasso Transform Bar)
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
                        activeStrokeWidth = activeStrokeWidth,
                        isDarkTheme = isDarkTheme,
                        onSelectColor = onSelectColor,
                        onSelectWidth = onSelectWidth
                    )
                }
            }
        }
    }
}

@Composable
private fun ColorAndThicknessRow(
    activeTool: InkTool,
    activeColorKey: String,
    activeStrokeWidth: Float,
    isDarkTheme: Boolean,
    onSelectColor: (String) -> Unit,
    onSelectWidth: (Float) -> Unit
) {
    val activeInkColor = resolveInkColor(activeColorKey, isDarkTheme)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Color Swatch Toggles (48dp accessible touch targets)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PaletteKeys.forEach { key ->
                val swatchColor = resolveInkColor(key, isDarkTheme)
                val isSelected = key == activeColorKey
                val desc = stringResource(R.string.color_swatch_desc, key.lowercase())

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { onSelectColor(key) }
                        .semantics { contentDescription = desc }
                        .testTag("ink_color_$key")
                ) {
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 26.dp else 20.dp)
                            .clip(CircleShape)
                            .background(swatchColor)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                                shape = CircleShape
                            )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Line Thickness Toggles with visual stroke preview
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
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.background
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
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
                        color = activeInkColor
                    )
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ThicknessPreviewIndicator(
    strokeWidthDp: Float,
    isHighlighter: Boolean,
    color: Color
) {
    val previewStroke = if (isHighlighter) {
        (strokeWidthDp * 0.42f).coerceIn(3f, 10f)
    } else {
        strokeWidthDp.coerceIn(1.8f, 7f)
    }
    Canvas(modifier = Modifier.size(width = 16.dp, height = 14.dp)) {
        drawLine(
            color = if (isHighlighter) color.copy(alpha = 0.55f) else color,
            start = Offset(2.dp.toPx(), size.height / 2f),
            end = Offset(size.width - 2.dp.toPx(), size.height / 2f),
            strokeWidth = previewStroke.dp.toPx(),
            cap = StrokeCap.Round
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
                            if (selected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.background
                        )
                        .border(
                            width = 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
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
                            color = ringColor,
                            radius = (iconDiameter / 2f).dp.toPx(),
                            style = Stroke(width = 1.4.dp.toPx())
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
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
    val bg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .border(
                width = 1.dp,
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
