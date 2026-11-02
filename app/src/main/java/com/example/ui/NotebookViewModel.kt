package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.ChecklistItem
import com.example.data.EditorMode
import com.example.data.FolderEntity
import com.example.data.InkStroke
import com.example.data.InkTool
import com.example.data.NoteDatabase
import com.example.data.NoteEntity
import com.example.data.NoteImageAttachment
import com.example.data.NoteRepository
import com.example.data.NoteSerializer
import com.example.data.PaperTemplate
import com.example.data.StrokePoint
import com.example.data.UserPrefs
import com.example.util.CanvasHistoryStack
import com.example.util.CanvasPngExportHelper
import com.example.util.DragDropReorderHelper
import com.example.util.LassoHitSelection
import com.example.util.LassoSelectionEngine
import com.example.util.NoteImageStorageHelper
import com.example.util.PdfExportHelper
import com.example.util.StrokeEraserEngine
import com.example.widget.NoteWidgetHelper
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface NoteFilter {
    data object All : NoteFilter
    data object Favorites : NoteFilter
    data object Trash : NoteFilter
    data class Folder(val folderId: Long, val folderName: String) : NoteFilter
}

data class EditorState(
    val noteId: Long = 0L,
    val title: String = "",
    val body: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val images: List<NoteImageAttachment> = emptyList(),
    val strokes: List<InkStroke> = emptyList(),
    val canvasHistory: CanvasHistoryStack = CanvasHistoryStack(),
    val lassoSelection: LassoHitSelection = LassoHitSelection(),
    val template: PaperTemplate = PaperTemplate.BLANK,
    val previousNonGridTemplate: PaperTemplate = PaperTemplate.BLANK,
    val folderId: Long? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val mode: EditorMode = EditorMode.PAGE,
    val activeTool: InkTool = InkTool.PEN,
    val activeColorKey: String = "INK",
    val activeStrokeWidth: Float = 3.4f,
    val savedPenWidth: Float = 3.4f,
    val savedMarkerWidth: Float = 16f,
    val savedEraserRadius: Float = 18f
)

data class WorkspaceUiState(
    val visibleNotes: List<NoteEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val folderCounts: Map<Long, Int> = emptyMap(),
    val totalActiveCount: Int = 0,
    val favoritesCount: Int = 0,
    val trashCount: Int = 0,
    val activeFilter: NoteFilter = NoteFilter.All,
    val searchQuery: String = "",
    val prefs: UserPrefs = UserPrefs(),
    val isLoaded: Boolean = false
)

