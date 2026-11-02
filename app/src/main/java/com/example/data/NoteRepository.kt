package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.widget.NoteWidgetHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "notestudio_prefs")

data class UserPrefs(
    val isDarkThemeOverride: Boolean? = null, // null = follow system
    val isGridView: Boolean = true
)

class NoteRepository(
    private val dao: NoteDao,
    private val appContext: Context
) {
    private val darkModeSetKey = booleanPreferencesKey("dark_mode_set")
    private val darkModeValueKey = booleanPreferencesKey("dark_mode_val")
    private val gridViewKey = booleanPreferencesKey("grid_view")
    private val seededKey = booleanPreferencesKey("seeded_initial_notes")

    val allNotes: Flow<List<NoteEntity>> = dao.observeAllNotes()
    val allFolders: Flow<List<FolderEntity>> = dao.observeFolders()

    val prefsFlow: Flow<UserPrefs> = appContext.dataStore.data.map { prefs ->
        val hasOverride = prefs[darkModeSetKey] ?: false
        val darkVal = prefs[darkModeValueKey] ?: false
        val grid = prefs[gridViewKey] ?: true
        UserPrefs(
            isDarkThemeOverride = if (hasOverride) darkVal else null,
            isGridView = grid
        )
    }

    suspend fun seedIfFirstLaunch() {
        var alreadySeeded = false
        appContext.dataStore.edit { prefs ->
            alreadySeeded = prefs[seededKey] ?: false
            if (!alreadySeeded) {
                prefs[seededKey] = true
            }
        }
        if (!alreadySeeded && dao.countNotes() == 0) {
            val specsId = dao.insertFolder(SampleNotebookData.initialFolders[0])
            val sketchesId = dao.insertFolder(SampleNotebookData.initialFolders[1])
            SampleNotebookData.buildStarterNotes(specsId, sketchesId).forEach { note ->
                dao.insertNote(note)
            }
            NoteWidgetHelper.refreshAllWidgets(appContext)
        }
    }

    suspend fun getNoteById(id: Long): NoteEntity? = dao.getNoteById(id)

    suspend fun saveNote(note: NoteEntity): Long {
        val id = if (note.id == 0L) {
            dao.insertNote(note.copy(updatedAt = System.currentTimeMillis()))
        } else {
            dao.updateNote(note.copy(updatedAt = System.currentTimeMillis()))
            note.id
        }
        NoteWidgetHelper.refreshAllWidgets(appContext)
        return id
    }

    suspend fun toggleFavorite(note: NoteEntity) {
        dao.updateNote(note.copy(isFavorite = !note.isFavorite, updatedAt = System.currentTimeMillis()))
        NoteWidgetHelper.refreshAllWidgets(appContext)
    }

    suspend fun togglePinned(note: NoteEntity) {
        dao.updateNote(note.copy(isPinned = !note.isPinned, updatedAt = System.currentTimeMillis()))
        NoteWidgetHelper.refreshAllWidgets(appContext)
    }

    suspend fun moveToTrash(note: NoteEntity) {
        dao.updateNote(note.copy(isTrashed = true, isPinned = false, updatedAt = System.currentTimeMillis()))
        NoteWidgetHelper.refreshAllWidgets(appContext)
    }

    suspend fun restoreFromTrash(note: NoteEntity) {
        dao.updateNote(note.copy(isTrashed = false, updatedAt = System.currentTimeMillis()))
        NoteWidgetHelper.refreshAllWidgets(appContext)
    }

    suspend fun deletePermanently(noteId: Long) {
        dao.deleteNotePermanently(noteId)
        NoteWidgetHelper.refreshAllWidgets(appContext)
    }

    suspend fun emptyTrash() {
        dao.emptyTrash()
        NoteWidgetHelper.refreshAllWidgets(appContext)
    }

    suspend fun createFolder(name: String): Long {
        return dao.insertFolder(FolderEntity(name = name.trim()))
    }

    suspend fun deleteFolder(folderId: Long) {
        dao.unassignFolderNotes(folderId)
        dao.deleteFolder(folderId)
        NoteWidgetHelper.refreshAllWidgets(appContext)
    }

    suspend fun moveNoteToFolder(note: NoteEntity, targetFolderId: Long?) {
        if (note.folderId == targetFolderId) return
        dao.updateNote(note.copy(folderId = targetFolderId, updatedAt = System.currentTimeMillis()))
        NoteWidgetHelper.refreshAllWidgets(appContext)
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[darkModeSetKey] = true
            prefs[darkModeValueKey] = isDark
        }
    }

    suspend fun setGridView(isGrid: Boolean) {
        appContext.dataStore.edit { prefs ->
            prefs[gridViewKey] = isGrid
        }
    }
}
