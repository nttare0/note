package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.AddTask
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridOff
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RestoreFromTrash
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.ChecklistItem
import com.example.data.EditorMode
import com.example.data.FolderEntity
import com.example.data.InkStroke
import com.example.data.InkTool
import com.example.data.NoteImageAttachment
import com.example.data.PaperTemplate
import com.example.data.StrokePoint
import com.example.ui.EditorState
import com.example.ui.components.DraggableAttachedImagesSection
import com.example.ui.components.DraggableChecklistSection
import com.example.ui.components.DrawingCanvasToolbar
import com.example.ui.components.ExternalNoteDropContainer
import com.example.ui.components.FreehandDrawingSurface
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    editorState: EditorState,
    folders: List<FolderEntity>,
    customInkColors: List<String> = emptyList(),
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onModeChange: (EditorMode) -> Unit,
    onTemplateChange: (PaperTemplate) -> Unit,
    onFolderChange: (Long?) -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
    onAttachImageUri: (Uri) -> Unit,
    onAttachCanvasImageUri: (Uri) -> Unit = onAttachImageUri,
    onPlaceImageOnCanvas: (String) -> Unit = {},
    onRemoveAttachedImage: (String) -> Unit,
    onReorderAttachedImages: (Int, Int) -> Unit,
    onInsertDroppedText: (String) -> Unit,
    onAddChecklistItem: () -> Unit,
    onUpdateChecklistText: (String, String) -> Unit,
    onToggleChecklistItem: (String) -> Unit,
    onRemoveChecklistItem: (String) -> Unit,
    onReorderChecklistItems: (Int, Int) -> Unit,
    onSelectInkTool: (InkTool) -> Unit,
    onSelectInkColor: (String) -> Unit,
    onAddCustomInkColor: (String) -> Unit = {},
    onRemoveCustomInkColor: (String) -> Unit = {},
    onSelectStrokeWidth: (Float) -> Unit,
    onCommitStroke: (InkStroke) -> Unit,
    onEraseSegmentSweep: (Float, Float, Float, Float, Float) -> Unit,
    onEraseGestureStart: () -> Unit,
    onEraseGestureEnd: () -> Unit,
    onCompleteLassoSelection: (List<StrokePoint>) -> Unit = {},
    onLongPressSelectCanvasElement: (Float, Float) -> Boolean = { _, _ -> false },
    onClearLassoSelection: () -> Unit = {},
    onLassoTransformStart: () -> Unit = {},
    onLassoTransformDelta: (Float, Float, Float, Float, Float, Float) -> Unit = { _, _, _, _, _, _ -> },
    onLassoTransformEnd: () -> Unit = {},
    onStepLassoTransform: (Float, Float) -> Unit = { _, _ -> },
    onDeleteLassoSelection: () -> Unit = {},
    onUndoStroke: () -> Unit,
    onRedoStroke: () -> Unit,
    onClearCanvas: () -> Unit,
    onToggleCanvasGrid: () -> Unit,
    onTogglePressureSensitivity: () -> Unit = {},
    onSaveCanvasPng: ((Boolean) -> Unit) -> Unit,
    onExportPdf: ((Boolean) -> Unit) -> Unit,
    onPinNoteWidget: ((Boolean) -> Unit) -> Unit = {},
    onMoveToTrash: () -> Unit,
    onRestoreNote: () -> Unit,
    onDeleteForever: () -> Unit
) {
    BackHandler(onBack = onBack)

    var showTemplateSheet by remember { mutableStateOf(false) }
    var showFolderDropdown by remember { mutableStateOf(false) }
    var pickImageForCanvas by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Zero-permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { pickedUri: Uri? ->
        val forCanvas = pickImageForCanvas
        pickImageForCanvas = false
        if (pickedUri != null) {
            if (forCanvas) {
                onAttachCanvasImageUri(pickedUri)
            } else {
                onAttachImageUri(pickedUri)
            }
        }
    }

    val triggerSavePngToGallery: () -> Unit = {
        onSaveCanvasPng { saved ->
            scope.launch {
                val msgRes = if (saved) {
                    R.string.png_saved_gallery_message
                } else {
                    R.string.png_save_failed_message
                }
                snackbarHostState.showSnackbar(context.getString(msgRes))
            }
        }
    }

    val activeFolderName = remember(editorState.folderId, folders) {
        folders.firstOrNull { it.id == editorState.folderId }?.name
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        ExternalNoteDropContainer(
            enabled = !editorState.isTrashed,
            onDropImageUri = onAttachImageUri,
            onDropText = onInsertDroppedText,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Top Minimalist Editor Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(1.5.dp, MaterialTheme.shapes.small)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                            .testTag("editor_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.editor_back_desc),
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    ModeSegmentedTabs(
                        currentMode = editorState.mode,
                        onSelectMode = onModeChange
                    )
                }

                // Right Actions
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    if (editorState.isTrashed) {
                        IconButton(
                            onClick = onRestoreNote,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RestoreFromTrash,
                                contentDescription = stringResource(R.string.editor_restore_desc),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onDeleteForever,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = stringResource(R.string.editor_delete_forever_desc),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("editor_top_add_image_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.editor_add_image),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = triggerSavePngToGallery,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("editor_export_png_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = stringResource(R.string.png_export_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = { showTemplateSheet = true },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("editor_template_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Layers,
                                contentDescription = stringResource(R.string.editor_template_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onTogglePinned,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("editor_pin_button")
                        ) {
                            Icon(
                                imageVector = if (editorState.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = stringResource(R.string.editor_pin_desc),
                                tint = if (editorState.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("editor_star_button")
                        ) {
                            Icon(
                                imageVector = if (editorState.isFavorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = stringResource(R.string.editor_favorite_desc),
                                tint = if (editorState.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onPinNoteWidget { requested ->
                                    scope.launch {
                                        val msgRes = if (requested) {
                                            R.string.widget_pinned_requested_message
                                        } else {
                                            R.string.widget_pin_unsupported_message
                                        }
                                        snackbarHostState.showSnackbar(context.getString(msgRes))
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("editor_pin_widget_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Widgets,
                                contentDescription = stringResource(R.string.widget_pin_note_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                onExportPdf { ok ->
                                    if (ok) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.pdf_exported_message)
                                            )
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("editor_export_pdf_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.IosShare,
                                contentDescription = stringResource(R.string.editor_export_pdf_desc),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (editorState.noteId != 0L) {
                            IconButton(
                                onClick = onMoveToTrash,
                                modifier = Modifier
                                    .size(44.dp)
                                    .testTag("editor_trash_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.editor_delete_desc),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Main Editor Content Area centered cleanly up to 680dp on tablets
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (editorState.mode == EditorMode.INK) {
                    // Dedicated Full-Screen S-Pen Ink Workspace
                    Column(
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        TitleField(
                            title = editorState.title,
                            readOnly = editorState.isTrashed,
                            onTitleChange = onTitleChange
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        FreehandDrawingSurface(
                            strokes = editorState.strokes,
                            canvasImages = editorState.images,
                            lassoSelection = editorState.lassoSelection,
                            template = editorState.template,
                            interactive = !editorState.isTrashed,
                            activeTool = editorState.activeTool,
                            activeColorKey = editorState.activeColorKey,
                            activeStrokeWidth = editorState.activeStrokeWidth,
                            isPressureSensitive = editorState.isPressureSensitive,
                            isDarkTheme = isDarkTheme,
                            fillFullHeight = true,
                            onCommitStroke = onCommitStroke,
                            onEraseSegmentSweep = onEraseSegmentSweep,
                            onEraseGestureStart = onEraseGestureStart,
                            onEraseGestureEnd = onEraseGestureEnd,
                            onCompleteLassoSelection = onCompleteLassoSelection,
                            onLassoTransformStart = onLassoTransformStart,
                            onLassoTransformDelta = onLassoTransformDelta,
                            onLassoTransformEnd = onLassoTransformEnd,
                            onLongPressSelectElement = onLongPressSelectCanvasElement,
                            onDeleteSelectedElements = onDeleteLassoSelection,
                            onClearSelection = onClearLassoSelection,
                            modifier = Modifier.weight(1f)
                        )
                        if (!editorState.isTrashed) {
                            val hasCanvasContent = editorState.strokes.isNotEmpty() ||
                                editorState.images.any { it.placedOnCanvas }
                            Spacer(modifier = Modifier.height(12.dp))
                            DrawingCanvasToolbar(
                                activeTool = editorState.activeTool,
                                activeColorKey = editorState.activeColorKey,
                                activeStrokeWidth = editorState.activeStrokeWidth,
                                canUndo = editorState.canvasHistory.canUndo,
                                canRedo = editorState.canvasHistory.canRedo,
                                undoCount = editorState.canvasHistory.undoCount,
                                redoCount = editorState.canvasHistory.redoCount,
                                canClear = hasCanvasContent,
                                customColorKeys = customInkColors,
                                isGridEnabled = editorState.template == PaperTemplate.GRID,
                                isPressureSensitive = editorState.isPressureSensitive,
                                lassoSelectedCount = editorState.lassoSelection.totalCount,
                                isDarkTheme = isDarkTheme,
                                onSelectTool = onSelectInkTool,
                                onSelectColor = onSelectInkColor,
                                onAddCustomColor = onAddCustomInkColor,
                                onRemoveCustomColor = onRemoveCustomInkColor,
                                onSelectWidth = onSelectStrokeWidth,
                                onUndo = onUndoStroke,
                                onRedo = onRedoStroke,
                                onClear = onClearCanvas,
                                onToggleGrid = onToggleCanvasGrid,
                                onTogglePressureSensitivity = onTogglePressureSensitivity,
                                onSavePng = triggerSavePngToGallery,
                                onRotateSelectionLeft = { onStepLassoTransform(1f, -15f) },
                                onRotateSelectionRight = { onStepLassoTransform(1f, 15f) },
                                onScaleSelectionDown = { onStepLassoTransform(0.88f, 0f) },
                                onScaleSelectionUp = { onStepLassoTransform(1.14f, 0f) },
                                onDeleteSelection = onDeleteLassoSelection,
                                onClearSelection = onClearLassoSelection,
                                onAddCanvasImage = {
                                    pickImageForCanvas = true
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                }
                            )
                        }
                    }
                } else {
                    // Combined Samsung Notes Page or Focused Type Mode
                    Column(
                        modifier = Modifier
                            .widthIn(max = 680.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        // Folder, Template, Add Image & Add Checklist Quick Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                        .clickable(enabled = !editorState.isTrashed) { showFolderDropdown = true }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("editor_folder_selector")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = activeFolderName ?: stringResource(R.string.editor_unfiled),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = showFolderDropdown,
                                    onDismissRequest = { showFolderDropdown = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.editor_unfiled)) },
                                        onClick = {
                                            onFolderChange(null)
                                            showFolderDropdown = false
                                        }
                                    )
                                    folders.forEach { folder ->
                                        DropdownMenuItem(
                                            text = { Text(folder.name) },
                                            onClick = {
                                                onFolderChange(folder.id)
                                                showFolderDropdown = false
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                    .clickable(enabled = !editorState.isTrashed) { showTemplateSheet = true }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = editorState.template.labelKey,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!editorState.isTrashed) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                        .clickable {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("add_image_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AddPhotoAlternate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.editor_add_image),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.small)
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                        .clickable(onClick = onAddChecklistItem)
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                        .testTag("add_checklist_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AddTask,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.editor_add_checklist),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TitleField(
                            title = editorState.title,
                            readOnly = editorState.isTrashed,
                            onTitleChange = onTitleChange
                        )

                        // Attached Images Section (supports long-press drag & drop reordering + Place on Canvas)
                        if (editorState.images.any { !it.placedOnCanvas }) {
                            Spacer(modifier = Modifier.height(14.dp))
                            DraggableAttachedImagesSection(
                                images = editorState.images,
                                readOnly = editorState.isTrashed,
                                onRemoveImage = onRemoveAttachedImage,
                                onReorderImages = onReorderAttachedImages,
                                onPlaceImageOnCanvas = onPlaceImageOnCanvas
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Body Text Editor Whiteboard Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 2.dp,
                                    shape = MaterialTheme.shapes.medium,
                                    ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                                    spotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                                )
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                                .padding(16.dp)
                        ) {
                            if (editorState.body.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.editor_body_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = editorState.body,
                                onValueChange = onBodyChange,
                                readOnly = editorState.isTrashed,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp)
                                    .testTag("editor_body_input")
                            )
                        }

                        // Actionable Checklist Section with Drag & Drop reordering
                        if (editorState.checklist.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(14.dp))
                            DraggableChecklistSection(
                                checklist = editorState.checklist,
                                readOnly = editorState.isTrashed,
                                onToggleItem = onToggleChecklistItem,
                                onUpdateText = onUpdateChecklistText,
                                onRemoveItem = onRemoveChecklistItem,
                                onReorderItems = onReorderChecklistItems
                            )
                        }

                        // Integrated Ink Sketch Section in PAGE mode
                        if (editorState.mode == EditorMode.PAGE) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.editor_mode_ink),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!editorState.isTrashed) {
                                        val isGridOn = editorState.template == PaperTemplate.GRID
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .background(
                                                    if (isGridOn) MaterialTheme.colorScheme.primaryContainer
                                                    else MaterialTheme.colorScheme.surface
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isGridOn) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.outline,
                                                    shape = MaterialTheme.shapes.small
                                                )
                                                .clickable(onClick = onToggleCanvasGrid)
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                                .testTag("page_canvas_grid_chip")
                                        ) {
                                            Icon(
                                                imageVector = if (isGridOn) Icons.Outlined.GridOn else Icons.Outlined.GridOff,
                                                contentDescription = null,
                                                tint = if (isGridOn) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = stringResource(R.string.tool_grid_toggle_label),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isGridOn) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    TextButton(onClick = { onModeChange(EditorMode.INK) }) {
                                        Text(
                                            text = "${editorState.template.labelKey} · Full canvas",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            FreehandDrawingSurface(
                                strokes = editorState.strokes,
                                canvasImages = editorState.images,
                                lassoSelection = editorState.lassoSelection,
                                template = editorState.template,
                                interactive = !editorState.isTrashed,
                                activeTool = editorState.activeTool,
                                activeColorKey = editorState.activeColorKey,
                                activeStrokeWidth = editorState.activeStrokeWidth,
                                isPressureSensitive = editorState.isPressureSensitive,
                                isDarkTheme = isDarkTheme,
                                canvasHeight = 280.dp,
                                onCommitStroke = onCommitStroke,
                                onEraseSegmentSweep = onEraseSegmentSweep,
                                onEraseGestureStart = onEraseGestureStart,
                                onEraseGestureEnd = onEraseGestureEnd,
                                onCompleteLassoSelection = onCompleteLassoSelection,
                                onLassoTransformStart = onLassoTransformStart,
                                onLassoTransformDelta = onLassoTransformDelta,
                                onLassoTransformEnd = onLassoTransformEnd,
                                onLongPressSelectElement = onLongPressSelectCanvasElement,
                                onDeleteSelectedElements = onDeleteLassoSelection,
                                onClearSelection = onClearLassoSelection
                            )

                            if (!editorState.isTrashed) {
                                val hasCanvasContent = editorState.strokes.isNotEmpty() ||
                                    editorState.images.any { it.placedOnCanvas }
                                Spacer(modifier = Modifier.height(12.dp))
                                DrawingCanvasToolbar(
                                    activeTool = editorState.activeTool,
                                    activeColorKey = editorState.activeColorKey,
                                    activeStrokeWidth = editorState.activeStrokeWidth,
                                    canUndo = editorState.canvasHistory.canUndo,
                                    canRedo = editorState.canvasHistory.canRedo,
                                    undoCount = editorState.canvasHistory.undoCount,
                                    redoCount = editorState.canvasHistory.redoCount,
                                    canClear = hasCanvasContent,
                                    customColorKeys = customInkColors,
                                    isGridEnabled = editorState.template == PaperTemplate.GRID,
                                    isPressureSensitive = editorState.isPressureSensitive,
                                    lassoSelectedCount = editorState.lassoSelection.totalCount,
                                    isDarkTheme = isDarkTheme,
                                    onSelectTool = onSelectInkTool,
                                    onSelectColor = onSelectInkColor,
                                    onAddCustomColor = onAddCustomInkColor,
                                    onRemoveCustomColor = onRemoveCustomInkColor,
                                    onSelectWidth = onSelectStrokeWidth,
                                    onUndo = onUndoStroke,
                                    onRedo = onRedoStroke,
                                    onClear = onClearCanvas,
                                    onToggleGrid = onToggleCanvasGrid,
                                    onTogglePressureSensitivity = onTogglePressureSensitivity,
                                    onSavePng = triggerSavePngToGallery,
                                    onRotateSelectionLeft = { onStepLassoTransform(1f, -15f) },
                                    onRotateSelectionRight = { onStepLassoTransform(1f, 15f) },
                                    onScaleSelectionDown = { onStepLassoTransform(0.88f, 0f) },
                                    onScaleSelectionUp = { onStepLassoTransform(1.14f, 0f) },
                                    onDeleteSelection = onDeleteLassoSelection,
                                    onClearSelection = onClearLassoSelection,
                                    onAddCanvasImage = {
                                        pickImageForCanvas = true
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(36.dp))
                    }
                }
            }
            }
        }
    }

    if (showTemplateSheet) {
        ModalBottomSheet(
            onDismissRequest = { showTemplateSheet = false },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.template_sheet_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                PaperTemplate.entries.forEach { tmpl ->
                    val selected = tmpl == editorState.template
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .clickable {
                                onTemplateChange(tmpl)
                                showTemplateSheet = false
                            }
                            .padding(horizontal = 14.dp)
                            .testTag("template_option_${tmpl.name}")
                    ) {
                        Text(
                            text = tmpl.labelKey,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (selected) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun TitleField(
    title: String,
    readOnly: Boolean,
    onTitleChange: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        if (title.isEmpty()) {
            Text(
                text = stringResource(R.string.editor_title_placeholder),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        BasicTextField(
            value = title,
            onValueChange = onTitleChange,
            readOnly = readOnly,
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineMedium.copy(
                color = MaterialTheme.colorScheme.onBackground
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_title_input")
        )
    }
}

@Composable
private fun ModeSegmentedTabs(
    currentMode: EditorMode,
    onSelectMode: (EditorMode) -> Unit
) {
    val tabs = listOf(
        EditorMode.PAGE to R.string.editor_mode_split,
        EditorMode.TYPE to R.string.editor_mode_type,
        EditorMode.INK to R.string.editor_mode_ink
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .shadow(1.5.dp, MaterialTheme.shapes.small)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
            .padding(3.dp)
    ) {
        tabs.forEach { (mode, labelRes) ->
            val selected = currentMode == mode
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .height(34.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .clickable { onSelectMode(mode) }
                    .padding(horizontal = 12.dp)
                    .testTag("editor_mode_${mode.name}")
            ) {
                Text(
                    text = stringResource(labelRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
