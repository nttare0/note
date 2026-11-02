package com.example.ui.components

import android.app.Activity
import android.content.ClipDescription
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ChecklistItem
import com.example.data.NoteImageAttachment
import com.example.util.DragDropReorderHelper
import java.io.File

/**
 * Multi-window / Split-screen external Drag-and-Drop container for notes.
 * Accepts dropped images and plain text dragged from external apps
 * (Gallery, Browser, Files) and shows a calm bordered drop-zone overlay while hovering.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExternalNoteDropContainer(
    enabled: Boolean,
    onDropImageUri: (Uri) -> Unit,
    onDropText: (String) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var isExternalDragHovering by remember { mutableStateOf(false) }

    val latestDropImage by rememberUpdatedState(onDropImageUri)
    val latestDropText by rememberUpdatedState(onDropText)

    val dropTarget = remember(enabled, context) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                if (enabled) isExternalDragHovering = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isExternalDragHovering = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isExternalDragHovering = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isExternalDragHovering = false
                if (!enabled) return false

                val dragEvent = event.toAndroidDragEvent()
                val clipData = dragEvent.clipData ?: return false
                if (clipData.itemCount == 0) return false

                val activity = context as? Activity
                var dragPerm: android.view.DragAndDropPermissions? = null
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                    dragPerm = activity.requestDragAndDropPermissions(dragEvent)
                }

                var handled = false
                try {
                    for (i in 0 until clipData.itemCount) {
                        val clipItem = clipData.getItemAt(i)
                        val uri = clipItem.uri
                        if (uri != null) {
                            latestDropImage(uri)
                            handled = true
                        } else {
                            val coerced = clipItem.coerceToText(context)?.toString()?.trim()
                            if (!coerced.isNullOrEmpty()) {
                                latestDropText(coerced)
                                handled = true
                            }
                        }
                    }
                } finally {
                    // Note: URI import runs asynchronously; release permission only when purely text
                    if (clipData.getItemAt(0).uri == null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            dragPerm?.release()
                        }
                    }
                }
                return handled
            }
        }
    }

    val dragDropModifier = if (enabled) {
        Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                val types = event.mimeTypes()
                types.any { mime ->
                    mime.startsWith("image/") ||
                        mime.startsWith("text/") ||
                        mime == ClipDescription.MIMETYPE_TEXT_PLAIN ||
                        mime == ClipDescription.MIMETYPE_TEXT_URILIST
                }
            },
            target = dropTarget
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(dragDropModifier)
            .testTag("editor_external_drop_container")
    ) {
        content()

        AnimatedVisibility(
            visible = isExternalDragHovering,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.90f))
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = MaterialTheme.shapes.large
                    )
                    .testTag("external_drop_overlay")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Text(
                        text = stringResource(R.string.drop_external_overlay_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = stringResource(R.string.drop_external_overlay_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Interactive Checklist section with 48dp accessible drag handles so users can drag and drop
 * checklist tasks into any priority order.
 */
