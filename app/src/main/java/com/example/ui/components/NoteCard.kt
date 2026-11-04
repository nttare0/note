package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.NoteEntity
import com.example.data.NoteSerializer
import com.example.data.PaperTemplate
import com.example.util.InkPaletteHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    folderName: String?,
    isDarkTheme: Boolean,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strokes = remember(note.strokesJson) { NoteSerializer.decodeStrokes(note.strokesJson) }
    val checklist = remember(note.checklistJson) { NoteSerializer.decodeChecklist(note.checklistJson) }
    val images = remember(note.imagesJson) { NoteSerializer.decodeImages(note.imagesJson) }
    val template = remember(note.template) {
        runCatching { PaperTemplate.valueOf(note.template) }.getOrDefault(PaperTemplate.BLANK)
    }
    val dateText = remember(note.updatedAt) {
        SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(note.updatedAt))
    }

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        note.isPinned -> MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        else -> MaterialTheme.colorScheme.outline
    }

    val cardBgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDarkTheme) 0.28f else 0.36f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val inkDefault = resolveInkColor("INK", isDarkTheme)
    val inkAmber = resolveInkColor("AMBER", isDarkTheme)
    val inkSlate = resolveInkColor("SLATE", isDarkTheme)
    val inkBlue = resolveInkColor("BLUE", isDarkTheme)
    val inkGreen = resolveInkColor("GREEN", isDarkTheme)
    val inkCoral = resolveInkColor("CORAL", isDarkTheme)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isSelected) 4.dp else 2.dp,
                shape = MaterialTheme.shapes.medium,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                spotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
            )
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .background(cardBgColor)
            .border(
                width = when {
                    isSelected -> 2.dp
                    note.isPinned -> 1.5.dp
                    else -> 1.dp
                },
                color = borderColor,
                shape = MaterialTheme.shapes.medium
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp)
            .testTag("note_item_card_${note.id}")
    ) {
        // Header row: Selection indicator + Pin/Folder badge + Star toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (isSelectionMode || isSelected) {
                    Icon(
                        imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = stringResource(R.string.note_selected_desc),
                        tint = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("note_selected_badge_${note.id}")
                    )
                }
                if (note.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = stringResource(R.string.badge_pinned),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                val tagText = folderName ?: template.labelKey
                BadgePill(
                    text = tagText,
                    highlighted = isSelected || note.isPinned || folderName != null
                )
            }

            if (!note.isTrashed && !isSelectionMode) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("favorite_note_button_${note.id}")
                ) {
                    Icon(
                        imageVector = if (note.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = stringResource(R.string.editor_favorite_desc),
                        tint = if (note.isFavorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title
        Text(
            text = note.title.ifBlank { stringResource(R.string.editor_title_placeholder) },
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Cover Image Thumbnail if note has attached images
        val coverImage = images.firstOrNull()
        if (coverImage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = File(coverImage.localPath),
                contentDescription = stringResource(R.string.note_attached_image_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            )
        }

        // Body snippet
        if (note.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = note.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Miniature Ink Preview (Samsung Notes page thumbnail aesthetic)
        if (strokes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            val lineCol = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPaperTemplate(template, lineCol, lineCol)
                    val scale = 0.42f
                    strokes.forEach { stroke ->
                        val scaled = stroke.points.map {
                            it.copy(x = it.x * scale, y = it.y * scale)
                        }
                        val customArgb = InkPaletteHelper.parseHexRgbToArgbInt(stroke.colorKey)
                        val strokeCol = if (customArgb != null) {
                            Color(customArgb)
                        } else {
                            when (stroke.colorKey) {
                                "AMBER" -> inkAmber
                                "SLATE" -> inkSlate
                                "BLUE" -> inkBlue
                                "GREEN" -> inkGreen
                                "CORAL" -> inkCoral
                                else -> inkDefault
                            }
                        }
                        drawSmoothStroke(
                            points = scaled,
                            color = strokeCol,
                            widthDp = (stroke.widthDp * 0.65f).coerceAtLeast(1.2f),
                            isHighlighter = stroke.isHighlighter,
                            isStylus = stroke.isStylus
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Footer metadata row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dateText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (images.isNotEmpty()) {
                    BadgePill(
                        text = stringResource(R.string.badge_images_format, images.size)
                    )
                }
                if (checklist.isNotEmpty()) {
                    val done = checklist.count { it.isChecked }
                    BadgePill(
                        text = stringResource(R.string.badge_checklist_format, done, checklist.size)
                    )
                }
                if (strokes.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Text(
                            text = stringResource(R.string.badge_ink),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BadgePill(
    text: String,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bg = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg
        )
    }
}