class NotebookViewModel(
    private val repository: NoteRepository
) : ViewModel() {

    private val _activeFilter = MutableStateFlow<NoteFilter>(NoteFilter.All)
    private val _searchQuery = MutableStateFlow("")

    private val _activeEditor = MutableStateFlow<EditorState?>(null)
    val activeEditor: StateFlow<EditorState?> = _activeEditor.asStateFlow()

    val uiState: StateFlow<WorkspaceUiState> = combine(
        repository.allNotes,
        repository.allFolders,
        repository.prefsFlow,
        _activeFilter,
        _searchQuery
    ) { allNotes, folders, prefs, filter, query ->
        val nonTrashed = allNotes.filter { !it.isTrashed }
        val trashed = allNotes.filter { it.isTrashed }
        val folderCounts = nonTrashed
            .mapNotNull { it.folderId }
            .groupingBy { it }
            .eachCount()

        val filteredByScope = when (filter) {
            NoteFilter.All -> nonTrashed
            NoteFilter.Favorites -> nonTrashed.filter { it.isFavorite }
            NoteFilter.Trash -> trashed
            is NoteFilter.Folder -> nonTrashed.filter { it.folderId == filter.folderId }
        }

        val q = query.trim().lowercase()
        val finalNotes = if (q.isEmpty()) {
            filteredByScope
        } else {
            filteredByScope.filter { note ->
                note.title.lowercase().contains(q) ||
                    note.body.lowercase().contains(q) ||
                    note.checklistJson.lowercase().contains(q)
            }
        }

        WorkspaceUiState(
            visibleNotes = finalNotes,
            folders = folders,
            folderCounts = folderCounts,
            totalActiveCount = nonTrashed.size,
            favoritesCount = nonTrashed.count { it.isFavorite },
            trashCount = trashed.size,
            activeFilter = filter,
            searchQuery = query,
            prefs = prefs,
            isLoaded = true
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorkspaceUiState()
    )

    init {
        viewModelScope.launch {
            repository.seedIfFirstLaunch()
        }
    }

    fun selectFilter(filter: NoteFilter) {
        _activeFilter.value = filter
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleDarkTheme(currentlyDark: Boolean) {
        viewModelScope.launch {
            repository.setDarkTheme(!currentlyDark)
        }
    }

    fun toggleGridView() {
        val current = uiState.value.prefs.isGridView
        viewModelScope.launch {
            repository.setGridView(!current)
        }
    }

    fun createFolder(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val newId = repository.createFolder(clean)
            _activeFilter.value = NoteFilter.Folder(newId, clean)
        }
    }

    fun deleteFolder(folderId: Long) {
        viewModelScope.launch {
            repository.deleteFolder(folderId)
            val current = _activeFilter.value
            if (current is NoteFilter.Folder && current.folderId == folderId) {
                _activeFilter.value = NoteFilter.All
            }
        }
    }

    fun openNewNote(initialMode: EditorMode = EditorMode.PAGE) {
        val currentFolderId = (_activeFilter.value as? NoteFilter.Folder)?.folderId
        _activeEditor.value = EditorState(
            noteId = 0L,
            folderId = currentFolderId,
            template = PaperTemplate.BLANK,
            mode = initialMode
        )
    }

    fun openNewChecklistNote() {
        val currentFolderId = (_activeFilter.value as? NoteFilter.Folder)?.folderId
        _activeEditor.value = EditorState(
            noteId = 0L,
            folderId = currentFolderId,
            checklist = listOf(
                ChecklistItem(
                    id = UUID.randomUUID().toString(),
                    text = "",
                    isChecked = false
                )
            ),
            template = PaperTemplate.BLANK,
            mode = EditorMode.TYPE
        )
    }

    fun handleWidgetLaunchCommand(widgetAction: String, targetNoteId: Long) {
        when (widgetAction) {
            NoteWidgetHelper.ACTION_OPEN_WORKSPACE -> {
                _activeEditor.value = null
            }
            NoteWidgetHelper.ACTION_NEW_PAGE -> {
                openNewNote(EditorMode.PAGE)
            }
            NoteWidgetHelper.ACTION_NEW_INK -> {
                openNewNote(EditorMode.INK)
            }
            NoteWidgetHelper.ACTION_NEW_CHECKLIST -> {
                openNewChecklistNote()
            }
            NoteWidgetHelper.ACTION_OPEN_NOTE -> {
                if (targetNoteId > 0L) {
                    viewModelScope.launch {
                        val note = repository.getNoteById(targetNoteId)
                        if (note != null && !note.isTrashed) {
                            openExistingNote(note)
                        } else {
                            openNewNote(EditorMode.PAGE)
                        }
                    }
                }
            }
        }
    }

    fun openExistingNote(note: NoteEntity) {
        val parsedTemplate = runCatching { PaperTemplate.valueOf(note.template) }
            .getOrDefault(PaperTemplate.BLANK)
        val strokes = NoteSerializer.decodeStrokes(note.strokesJson)
        val checklist = NoteSerializer.decodeChecklist(note.checklistJson)
        val images = NoteSerializer.decodeImages(note.imagesJson)
        _activeEditor.value = EditorState(
            noteId = note.id,
            title = note.title,
            body = note.body,
            checklist = checklist,
            images = images,
            strokes = strokes,
            canvasHistory = CanvasHistoryStack(),
            template = parsedTemplate,
            previousNonGridTemplate = if (parsedTemplate == PaperTemplate.GRID) PaperTemplate.BLANK else parsedTemplate,
            folderId = note.folderId,
            isFavorite = note.isFavorite,
            isPinned = note.isPinned,
            isTrashed = note.isTrashed,
            createdAt = note.createdAt,
            mode = EditorMode.PAGE
        )
    }

    fun updateEditorTitle(title: String) {
        _activeEditor.update { it?.copy(title = title) }
        persistActiveEditorDebounced()
    }

    fun updateEditorBody(body: String) {
        _activeEditor.update { it?.copy(body = body) }
        persistActiveEditorDebounced()
    }

    fun setEditorMode(mode: EditorMode) {
        _activeEditor.update { it?.copy(mode = mode) }
    }

    fun setEditorTemplate(template: PaperTemplate) {
        _activeEditor.update { state ->
            state?.copy(
                template = template,
                previousNonGridTemplate = if (template == PaperTemplate.GRID) {
                    state.previousNonGridTemplate
                } else {
                    template
                }
            )
        }
        persistActiveEditorDebounced()
    }

    fun toggleCanvasGridBackground() {
        _activeEditor.update { state ->
            if (state == null) return@update null
            val nextTemplate = computeToggledGridTemplate(
                currentTemplate = state.template,
                previousNonGridTemplate = state.previousNonGridTemplate
            )
            val nextNonGrid = if (state.template != PaperTemplate.GRID) {
                state.template
            } else {
                state.previousNonGridTemplate
            }
            state.copy(
                template = nextTemplate,
                previousNonGridTemplate = nextNonGrid
            )
        }
        persistActiveEditorDebounced()
    }

    fun setEditorFolder(folderId: Long?) {
        _activeEditor.update { it?.copy(folderId = folderId) }
        persistActiveEditorDebounced()
    }

    fun toggleEditorFavorite() {
        _activeEditor.update { it?.copy(isFavorite = !it.isFavorite) }
        persistActiveEditorDebounced()
    }

    fun toggleEditorPinned() {
        _activeEditor.update { it?.copy(isPinned = !it.isPinned) }
        persistActiveEditorDebounced()
    }

    // Image Attachment operations (Zero-Permission Photo Picker)
    fun attachImageFromUri(context: Context, uri: Uri, placeOnCanvas: Boolean = false) {
        viewModelScope.launch {
            val attachment = NoteImageStorageHelper.importPickedImage(context, uri) ?: return@launch
            _activeEditor.update { state ->
                if (state == null) return@update null
                val shouldPlaceOnCanvas = placeOnCanvas ||
                    state.mode == EditorMode.INK ||
                    state.activeTool == InkTool.LASSO

                if (shouldPlaceOnCanvas) {
                    val ratio = (attachment.heightPx.toFloat() / attachment.widthPx.coerceAtLeast(1))
                        .coerceIn(0.55f, 1.45f)
                    val initWidthDp = 156f
                    val initHeightDp = (initWidthDp * ratio).coerceIn(84f, 220f)
                    val stagger = (state.images.count { it.placedOnCanvas } % 4) * 18f
                    val canvasAttachment = attachment.copy(
                        placedOnCanvas = true,
                        canvasXDp = 28f + stagger,
                        canvasYDp = 28f + stagger,
                        canvasWidthDp = initWidthDp,
                        canvasHeightDp = initHeightDp,
                        rotationDeg = 0f
                    )
                    state.copy(
                        canvasHistory = state.canvasHistory.recordBeforeChange(state.strokes, state.images),
                        images = state.images + canvasAttachment,
                        activeTool = InkTool.LASSO,
                        lassoSelection = LassoHitSelection(selectedImageIds = setOf(canvasAttachment.id))
                    )
                } else {
                    state.copy(images = state.images + attachment)
                }
            }
            persistActiveEditorDebounced()
        }
    }

    fun placeAttachedImageOnCanvas(imageId: String) {
        var didUpdate = false
        _activeEditor.update { state ->
            if (state == null) return@update null
            val target = state.images.firstOrNull { it.id == imageId } ?: return@update state
            if (target.placedOnCanvas) return@update state

            didUpdate = true
            val ratio = (target.heightPx.toFloat() / target.widthPx.coerceAtLeast(1))
                .coerceIn(0.55f, 1.45f)
            val initWidthDp = 156f
            val initHeightDp = (initWidthDp * ratio).coerceIn(84f, 220f)
            val stagger = (state.images.count { it.placedOnCanvas } % 4) * 18f
            val updatedImages = state.images.map { img ->
                if (img.id == imageId) {
                    img.copy(
                        placedOnCanvas = true,
                        canvasXDp = 28f + stagger,
                        canvasYDp = 28f + stagger,
                        canvasWidthDp = initWidthDp,
                        canvasHeightDp = initHeightDp,
                        rotationDeg = 0f
                    )
                } else {
                    img
                }
            }
            state.copy(
                canvasHistory = state.canvasHistory.recordBeforeChange(state.strokes, state.images),
                images = updatedImages,
                mode = if (state.mode == EditorMode.TYPE) EditorMode.PAGE else state.mode,
                activeTool = InkTool.LASSO,
                lassoSelection = LassoHitSelection(selectedImageIds = setOf(imageId))
            )
        }
        if (didUpdate) persistActiveEditorDebounced()
    }

    fun removeAttachedImage(imageId: String) {
        val target = _activeEditor.value?.images?.firstOrNull { it.id == imageId }
        _activeEditor.update { state ->
            state?.copy(
                images = state.images.filterNot { it.id == imageId },
                lassoSelection = state.lassoSelection.copy(
                    selectedImageIds = state.lassoSelection.selectedImageIds - imageId
                )
            )
        }
        persistActiveEditorDebounced()
        if (target != null && !target.placedOnCanvas) {
            viewModelScope.launch {
                NoteImageStorageHelper.deleteLocalImage(target.localPath)
            }
        }
    }

    fun reorderAttachedImages(fromIndex: Int, toIndex: Int) {
        var didMove = false
        _activeEditor.update { state ->
            if (state == null) return@update null
            val inlineImages = state.images.filterNot { it.placedOnCanvas }
            val canvasImages = state.images.filter { it.placedOnCanvas }
            val reorderedInline = DragDropReorderHelper.moveItem(inlineImages, fromIndex, toIndex)
            if (reorderedInline !== inlineImages) {
                didMove = true
                state.copy(images = reorderedInline + canvasImages)
            } else {
                state
            }
        }
        if (didMove) persistActiveEditorDebounced()
    }

    fun insertDroppedText(droppedText: String) {
        val trimmed = droppedText.trim()
        if (trimmed.isEmpty()) return
        _activeEditor.update { state ->
            if (state == null) return@update null
            val updatedBody = if (state.body.isBlank()) {
                trimmed
            } else {
                "${state.body.trimEnd()}\n\n$trimmed"
            }
            state.copy(body = updatedBody)
        }
        persistActiveEditorDebounced()
    }

    // Checklist operations
    fun addChecklistItem() {
        _activeEditor.update { state ->
            state?.copy(
                checklist = state.checklist + ChecklistItem(
                    id = UUID.randomUUID().toString(),
                    text = "",
                    isChecked = false
                )
            )
        }
        persistActiveEditorDebounced()
    }

    fun updateChecklistItemText(itemId: String, text: String) {
        _activeEditor.update { state ->
            state?.copy(
                checklist = state.checklist.map { if (it.id == itemId) it.copy(text = text) else it }
            )
        }
        persistActiveEditorDebounced()
    }

    fun toggleChecklistItem(itemId: String) {
        _activeEditor.update { state ->
            state?.copy(
                checklist = state.checklist.map { if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it }
            )
        }
        persistActiveEditorDebounced()
    }

    fun removeChecklistItem(itemId: String) {
        _activeEditor.update { state ->
            state?.copy(checklist = state.checklist.filterNot { it.id == itemId })
        }
        persistActiveEditorDebounced()
    }

    fun reorderChecklistItems(fromIndex: Int, toIndex: Int) {
        var didMove = false
        _activeEditor.update { state ->
            if (state == null) return@update null
            val reordered = DragDropReorderHelper.moveItem(state.checklist, fromIndex, toIndex)
            if (reordered !== state.checklist) {
                didMove = true
                state.copy(checklist = reordered)
            } else {
                state
            }
        }
        if (didMove) persistActiveEditorDebounced()
    }

    private var preEraseSnapshot: List<InkStroke>? = null
    private var preEraseImagesSnapshot: List<NoteImageAttachment>? = null
    private var eraseGestureModified: Boolean = false

    private var preTransformStrokes: List<InkStroke>? = null
    private var preTransformImages: List<NoteImageAttachment>? = null
    private var lassoGestureModified: Boolean = false

    // Ink / S-Pen, Lasso Selection & Undo/Redo Stack operations
    fun selectInkTool(tool: InkTool) {
        _activeEditor.update { state ->
            if (state == null) null
            else {
                val restoredWidth = when (tool) {
                    InkTool.PEN -> state.savedPenWidth
                    InkTool.MARKER -> state.savedMarkerWidth
                    InkTool.ERASER -> state.savedEraserRadius
                    InkTool.LASSO -> state.activeStrokeWidth
                }
                val nextSelection = if (tool == InkTool.LASSO) {
                    state.lassoSelection
                } else {
                    LassoHitSelection()
                }
                state.copy(
                    activeTool = tool,
                    activeStrokeWidth = restoredWidth,
                    lassoSelection = nextSelection
                )
            }
        }
    }

    fun selectInkColor(colorKey: String) {
        var didRecolorSelection = false
        _activeEditor.update { state ->
            if (state == null) null
            else if (state.activeTool == InkTool.LASSO && state.lassoSelection.selectedStrokeIds.isNotEmpty()) {
                didRecolorSelection = true
                val updatedStrokes = state.strokes.map { stroke ->
                    if (stroke.id in state.lassoSelection.selectedStrokeIds) {
                        stroke.copy(colorKey = colorKey)
                    } else {
                        stroke
                    }
                }
                state.copy(
                    activeColorKey = colorKey,
                    canvasHistory = state.canvasHistory.recordBeforeChange(state.strokes, state.images),
                    strokes = updatedStrokes
                )
            } else {
                val switchingToPen = state.activeTool == InkTool.ERASER || state.activeTool == InkTool.LASSO
                val nextTool = if (switchingToPen) InkTool.PEN else state.activeTool
                val nextWidth = if (switchingToPen) state.savedPenWidth else state.activeStrokeWidth
                state.copy(
                    activeColorKey = colorKey,
                    activeTool = nextTool,
                    activeStrokeWidth = nextWidth,
                    lassoSelection = LassoHitSelection()
                )
            }
        }
        if (didRecolorSelection) persistActiveEditorDebounced()
    }

    fun selectStrokeWidth(widthDp: Float) {
        _activeEditor.update { state ->
            if (state == null) null
            else {
                when (state.activeTool) {
                    InkTool.PEN -> state.copy(activeStrokeWidth = widthDp, savedPenWidth = widthDp)
                    InkTool.MARKER -> state.copy(activeStrokeWidth = widthDp, savedMarkerWidth = widthDp)
                    InkTool.ERASER -> state.copy(activeStrokeWidth = widthDp, savedEraserRadius = widthDp)
                    InkTool.LASSO -> state
                }
            }
        }
    }

    fun completeLassoSelection(lassoPoints: List<StrokePoint>) {
        _activeEditor.update { state ->
            if (state == null) return@update null
            val hit = LassoSelectionEngine.findSelectedElements(
                lassoPoints = lassoPoints,
                strokes = state.strokes,
                images = state.images
            )
            state.copy(lassoSelection = hit)
        }
    }

    fun clearLassoSelection() {
        _activeEditor.update { state ->
            state?.copy(lassoSelection = LassoHitSelection())
        }
    }

    fun beginLassoTransformGesture() {
        val current = _activeEditor.value ?: return
        preTransformStrokes = current.strokes
        preTransformImages = current.images
        lassoGestureModified = false
    }

    fun applyLassoTransformDelta(
        pivotXDp: Float,
        pivotYDp: Float,
        dxDp: Float,
        dyDp: Float,
        scaleFactor: Float,
        rotationDeltaDeg: Float
    ) {
        _activeEditor.update { state ->
            if (state == null || state.lassoSelection.isEmpty) return@update state
            val nextStrokes = LassoSelectionEngine.transformSelectedStrokes(
                strokes = state.strokes,
                selectedStrokeIds = state.lassoSelection.selectedStrokeIds,
                pivotXDp = pivotXDp,
                pivotYDp = pivotYDp,
                dxDp = dxDp,
                dyDp = dyDp,
                scaleFactor = scaleFactor,
                rotationDeltaDeg = rotationDeltaDeg
            )
            val nextImages = LassoSelectionEngine.transformSelectedImages(
                images = state.images,
                selectedImageIds = state.lassoSelection.selectedImageIds,
                pivotXDp = pivotXDp,
                pivotYDp = pivotYDp,
                dxDp = dxDp,
                dyDp = dyDp,
                scaleFactor = scaleFactor,
                rotationDeltaDeg = rotationDeltaDeg
            )
            lassoGestureModified = true
            state.copy(
                strokes = nextStrokes,
                images = nextImages
            )
        }
    }

    fun endLassoTransformGesture() {
        val prevStrokes = preTransformStrokes
        val prevImages = preTransformImages
        preTransformStrokes = null
        preTransformImages = null
        if (lassoGestureModified && prevStrokes != null && prevImages != null) {
            lassoGestureModified = false
            _activeEditor.update { state ->
                state?.copy(
                    canvasHistory = state.canvasHistory.recordBeforeChange(prevStrokes, prevImages)
                )
            }
            persistActiveEditorDebounced()
        }
    }

    fun transformSelectionByStep(scaleFactor: Float = 1f, rotationDeltaDeg: Float = 0f) {
        var didTransform = false
        _activeEditor.update { state ->
            if (state == null || state.lassoSelection.isEmpty) return@update state
            val bounds = LassoSelectionEngine.computeSelectionBounds(
                strokes = state.strokes,
                images = state.images,
                selection = state.lassoSelection
            ) ?: return@update state

            val nextStrokes = LassoSelectionEngine.transformSelectedStrokes(
                strokes = state.strokes,
                selectedStrokeIds = state.lassoSelection.selectedStrokeIds,
                pivotXDp = bounds.centerX,
                pivotYDp = bounds.centerY,
                scaleFactor = scaleFactor,
                rotationDeltaDeg = rotationDeltaDeg
            )
            val nextImages = LassoSelectionEngine.transformSelectedImages(
                images = state.images,
                selectedImageIds = state.lassoSelection.selectedImageIds,
                pivotXDp = bounds.centerX,
                pivotYDp = bounds.centerY,
                scaleFactor = scaleFactor,
                rotationDeltaDeg = rotationDeltaDeg
            )
            didTransform = true
            state.copy(
                canvasHistory = state.canvasHistory.recordBeforeChange(state.strokes, state.images),
                strokes = nextStrokes,
                images = nextImages
            )
        }
        if (didTransform) persistActiveEditorDebounced()
    }

    fun deleteLassoSelection() {
        var didDelete = false
        _activeEditor.update { state ->
            if (state == null || state.lassoSelection.isEmpty) return@update state
            didDelete = true
            state.copy(
                canvasHistory = state.canvasHistory.recordBeforeChange(state.strokes, state.images),
                strokes = state.strokes.filterNot { it.id in state.lassoSelection.selectedStrokeIds },
                images = state.images.filterNot { it.id in state.lassoSelection.selectedImageIds },
                lassoSelection = LassoHitSelection()
            )
        }
        if (didDelete) persistActiveEditorDebounced()
    }

    fun commitInkStroke(stroke: InkStroke) {
        _activeEditor.update { state ->
            if (state == null) null
            else {
                state.copy(
                    canvasHistory = state.canvasHistory.recordBeforeChange(state.strokes, state.images),
                    strokes = state.strokes + stroke,
                    lassoSelection = LassoHitSelection()
                )
            }
        }
        persistActiveEditorDebounced()
    }

    fun beginEraseGesture() {
        val current = _activeEditor.value
        preEraseSnapshot = current?.strokes
        preEraseImagesSnapshot = current?.images
        eraseGestureModified = false
    }

    fun eraseStrokeSegments(
        fromXDp: Float,
        fromYDp: Float,
        toXDp: Float,
        toYDp: Float,
        eraserRadiusDp: Float
    ) {
        _activeEditor.update { state ->
            if (state == null || state.strokes.isEmpty()) return@update state
            val pass = StrokeEraserEngine.eraseSegmentsAlongSweep(
                strokes = state.strokes,
                fromXDp = fromXDp,
                fromYDp = fromYDp,
                toXDp = toXDp,
                toYDp = toYDp,
                eraserRadiusDp = eraserRadiusDp
            )
            if (pass.modified) {
                eraseGestureModified = true
                state.copy(strokes = pass.updatedStrokes)
            } else {
                state
            }
        }
    }

    fun endEraseGesture() {
        val snapshot = preEraseSnapshot
        val imgSnapshot = preEraseImagesSnapshot
        preEraseSnapshot = null
        preEraseImagesSnapshot = null
        if (eraseGestureModified && snapshot != null) {
            eraseGestureModified = false
            _activeEditor.update { state ->
                state?.copy(
                    canvasHistory = state.canvasHistory.recordBeforeChange(
                        previousStrokes = snapshot,
                        previousImages = imgSnapshot ?: state.images
                    )
                )
            }
            persistActiveEditorDebounced()
        }
    }

    fun undoStroke() {
        var didUndo = false
        _activeEditor.update { state ->
            if (state == null) return@update null
            val popped = state.canvasHistory.undoState(state.strokes, state.images) ?: return@update state
            didUndo = true
            state.copy(
                strokes = popped.first.strokes,
                images = popped.first.images,
                canvasHistory = popped.second,
                lassoSelection = LassoHitSelection()
            )
        }
        if (didUndo) persistActiveEditorDebounced()
    }

    fun redoStroke() {
        var didRedo = false
        _activeEditor.update { state ->
            if (state == null) return@update null
            val restored = state.canvasHistory.redoState(state.strokes, state.images) ?: return@update state
            didRedo = true
            state.copy(
                strokes = restored.first.strokes,
                images = restored.first.images,
                canvasHistory = restored.second,
                lassoSelection = LassoHitSelection()
            )
        }
        if (didRedo) persistActiveEditorDebounced()
    }

    fun clearCanvas() {
        var didClear = false
        _activeEditor.update { state ->
            val hasCanvasContent = state != null &&
                (state.strokes.isNotEmpty() || state.images.any { it.placedOnCanvas })
            if (state == null || !hasCanvasContent) state
            else {
                didClear = true
                state.copy(
                    canvasHistory = state.canvasHistory.recordBeforeChange(state.strokes, state.images),
                    strokes = emptyList(),
                    images = state.images.filterNot { it.placedOnCanvas },
                    lassoSelection = LassoHitSelection()
                )
            }
        }
        if (didClear) persistActiveEditorDebounced()
    }

    fun exportCanvasAsPngToGallery(
        context: Context,
        isDarkSurface: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        val current = _activeEditor.value ?: return
        viewModelScope.launch {
            val saved = CanvasPngExportHelper.saveCanvasPngToGallery(
                context = context,
                noteTitle = current.title,
                strokes = current.strokes,
                template = current.template,
                isDarkSurface = isDarkSurface,
                canvasImages = current.images
            )
            onResult(saved)
        }
    }

    fun saveAndCloseEditor() {
        val current = _activeEditor.value ?: return
        _activeEditor.value = null
        viewModelScope.launch {
            if (current.hasAnyContent() || current.noteId != 0L) {
                repository.saveNote(current.toEntity())
            }
        }
    }

    fun trashCurrentNote() {
        val current = _activeEditor.value ?: return
        _activeEditor.value = null
        if (current.noteId != 0L) {
            viewModelScope.launch {
                repository.moveToTrash(current.toEntity())
            }
        }
    }

    fun restoreCurrentNote() {
        val current = _activeEditor.value ?: return
        _activeEditor.update { it?.copy(isTrashed = false) }
        if (current.noteId != 0L) {
            viewModelScope.launch {
                repository.restoreFromTrash(current.toEntity())
            }
        }
    }

    fun deleteCurrentNoteForever() {
        val current = _activeEditor.value ?: return
        _activeEditor.value = null
        if (current.noteId != 0L) {
            viewModelScope.launch {
                current.images.forEach { NoteImageStorageHelper.deleteLocalImage(it.localPath) }
                repository.deletePermanently(current.noteId)
            }
        }
    }

    fun toggleNoteFavoriteFromList(note: NoteEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(note)
        }
    }

    fun moveNoteToFolderFromWorkspace(note: NoteEntity, targetFolderId: Long?) {
        viewModelScope.launch {
            repository.moveNoteToFolder(note, targetFolderId)
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            repository.emptyTrash()
        }
    }

    fun exportCurrentNotePdf(context: Context, onResult: (Boolean) -> Unit) {
        val current = _activeEditor.value ?: return
        viewModelScope.launch {
            val savedId = repository.saveNote(current.toEntity())
            val entity = current.copy(noteId = savedId).toEntity()
            val ok = PdfExportHelper.exportAndShareNotePdf(
                context = context,
                note = entity,
                checklist = current.checklist,
                strokes = current.strokes,
                images = current.images
            )
            onResult(ok)
        }
    }

    fun pinCurrentNoteToHomeWidget(context: Context, onResult: (Boolean) -> Unit) {
        val current = _activeEditor.value ?: return
        viewModelScope.launch {
            val savedId = repository.saveNote(current.toEntity())
            if (current.noteId == 0L) {
                _activeEditor.update { it?.copy(noteId = savedId) }
            }
            val requested = NoteWidgetHelper.requestPinNotePreviewWidget(context, savedId)
            onResult(requested)
        }
    }

    private fun persistActiveEditorDebounced() {
        val snapshot = _activeEditor.value ?: return
        if (!snapshot.hasAnyContent() && snapshot.noteId == 0L) return

        viewModelScope.launch {
            val id = repository.saveNote(snapshot.toEntity())
            if (snapshot.noteId == 0L) {
                _activeEditor.update { current ->
                    if (current != null && current.noteId == 0L) current.copy(noteId = id) else current
                }
            }
        }
    }

    private fun EditorState.hasAnyContent(): Boolean {
        return title.isNotBlank() ||
            body.isNotBlank() ||
            checklist.any { it.text.isNotBlank() } ||
            images.isNotEmpty() ||
            strokes.isNotEmpty()
    }

    private fun EditorState.toEntity(): NoteEntity {
        val cleanedChecklist = checklist.filter { it.text.isNotBlank() || checklist.size <= 1 }
        return NoteEntity(
            id = noteId,
            title = title.trimEnd(),
            body = body,
            checklistJson = NoteSerializer.encodeChecklist(cleanedChecklist),
            strokesJson = NoteSerializer.encodeStrokes(strokes),
            imagesJson = NoteSerializer.encodeImages(images),
            template = template.name,
            folderId = folderId,
            isFavorite = isFavorite,
            isPinned = isPinned,
            isTrashed = isTrashed,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis()
        )
    }

    companion object {
        fun computeToggledGridTemplate(
            currentTemplate: PaperTemplate,
            previousNonGridTemplate: PaperTemplate = PaperTemplate.BLANK
        ): PaperTemplate {
            return if (currentTemplate == PaperTemplate.GRID) {
                if (previousNonGridTemplate == PaperTemplate.GRID) {
                    PaperTemplate.BLANK
                } else {
                    previousNonGridTemplate
                }
            } else {
                PaperTemplate.GRID
            }
        }

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = NoteDatabase.getInstance(context)
                    val repo = NoteRepository(db.noteDao(), context.applicationContext)
                    return NotebookViewModel(repo) as T
                }
            }
    }
}
