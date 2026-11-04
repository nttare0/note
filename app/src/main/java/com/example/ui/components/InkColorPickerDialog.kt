package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.StrokePoint
import com.example.util.InkPaletteHelper

/**
 * Minimal, Excalidraw-styled customizable ink color palette dialog.
 * Lets users pick curated shades, craft custom colors via Hue / Richness / Brightness sliders
 * or Hex input, preview a calligraphic stroke live, and manage saved SQLite custom swatches.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InkColorPickerDialog(
    initialColorKey: String,
    savedCustomColors: List<String>,
    onConfirmCustomColor: (String) -> Unit,
    onRemoveCustomColor: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val startHex = remember(initialColorKey) {
        InkPaletteHelper.normalizeHexColorKey(initialColorKey)
            ?: InkPaletteHelper.CURATED_PRESET_HEX_COLORS.first()
    }
    val initialHsv = remember(startHex) {
        InkPaletteHelper.hexKeyToHsv(startHex) ?: Triple(252f, 0.69f, 0.91f)
    }

    var hue by remember { mutableFloatStateOf(initialHsv.first) }
    var saturation by remember { mutableFloatStateOf(initialHsv.second) }
    var brightness by remember { mutableFloatStateOf(initialHsv.third) }
    var hexText by remember { mutableStateOf(startHex) }

    val resolvedHex = remember(hexText, hue, saturation, brightness) {
        InkPaletteHelper.normalizeHexColorKey(hexText)
            ?: InkPaletteHelper.hsvToHexKey(hue, saturation, brightness)
    }
    val previewColor = remember(resolvedHex) {
        val argb = InkPaletteHelper.parseHexRgbToArgbInt(resolvedHex)
        if (argb != null) Color(argb) else Color.Unspecified
    }

    fun applyHexPreset(presetHex: String) {
        val norm = InkPaletteHelper.normalizeHexColorKey(presetHex) ?: return
        hexText = norm
        InkPaletteHelper.hexKeyToHsv(norm)?.let { (h, s, v) ->
            hue = h
            saturation = s
            brightness = v
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.testTag("custom_color_picker_dialog"),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.palette_dialog_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.palette_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Live Handwriting Stroke Preview Box
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.background)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        .padding(horizontal = 14.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                    ) {
                        val wDp = size.width.toDp().value.coerceAtLeast(80f)
                        drawSmoothStroke(
                            points = listOf(
                                StrokePoint(x = 8f, y = 24f, pressure = 0.16f),
                                StrokePoint(x = wDp * 0.34f, y = 8f, pressure = 0.94f),
                                StrokePoint(x = wDp * 0.66f, y = 26f, pressure = 0.56f),
                                StrokePoint(x = wDp - 12f, y = 12f, pressure = 0.18f)
                            ),
                            color = if (previewColor != Color.Unspecified) previewColor else Color.Black,
                            widthDp = 4.2f,
                            isHighlighter = false,
                            isStylus = true
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = resolvedHex,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Curated Shades Grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.palette_presets_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        InkPaletteHelper.CURATED_PRESET_HEX_COLORS.forEach { presetHex ->
                            val argb = InkPaletteHelper.parseHexRgbToArgbInt(presetHex)
                            val swatchCol = if (argb != null) Color(argb) else MaterialTheme.colorScheme.primary
                            val isSelected = resolvedHex.equals(presetHex, ignoreCase = true)

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.background
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .clickable { applyHexPreset(presetHex) }
                                    .testTag("preset_swatch_${presetHex.removePrefix("#")}")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(swatchCol)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.surface,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Custom Hue, Richness & Brightness Controls
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.palette_hue_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = hue,
                        onValueChange = { nextHue ->
                            hue = nextHue
                            hexText = InkPaletteHelper.hsvToHexKey(nextHue, saturation, brightness)
                        },
                        valueRange = 0f..360f,
                        modifier = Modifier.testTag("palette_hue_slider")
                    )

                    Text(
                        text = stringResource(R.string.palette_tone_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = saturation,
                        onValueChange = { nextSat ->
                            saturation = nextSat
                            hexText = InkPaletteHelper.hsvToHexKey(hue, nextSat, brightness)
                        },
                        valueRange = 0.08f..1f,
                        modifier = Modifier.testTag("palette_saturation_slider")
                    )

                    Text(
                        text = stringResource(R.string.palette_brightness_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = brightness,
                        onValueChange = { nextVal ->
                            brightness = nextVal
                            hexText = InkPaletteHelper.hsvToHexKey(hue, saturation, nextVal)
                        },
                        valueRange = 0.15f..1f,
                        modifier = Modifier.testTag("palette_brightness_slider")
                    )
                }

                // Direct Hex Code Input
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { typed ->
                        val capped = typed.take(7)
                        hexText = capped
                        InkPaletteHelper.normalizeHexColorKey(capped)?.let { validHex ->
                            InkPaletteHelper.hexKeyToHsv(validHex)?.let { (h, s, v) ->
                                hue = h
                                saturation = s
                                brightness = v
                            }
                        }
                    },
                    singleLine = true,
                    label = { Text(stringResource(R.string.palette_hex_label)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_hex_color_input")
                )

                // Saved SQLite Custom Swatches Management Row
                if (savedCustomColors.isNotEmpty() && onRemoveCustomColor != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.palette_saved_custom_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            savedCustomColors.forEach { savedKey ->
                                val savedArgb = InkPaletteHelper.parseHexRgbToArgbInt(savedKey)
                                val savedCol = if (savedArgb != null) Color(savedArgb) else MaterialTheme.colorScheme.primary
                                val removeDesc = stringResource(R.string.palette_remove_custom_desc, savedKey)

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .height(48.dp)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.background)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                        .clickable { applyHexPreset(savedKey) }
                                        .padding(start = 10.dp, end = 6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(savedCol)
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    )
                                    Text(
                                        text = savedKey,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable { onRemoveCustomColor(savedKey) }
                                            .semantics { contentDescription = removeDesc }
                                            .testTag("remove_custom_color_${savedKey.removePrefix("#")}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Close,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmCustomColor(resolvedHex) },
                modifier = Modifier.testTag("confirm_add_custom_color_button")
            ) {
                Text(stringResource(R.string.palette_apply_action))
            }
        }
    )
}
