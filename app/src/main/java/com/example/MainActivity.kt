package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.NotebookViewModel
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.NoteStudioTheme
import com.example.widget.NoteWidgetHelper

data class WidgetLaunchEvent(
    val action: String,
    val noteId: Long = 0L,
    val token: Long = System.nanoTime()
)

class MainActivity : ComponentActivity() {

    private var pendingWidgetEvent by mutableStateOf<WidgetLaunchEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            extractWidgetLaunchEvent(intent)?.let { pendingWidgetEvent = it }
        }
        setContent {
            NoteStudioApp(
                pendingWidgetEvent = pendingWidgetEvent,
                onConsumeWidgetEvent = { pendingWidgetEvent = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractWidgetLaunchEvent(intent)?.let { pendingWidgetEvent = it }
    }

    private fun extractWidgetLaunchEvent(intent: Intent?): WidgetLaunchEvent? {
        val action = intent?.getStringExtra(NoteWidgetHelper.EXTRA_WIDGET_ACTION) ?: return null
        val targetId = intent.getLongExtra(NoteWidgetHelper.EXTRA_TARGET_NOTE_ID, 0L)
        intent.removeExtra(NoteWidgetHelper.EXTRA_WIDGET_ACTION)
        intent.removeExtra(NoteWidgetHelper.EXTRA_TARGET_NOTE_ID)
        return WidgetLaunchEvent(action = action, noteId = targetId)
    }
}

@Composable
fun NoteStudioApp(
    pendingWidgetEvent: WidgetLaunchEvent? = null,
    onConsumeWidgetEvent: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: NotebookViewModel = viewModel(
        factory = NotebookViewModel.factory(context)
    )

    LaunchedEffect(pendingWidgetEvent) {
        if (pendingWidgetEvent != null) {
            viewModel.handleWidgetLaunchCommand(
                widgetAction = pendingWidgetEvent.action,
                targetNoteId = pendingWidgetEvent.noteId
            )
            onConsumeWidgetEvent()
        }
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activeEditor by viewModel.activeEditor.collectAsStateWithLifecycle()

    val systemDark = isSystemInDarkTheme()
    val effectiveDark = uiState.prefs.isDarkThemeOverride ?: systemDark

    NoteStudioTheme(darkTheme = effectiveDark) {
        AnimatedContent(
            targetState = activeEditor != null,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith fadeOut(animationSpec = tween(160))
            },
            label = "notebook_screen_transition"
        ) { isEditing ->
            val currentEditor = activeEditor
            if (isEditing && currentEditor != null) {
                NoteEditorScreen(
                    editorState = currentEditor,
                    folders = uiState.folders,
                    customInkColors = uiState.customInkColors,
                    isDarkTheme = effectiveDark,
                    onBack = viewModel::saveAndCloseEditor,
                    onTitleChange = viewModel::updateEditorTitle,
                    onBodyChange = viewModel::updateEditorBody,
                    onModeChange = viewModel::setEditorMode,
                    onTemplateChange = viewModel::setEditorTemplate,
                    onFolderChange = viewModel::setEditorFolder,
                    onToggleFavorite = viewModel::toggleEditorFavorite,
                    onTogglePinned = viewModel::toggleEditorPinned,
                    onAttachImageUri = { uri -> viewModel.attachImageFromUri(context, uri, placeOnCanvas = false) },
                    onAttachCanvasImageUri = { uri -> viewModel.attachImageFromUri(context, uri, placeOnCanvas = true) },
                    onPlaceImageOnCanvas = viewModel::placeAttachedImageOnCanvas,
                    onRemoveAttachedImage = viewModel::removeAttachedImage,
                    onReorderAttachedImages = viewModel::reorderAttachedImages,
                    onInsertDroppedText = viewModel::insertDroppedText,
                    onAddChecklistItem = viewModel::addChecklistItem,
                    onUpdateChecklistText = viewModel::updateChecklistItemText,
                    onToggleChecklistItem = viewModel::toggleChecklistItem,
                    onRemoveChecklistItem = viewModel::removeChecklistItem,
                    onReorderChecklistItems = viewModel::reorderChecklistItems,
                    onSelectInkTool = viewModel::selectInkTool,
                    onSelectInkColor = viewModel::selectInkColor,
                    onAddCustomInkColor = viewModel::addCustomInkColor,
                    onRemoveCustomInkColor = viewModel::removeCustomInkColor,
                    onSelectStrokeWidth = viewModel::selectStrokeWidth,
                    onCommitStroke = viewModel::commitInkStroke,
                    onEraseSegmentSweep = viewModel::eraseStrokeSegments,
                    onEraseGestureStart = viewModel::beginEraseGesture,
                    onEraseGestureEnd = viewModel::endEraseGesture,
                    onCompleteLassoSelection = viewModel::completeLassoSelection,
                    onLongPressSelectCanvasElement = viewModel::selectCanvasElementAtPoint,
                    onClearLassoSelection = viewModel::clearLassoSelection,
                    onLassoTransformStart = viewModel::beginLassoTransformGesture,
                    onLassoTransformDelta = viewModel::applyLassoTransformDelta,
                    onLassoTransformEnd = viewModel::endLassoTransformGesture,
                    onStepLassoTransform = viewModel::transformSelectionByStep,
                    onDeleteLassoSelection = viewModel::deleteLassoSelection,
                    onUndoStroke = viewModel::undoStroke,
                    onRedoStroke = viewModel::redoStroke,
                    onClearCanvas = viewModel::clearCanvas,
                    onToggleCanvasGrid = viewModel::toggleCanvasGridBackground,
                    onTogglePressureSensitivity = viewModel::togglePressureSensitivity,
                    onSaveCanvasPng = { callback ->
                        viewModel.exportCanvasAsPngToGallery(context, effectiveDark, callback)
                    },
                    onExportPdf = { callback -> viewModel.exportCurrentNotePdf(context, callback) },
                    onPinNoteWidget = { callback ->
                        viewModel.pinCurrentNoteToHomeWidget(context, callback)
                    },
                    onMoveToTrash = viewModel::trashCurrentNote,
                    onRestoreNote = viewModel::restoreCurrentNote,
                    onDeleteForever = viewModel::deleteCurrentNoteForever
                )
            } else {
                WorkspaceScreen(
                    uiState = uiState,
                    isDarkTheme = effectiveDark,
                    onSelectFilter = viewModel::selectFilter,
                    onSearchChange = viewModel::updateSearchQuery,
                    onToggleTheme = { viewModel.toggleDarkTheme(effectiveDark) },
                    onToggleGridView = viewModel::toggleGridView,
                    onCreateNote = viewModel::openNewNote,
                    onOpenNote = viewModel::openExistingNote,
                    onToggleNoteFavorite = viewModel::toggleNoteFavoriteFromList,
                    onMoveNoteToFolder = viewModel::moveNoteToFolderFromWorkspace,
                    onToggleSelectNote = viewModel::toggleNoteSelection,
                    onSelectNote = viewModel::selectNote,
                    onSelectAllNotes = viewModel::selectAllVisibleNotes,
                    onClearNoteSelection = viewModel::clearNoteSelection,
                    onDeleteSelectedNotes = { callback -> viewModel.deleteSelectedNotes(callback) },
                    onPinQuickCaptureWidget = {
                        NoteWidgetHelper.requestPinQuickCaptureWidget(context)
                    },
                    onCreateFolder = viewModel::createFolder,
                    onDeleteFolder = viewModel::deleteFolder,
                    onEmptyTrash = viewModel::emptyTrash
                )
            }
        }
    }
}
