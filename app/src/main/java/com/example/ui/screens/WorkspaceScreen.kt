package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.data.EditorMode
import com.example.data.FolderEntity
import com.example.data.NoteEntity
import com.example.ui.NoteFilter
import com.example.ui.WorkspaceUiState
import com.example.ui.components.NoteCard
import kotlinx.coroutines.launch

private const val UNFILED_DROP_TARGET_ID = -1L

@Composable
fun WorkspaceScreen(
    uiState: WorkspaceUiState,
    isDarkTheme: Boolean,
    onSelectFilter: (NoteFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleGridView: () -> Unit,
    onCreateNote: (EditorMode) -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    onToggleNoteFavorite: (NoteEntity) -> Unit,
    onMoveNoteToFolder: (NoteEntity, Long?) -> Unit = { _, _ -> },
    onToggleSelectNote: (Long) -> Unit = {},
    onSelectNote: (Long) -> Unit = {},
    onSelectAllNotes: () -> Unit = {},
    onClearNoteSelection: () -> Unit = {},
    onDeleteSelectedNotes: ((Int, Boolean) -> Unit) -> Unit = {},
    onPinQuickCaptureWidget: (() -> Boolean)? = null,
    onCreateFolder: (String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onEmptyTrash: () -> Unit
) {
    BackHandler(enabled = uiState.selectedNoteIds.isNotEmpty()) {
        onClearNoteSelection()
    }

    var showCreateFolderDialog by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpanded = maxWidth >= 800.dp
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Shared Workspace Drag-and-Drop State (so tablet sidebar folders also act as drop zones)
        val dropTargetRects = remember { mutableStateMapOf<Long, Rect>() }
        var draggingNote by remember { mutableStateOf<NoteEntity?>(null) }
        var hoveredDropTargetId by remember { mutableStateOf<Long?>(null) }
        val snackbarHostState = remember { SnackbarHostState() }
        val context = LocalContext.current

        val handleDropOnTarget: (NoteEntity, Long) -> Unit = { note, targetKey ->
            val targetFolderId = if (targetKey == UNFILED_DROP_TARGET_ID) null else targetKey
            if (note.folderId != targetFolderId) {
                onMoveNoteToFolder(note, targetFolderId)
                val folderName = uiState.folders.firstOrNull { it.id == targetFolderId }?.name
                scope.launch {
                    val message = if (folderName != null) {
                        context.getString(R.string.drop_moved_to_folder_message, folderName)
                    } else {
                        context.getString(R.string.drop_moved_to_unfiled_message)
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }

        if (isExpanded) {
            // Canonical Tablet / Foldable Layout: Permanent Sidebar + Staggered Workspace
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                SidebarContent(
                    uiState = uiState,
                    hoveredDropTargetId = hoveredDropTargetId,
                    onRegisterDropBounds = { id, rect ->
                        if (rect == null) dropTargetRects.remove(id) else dropTargetRects[id] = rect
                    },
                    onSelectFilter = onSelectFilter,
                    onOpenNewFolderDialog = { showCreateFolderDialog = true },
                    onDeleteFolder = onDeleteFolder,
                    onPinQuickWidget = onPinQuickCaptureWidget?.let { pinFn ->
                        {
                            val ok = pinFn()
                            scope.launch {
                                val msgRes = if (ok) {
                                    R.string.widget_pinned_requested_message
                                } else {
                                    R.string.widget_pin_unsupported_message
                                }
                                snackbarHostState.showSnackbar(context.getString(msgRes))
                            }
                        }
                    },
                    modifier = Modifier
                        .width(268.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outline)
                )
                WorkspaceMainPane(
                    uiState = uiState,
                    isDarkTheme = isDarkTheme,
                    showMenuButton = false,
                    snackbarHostState = snackbarHostState,
                    draggingNote = draggingNote,
                    hoveredDropTargetId = hoveredDropTargetId,
                    dropTargetRects = dropTargetRects,
                    onStartDraggingNote = { draggingNote = it },
                    onHoverDropTarget = { hoveredDropTargetId = it },
                    onFinishDraggingNote = { note, targetKey ->
                        if (note != null && targetKey != null) {
                            handleDropOnTarget(note, targetKey)
                        }
                        draggingNote = null
                        hoveredDropTargetId = null
                    },
                    onMenuClick = {},
                    onSelectFilter = onSelectFilter,
                    onSearchChange = onSearchChange,
                    onToggleTheme = onToggleTheme,
                    onToggleGridView = onToggleGridView,
                    onCreateNote = onCreateNote,
                    onOpenNote = onOpenNote,
                    onToggleNoteFavorite = onToggleNoteFavorite,
                    onToggleSelectNote = onToggleSelectNote,
                    onSelectNote = onSelectNote,
                    onSelectAllNotes = onSelectAllNotes,
                    onClearNoteSelection = onClearNoteSelection,
                    onDeleteSelectedNotes = onDeleteSelectedNotes,
                    onEmptyTrash = onEmptyTrash,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Handheld Layout: Clean Modal Sheet Drawer
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = MaterialTheme.colorScheme.surface,
                        drawerContentColor = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(288.dp)
                    ) {
                        SidebarContent(
                            uiState = uiState,
                            hoveredDropTargetId = null,
                            onRegisterDropBounds = { _, _ -> },
                            onSelectFilter = { filter ->
                                onSelectFilter(filter)
                                scope.launch { drawerState.close() }
                            },
                            onOpenNewFolderDialog = { showCreateFolderDialog = true },
                            onDeleteFolder = onDeleteFolder,
                            onPinQuickWidget = onPinQuickCaptureWidget?.let { pinFn ->
                                {
                                    scope.launch { drawerState.close() }
                                    val ok = pinFn()
                                    scope.launch {
                                        val msgRes = if (ok) {
                                            R.string.widget_pinned_requested_message
                                        } else {
                                            R.string.widget_pin_unsupported_message
                                        }
                                        snackbarHostState.showSnackbar(context.getString(msgRes))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            ) {
                WorkspaceMainPane(
                    uiState = uiState,
                    isDarkTheme = isDarkTheme,
                    showMenuButton = true,
                    snackbarHostState = snackbarHostState,
                    draggingNote = draggingNote,
                    hoveredDropTargetId = hoveredDropTargetId,
                    dropTargetRects = dropTargetRects,
                    onStartDraggingNote = { draggingNote = it },
                    onHoverDropTarget = { hoveredDropTargetId = it },
                    onFinishDraggingNote = { note, targetKey ->
                        if (note != null && targetKey != null) {
                            handleDropOnTarget(note, targetKey)
                        }
                        draggingNote = null
                        hoveredDropTargetId = null
                    },
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSelectFilter = onSelectFilter,
                    onSearchChange = onSearchChange,
                    onToggleTheme = onToggleTheme,
                    onToggleGridView = onToggleGridView,
                    onCreateNote = onCreateNote,
                    onOpenNote = onOpenNote,
                    onToggleNoteFavorite = onToggleNoteFavorite,
                    onToggleSelectNote = onToggleSelectNote,
                    onSelectNote = onSelectNote,
                    onSelectAllNotes = onSelectAllNotes,
                    onClearNoteSelection = onClearNoteSelection,
                    onDeleteSelectedNotes = onDeleteSelectedNotes,
                    onEmptyTrash = onEmptyTrash
                )
            }
        }
    }

    if (showCreateFolderDialog) {
        CreateFolderDialog(
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                onCreateFolder(name)
                showCreateFolderDialog = false
            }
        )
    }
}

@Composable
private fun WorkspaceMainPane(
    uiState: WorkspaceUiState,
    isDarkTheme: Boolean,
    showMenuButton: Boolean,
    snackbarHostState: SnackbarHostState,
    draggingNote: NoteEntity?,
    hoveredDropTargetId: Long?,
    dropTargetRects: MutableMap<Long, Rect>,
    onStartDraggingNote: (NoteEntity) -> Unit,
    onHoverDropTarget: (Long?) -> Unit,
    onFinishDraggingNote: (NoteEntity?, Long?) -> Unit,
    onMenuClick: () -> Unit,
    onSelectFilter: (NoteFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onToggleTheme: () -> Unit,
    onToggleGridView: () -> Unit,
    onCreateNote: (EditorMode) -> Unit,
    onOpenNote: (NoteEntity) -> Unit,
    onToggleNoteFavorite: (NoteEntity) -> Unit,
    onToggleSelectNote: (Long) -> Unit,
    onSelectNote: (Long) -> Unit,
    onSelectAllNotes: () -> Unit,
    onClearNoteSelection: () -> Unit,
    onDeleteSelectedNotes: ((Int, Boolean) -> Unit) -> Unit,
    onEmptyTrash: () -> Unit,
    modifier: Modifier = Modifier
) {
    val folderMap = remember(uiState.folders) { uiState.folders.associate { it.id to it.name } }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isSelectionMode = uiState.selectedNoteIds.isNotEmpty()

    val headingTitle = when (val f = uiState.activeFilter) {
        NoteFilter.All -> stringResource(R.string.nav_all_notes)
        NoteFilter.Favorites -> stringResource(R.string.nav_favorites)
        NoteFilter.Trash -> stringResource(R.string.nav_trash)
        is NoteFilter.Folder -> f.folderName
    }

    val count = uiState.visibleNotes.size
    val countLabel = if (count == 1) {
        stringResource(R.string.notes_count_single)
    } else {
        stringResource(R.string.notes_count_format, count)
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.activeFilter != NoteFilter.Trash && !isSelectionMode) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Secondary quick S-Pen Ink floating island button
                    IconButton(
                        onClick = { onCreateNote(EditorMode.INK) },
                        modifier = Modifier
                            .size(48.dp)
                            .shadow(
                                elevation = 3.dp,
                                shape = MaterialTheme.shapes.medium,
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                            )
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                            .testTag("quick_ink_note_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Draw,
                            contentDescription = stringResource(R.string.editor_mode_ink),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    ExtendedFloatingActionButton(
                        onClick = { onCreateNote(EditorMode.PAGE) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.testTag("create_note_fab")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.empty_notes_action),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Minimalist Excalidraw Floating Island Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (showMenuButton) {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier
                                .size(48.dp)
                                .shadow(1.5.dp, MaterialTheme.shapes.small)
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                                .testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Menu,
                                contentDescription = stringResource(R.string.nav_menu_desc),
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Column {
                        Text(
                            text = headingTitle,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = countLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (uiState.activeFilter == NoteFilter.Trash && uiState.visibleNotes.isNotEmpty()) {
                        TextButton(
                            onClick = onEmptyTrash,
                            modifier = Modifier.testTag("empty_trash_button")
                        ) {
                            Text(
                                text = stringResource(R.string.action_empty_trash),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(
                        onClick = onToggleGridView,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_layout_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.prefs.isGridView) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                            contentDescription = stringResource(R.string.toggle_layout_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("toggle_theme_button")
                    ) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                            contentDescription = stringResource(R.string.toggle_theme_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated Long-Press Multi-Select & Batch Delete Bar
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(130))
            ) {
                Column {
                    WorkspaceSelectionActionBar(
                        selectedCount = uiState.selectedNoteIds.size,
                        onSelectAll = onSelectAllNotes,
                        onDeleteSelected = {
                            onDeleteSelectedNotes { countDeleted, wasPermanent ->
                                scope.launch {
                                    val msg = if (wasPermanent) {
                                        context.getString(R.string.selection_deleted_notes_message, countDeleted)
                                    } else {
                                        context.getString(R.string.selection_trashed_notes_message, countDeleted)
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        },
                        onClearSelection = onClearNoteSelection
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // Clean shadcn-style Search Input
            SearchInputBar(
                query = uiState.searchQuery,
                onQueryChange = onSearchChange
            )

            // Folder Filter & Drop-Zone Bar (tap to filter or drag & drop any note onto a folder chip)
            if (uiState.activeFilter != NoteFilter.Trash && uiState.folders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                FolderDropBar(
                    folders = uiState.folders,
                    activeFilter = uiState.activeFilter,
                    isDraggingNote = draggingNote != null,
                    hoveredDropTargetId = hoveredDropTargetId,
                    onRegisterDropBounds = { id, rect ->
                        if (rect == null) dropTargetRects.remove(id) else dropTargetRects[id] = rect
                    },
                    onSelectFilter = onSelectFilter
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Notes Grid / List or Calm Empty State
            if (uiState.visibleNotes.isEmpty() && uiState.isLoaded) {
                EmptyWorkspaceView(
                    filter = uiState.activeFilter,
                    hasSearchQuery = uiState.searchQuery.isNotBlank(),
                    onCreateNote = { onCreateNote(EditorMode.PAGE) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                val columns = if (uiState.prefs.isGridView) {
                    StaggeredGridCells.Adaptive(minSize = 164.dp)
                } else {
                    StaggeredGridCells.Fixed(1)
                }

                LazyVerticalStaggeredGrid(
                    columns = columns,
                    userScrollEnabled = draggingNote == null,
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("notes_grid")
                ) {
                    items(
                        items = uiState.visibleNotes,
                        key = { it.id }
                    ) { note ->
                        val isCardSelected = note.id in uiState.selectedNoteIds
                        DraggableWorkspaceNoteItem(
                            note = note,
                            canDrag = !note.isTrashed && uiState.folders.isNotEmpty() && !isSelectionMode,
                            isBeingDragged = draggingNote?.id == note.id,
                            isSelected = isCardSelected,
                            isSelectionMode = isSelectionMode,
                            folderName = note.folderId?.let { folderMap[it] },
                            isDarkTheme = isDarkTheme,
                            dropTargetRects = dropTargetRects,
                            onStartDrag = {
                                onClearNoteSelection()
                                onStartDraggingNote(note)
                            },
                            onHoverTarget = onHoverDropTarget,
                            onEndDrag = { hitKey -> onFinishDraggingNote(note, hitKey) },
                            onClick = {
                                if (isSelectionMode) {
                                    onToggleSelectNote(note.id)
                                } else {
                                    onOpenNote(note)
                                }
                            },
                            onLongSelect = {
                                if (isSelectionMode) {
                                    onToggleSelectNote(note.id)
                                } else {
                                    onSelectNote(note.id)
                                }
                            },
                            onToggleFavorite = { onToggleNoteFavorite(note) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSelectionActionBar(
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = MaterialTheme.shapes.medium,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.5.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.medium)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("workspace_selection_bar")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onClearSelection,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("workspace_clear_selection_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.selection_clear_desc),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                text = stringResource(R.string.selection_count_format, selectedCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.background)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable(onClick = onSelectAll)
                    .padding(horizontal = 12.dp)
                    .testTag("workspace_select_all_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.selection_select_all),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
                    .border(1.dp, MaterialTheme.colorScheme.error, MaterialTheme.shapes.small)
                    .clickable(onClick = onDeleteSelected)
                    .padding(horizontal = 12.dp)
                    .testTag("workspace_delete_selected_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.selection_delete_action),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DraggableWorkspaceNoteItem(
    note: NoteEntity,
    canDrag: Boolean,
    isBeingDragged: Boolean,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    folderName: String?,
    isDarkTheme: Boolean,
    dropTargetRects: Map<Long, Rect>,
    onStartDrag: () -> Unit,
    onHoverTarget: (Long?) -> Unit,
    onEndDrag: (Long?) -> Unit,
    onClick: () -> Unit,
    onLongSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var cardRootOrigin by remember { mutableStateOf(Offset.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var activeHitKey by remember { mutableStateOf<Long?>(null) }
    var enteredFolderDrag by remember { mutableStateOf(false) }

    val dragGestureModifier = if (canDrag) {
        Modifier.pointerInput(note.id, dropTargetRects) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    dragOffset = Offset.Zero
                    activeHitKey = null
                    enteredFolderDrag = false
                    // Immediately select note on stationary long-press; if user drags further, transition to folder drag
                    onLongSelect()
                },
                onDragCancel = {
                    val wasFolderDrag = enteredFolderDrag
                    dragOffset = Offset.Zero
                    activeHitKey = null
                    enteredFolderDrag = false
                    if (wasFolderDrag) {
                        onEndDrag(null)
                    }
                },
                onDragEnd = {
                    val wasFolderDrag = enteredFolderDrag
                    val droppedKey = activeHitKey
                    dragOffset = Offset.Zero
                    activeHitKey = null
                    enteredFolderDrag = false
                    if (wasFolderDrag) {
                        onEndDrag(droppedKey)
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragOffset += dragAmount
                    if (!enteredFolderDrag && dragOffset.getDistance() >= 20f) {
                        enteredFolderDrag = true
                        onStartDrag()
                    }
                    if (enteredFolderDrag) {
                        val pointerInRoot = cardRootOrigin + dragOffset + change.position
                        val hit = dropTargetRects.entries.firstOrNull { it.value.contains(pointerInRoot) }?.key
                        if (hit != activeHitKey) {
                            activeHitKey = hit
                            onHoverTarget(hit)
                        }
                    }
                }
            )
        }
    } else {
        Modifier
    }

    NoteCard(
        note = note,
        folderName = folderName,
        isDarkTheme = isDarkTheme,
        isSelected = isSelected,
        isSelectionMode = isSelectionMode,
        onClick = onClick,
        onLongClick = if (!canDrag) onLongSelect else null,
        onToggleFavorite = onToggleFavorite,
        modifier = Modifier
            .onGloballyPositioned { coords ->
                if (!isBeingDragged) {
                    cardRootOrigin = coords.boundsInRoot().topLeft
                }
            }
            .zIndex(if (isBeingDragged) 20f else 0f)
            .graphicsLayer {
                translationX = if (isBeingDragged) dragOffset.x else 0f
                translationY = if (isBeingDragged) dragOffset.y else 0f
                scaleX = if (isBeingDragged) 0.94f else 1f
                scaleY = if (isBeingDragged) 0.94f else 1f
                alpha = if (isBeingDragged) 0.92f else 1f
            }
            .then(dragGestureModifier)
    )
}

@Composable
private fun FolderDropBar(
    folders: List<FolderEntity>,
    activeFilter: NoteFilter,
    isDraggingNote: Boolean,
    hoveredDropTargetId: Long?,
    onRegisterDropBounds: (Long, Rect?) -> Unit,
    onSelectFilter: (NoteFilter) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AnimatedVisibility(
            visible = isDraggingNote,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(140))
        ) {
            Text(
                text = stringResource(R.string.drop_zone_hint_Move_to_folder),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("workspace_folder_drop_bar")
        ) {
            // Unfiled / All Notes chip (acts as drop zone to unassign folder when dragging)
            FolderDropChip(
                targetKey = UNFILED_DROP_TARGET_ID,
                icon = if (isDraggingNote) Icons.Outlined.FolderOff else Icons.Outlined.Description,
                label = if (isDraggingNote) {
                    stringResource(R.string.editor_unfiled)
                } else {
                    stringResource(R.string.nav_all_notes)
                },
                selected = activeFilter == NoteFilter.All && !isDraggingNote,
                isHoveredTarget = hoveredDropTargetId == UNFILED_DROP_TARGET_ID,
                isDraggingNote = isDraggingNote,
                onRegisterBounds = onRegisterDropBounds,
                onClick = { onSelectFilter(NoteFilter.All) },
                tag = "folder_drop_chip_unfiled"
            )

            folders.forEach { folder ->
                val isSelected = (activeFilter as? NoteFilter.Folder)?.folderId == folder.id
                FolderDropChip(
                    targetKey = folder.id,
                    icon = Icons.Outlined.Folder,
                    label = folder.name,
                    selected = isSelected,
                    isHoveredTarget = hoveredDropTargetId == folder.id,
                    isDraggingNote = isDraggingNote,
                    onRegisterBounds = onRegisterDropBounds,
                    onClick = { onSelectFilter(NoteFilter.Folder(folder.id, folder.name)) },
                    tag = "folder_drop_chip_${folder.id}"
                )
            }
        }
    }
}

@Composable
private fun FolderDropChip(
    targetKey: Long,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    isHoveredTarget: Boolean,
    isDraggingNote: Boolean,
    onRegisterBounds: (Long, Rect?) -> Unit,
    onClick: () -> Unit,
    tag: String
) {
    DisposableEffect(targetKey) {
        onDispose { onRegisterBounds(targetKey, null) }
    }

    val bg = when {
        isHoveredTarget -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val borderCol = when {
        isHoveredTarget || isDraggingNote -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val fg = when {
        isHoveredTarget -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .height(40.dp)
            .onGloballyPositioned { coords ->
                onRegisterBounds(targetKey, coords.boundsInRoot())
            }
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .border(
                width = if (isHoveredTarget) 2.dp else 1.dp,
                color = borderCol,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            maxLines = 1
        )
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(
                elevation = 2.dp,
                shape = MaterialTheme.shapes.medium,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                spotColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)
            )
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_notes_input")
            )
        }
        if (query.isNotEmpty()) {
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.search_clear_desc),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun SidebarContent(
    uiState: WorkspaceUiState,
    hoveredDropTargetId: Long?,
    onRegisterDropBounds: (Long, Rect?) -> Unit,
    onSelectFilter: (NoteFilter) -> Unit,
    onOpenNewFolderDialog: () -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onPinQuickWidget: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(1.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Draw,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        SidebarItem(
            icon = Icons.Outlined.Description,
            label = stringResource(R.string.nav_all_notes),
            count = uiState.totalActiveCount,
            selected = uiState.activeFilter == NoteFilter.All,
            isDropHovered = false,
            onClick = { onSelectFilter(NoteFilter.All) },
            tag = "drawer_all_notes"
        )
        Spacer(modifier = Modifier.height(4.dp))
        SidebarItem(
            icon = Icons.Outlined.StarOutline,
            label = stringResource(R.string.nav_favorites),
            count = uiState.favoritesCount,
            selected = uiState.activeFilter == NoteFilter.Favorites,
            isDropHovered = false,
            onClick = { onSelectFilter(NoteFilter.Favorites) },
            tag = "drawer_favorites"
        )
        Spacer(modifier = Modifier.height(4.dp))
        SidebarItem(
            icon = Icons.Outlined.DeleteOutline,
            label = stringResource(R.string.nav_trash),
            count = uiState.trashCount,
            selected = uiState.activeFilter == NoteFilter.Trash,
            isDropHovered = false,
            onClick = { onSelectFilter(NoteFilter.Trash) },
            tag = "drawer_trash"
        )

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.nav_folders),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = onOpenNewFolderDialog,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("add_folder_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.CreateNewFolder,
                    contentDescription = stringResource(R.string.nav_new_folder),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        uiState.folders.forEach { folder ->
            val selected = (uiState.activeFilter as? NoteFilter.Folder)?.folderId == folder.id
            val isDropHovered = hoveredDropTargetId == folder.id
            DisposableEffect(folder.id) {
                onDispose { onRegisterDropBounds(folder.id, null) }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        onRegisterDropBounds(folder.id, coords.boundsInRoot())
                    }
            ) {
                SidebarItem(
                    icon = Icons.Outlined.Folder,
                    label = folder.name,
                    count = uiState.folderCounts[folder.id] ?: 0,
                    selected = selected,
                    isDropHovered = isDropHovered,
                    onClick = { onSelectFilter(NoteFilter.Folder(folder.id, folder.name)) },
                    tag = "drawer_folder_${folder.id}",
                    modifier = Modifier.weight(1f)
                )
                if (selected) {
                    IconButton(
                        onClick = { onDeleteFolder(folder.id) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stringResource(R.string.editor_delete_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (onPinQuickWidget != null) {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.nav_widgets_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .clickable(onClick = onPinQuickWidget)
                    .padding(horizontal = 12.dp)
                    .testTag("drawer_pin_quick_widget")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Widgets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.widget_pin_quick_capture_action),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    isDropHovered: Boolean,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier
) {
    val bg = when {
        isDropHovered -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val fg = when {
        isDropHovered -> MaterialTheme.colorScheme.onPrimary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(MaterialTheme.shapes.small)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(tag)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isDropHovered) {
                    MaterialTheme.colorScheme.onPrimary
                } else if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    fg
                },
                maxLines = 1
            )
        }
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isDropHovered) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyWorkspaceView(
    filter: NoteFilter,
    hasSearchQuery: Boolean,
    onCreateNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (titleRes, subtitleRes) = when {
        hasSearchQuery -> R.string.empty_search_title to R.string.empty_search_subtitle
        filter == NoteFilter.Favorites -> R.string.empty_favorites_title to R.string.empty_favorites_subtitle
        filter == NoteFilter.Trash -> R.string.empty_trash_title to R.string.empty_trash_subtitle
        else -> R.string.empty_notes_title to R.string.empty_notes_subtitle
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .padding(24.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_excalidraw_empty_1793811079426),
                contentDescription = stringResource(R.string.empty_illustration_desc),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
                    .shadow(2.dp, MaterialTheme.shapes.medium)
                    .clip(MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(subtitleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!hasSearchQuery && filter != NoteFilter.Trash && filter != NoteFilter.Favorites) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onCreateNote,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.testTag("empty_create_note_button")
                ) {
                    Text(
                        text = stringResource(R.string.empty_notes_action),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(R.string.folder_dialog_title),
                style = MaterialTheme.typography.headlineMedium
            )
        },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                singleLine = true,
                label = { Text(stringResource(R.string.folder_name_label)) },
                placeholder = { Text(stringResource(R.string.folder_name_placeholder)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("folder_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank(),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.testTag("confirm_create_folder_button")
            ) {
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