@Composable
fun DraggableChecklistSection(
    checklist: List<ChecklistItem>,
    readOnly: Boolean,
    onToggleItem: (String) -> Unit,
    onUpdateText: (String, String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onReorderItems: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val rowSlotSpanPx = with(density) { 48.dp.toPx() }
    var draggingItemId by remember { mutableStateOf<String?>(null) }
    var dragTranslationY by remember { mutableFloatStateOf(0f) }

    val latestList by rememberUpdatedState(checklist)
    val latestOnReorder by rememberUpdatedState(onReorderItems)

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .testTag("draggable_checklist_section")
    ) {
        checklist.forEachIndexed { index, item ->
            key(item.id) {
                val isDragging = draggingItemId == item.id
                val currentIndex by rememberUpdatedState(index)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 4f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragTranslationY else 0f
                            scaleX = if (isDragging) 1.015f else 1f
                            scaleY = if (isDragging) 1.015f else 1f
                        }
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surface
                        )
                        .then(
                            if (isDragging) {
                                Modifier.border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = MaterialTheme.shapes.small
                                )
                            } else {
                                Modifier
                            }
                        )
                        .testTag("checklist_row_${item.id}")
                ) {
                    if (!readOnly && checklist.size > 1) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .pointerInput(item.id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggingItemId = item.id
                                            dragTranslationY = 0f
                                        },
                                        onDragCancel = {
                                            draggingItemId = null
                                            dragTranslationY = 0f
                                        },
                                        onDragEnd = {
                                            draggingItemId = null
                                            dragTranslationY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragTranslationY += dragAmount.y
                                            val targetIdx = DragDropReorderHelper.computeTargetIndex(
                                                startIndex = currentIndex,
                                                itemCount = latestList.size,
                                                dragOffsetPx = dragTranslationY,
                                                slotSpanPx = rowSlotSpanPx
                                            )
                                            if (targetIdx != currentIndex) {
                                                val deltaSlots = targetIdx - currentIndex
                                                latestOnReorder(currentIndex, targetIdx)
                                                dragTranslationY -= deltaSlots * rowSlotSpanPx
                                            }
                                        }
                                    )
                                }
                                .testTag("checklist_drag_handle_${item.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DragIndicator,
                                contentDescription = stringResource(R.string.drag_checklist_handle_desc),
                                tint = if (isDragging) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = if (readOnly) null else { _ -> onToggleItem(item.id) },
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (item.text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.editor_checklist_placeholder),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        BasicTextField(
                            value = item.text,
                            onValueChange = { onUpdateText(item.id, it) },
                            readOnly = readOnly,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = if (item.isChecked) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textDecoration = if (item.isChecked) {
                                    TextDecoration.LineThrough
                                } else {
                                    TextDecoration.None
                                }
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (!readOnly) {
                        IconButton(
                            onClick = { onRemoveItem(item.id) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.editor_remove_checklist_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Attached note images section supporting long-press horizontal drag-and-drop reordering
 * when 2 or more inline images are attached, plus a 1-tap action to place any attached image
 * directly onto the handwriting canvas so it can be moved, scaled, or rotated with the Lasso tool.
 */
@Composable
fun DraggableAttachedImagesSection(
    images: List<NoteImageAttachment>,
    readOnly: Boolean,
    onRemoveImage: (String) -> Unit,
    onReorderImages: (fromIndex: Int, toIndex: Int) -> Unit,
    onPlaceImageOnCanvas: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val inlineImages = remember(images) { images.filterNot { it.placedOnCanvas } }
    if (inlineImages.isEmpty()) return

    if (inlineImages.size == 1) {
        val img = inlineImages.first()
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 260.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                .testTag("attached_image_${img.id}")
        ) {
            AsyncImage(
                model = File(img.localPath),
                contentDescription = stringResource(R.string.note_attached_image_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
            if (!readOnly) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                ) {
                    if (onPlaceImageOnCanvas != null) {
                        IconButton(
                            onClick = { onPlaceImageOnCanvas(img.id) },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .testTag("place_on_canvas_${img.id}")
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Outlined.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.image_place_on_canvas_desc),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onRemoveImage(img.id) },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.90f))
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .testTag("remove_image_${img.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.editor_remove_image_desc),
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    } else {
        val density = LocalDensity.current
        val cardSlotSpanPx = with(density) { 220.dp.toPx() }
        var draggingImageId by remember { mutableStateOf<String?>(null) }
        var dragTranslationX by remember { mutableFloatStateOf(0f) }

        val latestImages by rememberUpdatedState(inlineImages)
        val latestOnReorder by rememberUpdatedState(onReorderImages)

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(
                    state = rememberScrollState(),
                    enabled = draggingImageId == null
                )
                .testTag("attached_images_strip")
        ) {
            inlineImages.forEachIndexed { index, img ->
                key(img.id) {
                    val isDragging = draggingImageId == img.id
                    val currentIndex by rememberUpdatedState(index)

                    Box(
                        modifier = Modifier
                            .width(210.dp)
                            .height(152.dp)
                            .zIndex(if (isDragging) 4f else 0f)
                            .graphicsLayer {
                                translationX = if (isDragging) dragTranslationX else 0f
                                scaleX = if (isDragging) 1.03f else 1f
                                scaleY = if (isDragging) 1.03f else 1f
                            }
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = if (isDragging) 2.dp else 1.dp,
                                color = if (isDragging) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                shape = MaterialTheme.shapes.medium
                            )
                            .then(
                                if (!readOnly) {
                                    Modifier.pointerInput(img.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggingImageId = img.id
                                                dragTranslationX = 0f
                                            },
                                            onDragCancel = {
                                                draggingImageId = null
                                                dragTranslationX = 0f
                                            },
                                            onDragEnd = {
                                                draggingImageId = null
                                                dragTranslationX = 0f
                                            },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                dragTranslationX += dragAmount.x
                                                val targetIdx = DragDropReorderHelper.computeTargetIndex(
                                                    startIndex = currentIndex,
                                                    itemCount = latestImages.size,
                                                    dragOffsetPx = dragTranslationX,
                                                    slotSpanPx = cardSlotSpanPx
                                                )
                                                if (targetIdx != currentIndex) {
                                                    val deltaSlots = targetIdx - currentIndex
                                                    latestOnReorder(currentIndex, targetIdx)
                                                    dragTranslationX -= deltaSlots * cardSlotSpanPx
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .testTag("attached_image_${img.id}")
                    ) {
                        AsyncImage(
                            model = File(img.localPath),
                            contentDescription = stringResource(R.string.note_attached_image_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        if (!readOnly) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(6.dp)
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DragIndicator,
                                    contentDescription = stringResource(R.string.drag_image_handle_desc),
                                    tint = if (isDragging) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                            ) {
                                if (onPlaceImageOnCanvas != null) {
                                    IconButton(
                                        onClick = { onPlaceImageOnCanvas(img.id) },
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                            .testTag("place_on_canvas_${img.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.AddPhotoAlternate,
                                            contentDescription = stringResource(R.string.image_place_on_canvas_desc),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveImage(img.id) },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                        .testTag("remove_image_${img.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = stringResource(R.string.editor_remove_image_desc),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
