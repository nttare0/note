package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.util.InkPaletteHelper
import com.example.util.NoteImageStorageHelper
import com.example.widget.NoteWidgetHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

private val Context.legacyDataStore by preferencesDataStore(name = "notestudio_prefs")

data class UserPrefs(
    val isDarkThemeOverride: Boolean? = null, // null = follow system
    val isGridView: Boolean = true
)

class NoteRepository(
    private val dao: NoteDao,
    private val appContext: Context
) {
    private val legacyDarkModeSetKey = booleanPreferencesKey("dark_mode_set")
    private val legacyDarkModeValueKey = booleanPreferencesKey("dark_mode_val")
    private val legacyGridViewKey = booleanPreferencesKey("grid_view")
    private val legacySeededKey = booleanPreferencesKey("seeded_initial_notes")

    val allNotes: Flow<List<NoteEntity>> = dao.observeAllNotes()
    val allFolders: Flow<List<FolderEntity>> = dao.observeFolders()
    val customInkColors: Flow<List<String>> = dao.observeCustomInkColors().map { list ->
        list.map { it.colorKey }.distinct()
    }

    val prefsFlow: Flow<UserPrefs> = combine(
        dao.observeAppPreferences(),
        appContext.legacyDataStore.data
    ) { sqlRows, legacyPrefs ->
        val sqlMap = sqlRows.associate { it.prefKey to it.prefValue }

        val darkOverride: Boolean? = when (sqlMap[KEY_DARK_THEME_OVERRIDE]) {
            "true" -> true
            "false" -> false
            "system" -> null
            else -> {
                val legacySet = legacyPrefs[legacyDarkModeSetKey] ?: false
                if (legacySet) legacyPrefs[legacyDarkModeValueKey] ?: false else null
            }
        }

        val gridView: Boolean = sqlMap[KEY_GRID_VIEW]?.toBooleanStrictOrNull()
            ?: (legacyPrefs[legacyGridViewKey] ?: true)

        UserPrefs(
            isDarkThemeOverride = darkOverride,
            isGridView = gridView
        )
    }

    suspend fun seedIfFirstLaunch() {
        val sqlSeeded = dao.getAppPreferenceValue(KEY_SEEDED_NOTES) == "true"
        val legacySeeded = appContext.legacyDataStore.data.firstOrNull()?.get(legacySeededKey) ?: false
        if (!sqlSeeded) {
            dao.upsertAppPreference(
                AppPreferenceEntity(prefKey = KEY_SEEDED_NOTES, prefValue = "true")
            )
        }
        if (!sqlSeeded && !legacySeeded && dao.countNotes() == 0) {
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

    suspend fun moveNotesToTrash(noteIds: List<Long>) {
        if (noteIds.isEmpty()) return
        dao.moveNotesToTrash(noteIds = noteIds, updatedAt = System.currentTimeMillis())
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

    suspend fun deleteNotesPermanently(noteIds: List<Long>) {
        if (noteIds.isEmpty()) return
        val targets = dao.getNotesByIds(noteIds)
        targets.forEach { entity ->
            NoteSerializer.decodeImages(entity.imagesJson).forEach { img ->
                NoteImageStorageHelper.deleteLocalImage(img.localPath)
            }
        }
        dao.deleteNotesPermanently(noteIds)
        NoteWidgetHelper.refreshAllWidgets(appContext)
    }

    suspend fun emptyTrash() {
        val trashed = dao.getTrashedNotes()
        trashed.forEach { entity ->
            NoteSerializer.decodeImages(entity.imagesJson).forEach { img ->
                NoteImageStorageHelper.deleteLocalImage(img.localPath)
            }
        }
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

    suspend fun addCustomInkColor(rawHexKey: String): String? {
        val normalized = InkPaletteHelper.normalizeHexColorKey(rawHexKey) ?: return null
        val existing = dao.getCustomInkColorByKey(normalized)
        if (existing == null) {
            dao.insertCustomInkColor(
                CustomInkColorEntity(
                    colorKey = normalized,
                    label = normalized
                )
            )
        }
        return normalized
    }

    suspend fun removeCustomInkColor(colorKey: String) {
        val normalized = InkPaletteHelper.normalizeHexColorKey(colorKey) ?: colorKey.trim()
        dao.deleteCustomInkColorByKey(normalized)
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        dao.upsertAppPreference(
            AppPreferenceEntity(
                prefKey = KEY_DARK_THEME_OVERRIDE,
                prefValue = isDark.toString()
            )
        )
    }

    suspend fun setGridView(isGrid: Boolean) {
        dao.upsertAppPreference(
            AppPreferenceEntity(
                prefKey = KEY_GRID_VIEW,
                prefValue = isGrid.toString()
            )
        )
    }

    companion object {
        private const val KEY_SEEDED_NOTES = "seeded_initial_notes"
        private const val KEY_DARK_THEME_OVERRIDE = "dark_theme_override"
        private const val KEY_GRID_VIEW = "workspace_grid_view"
    }
}
