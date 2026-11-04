package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAllNotes(): Flow<List<NoteEntity>>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun countNotes(): Int

    @Query("SELECT COUNT(*) FROM notes WHERE isTrashed = 0")
    suspend fun countActiveNotes(): Int

    @Query("SELECT * FROM notes WHERE isTrashed = 0 ORDER BY isPinned DESC, updatedAt DESC LIMIT 1")
    suspend fun getTopPreviewNote(): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id IN (:noteIds)")
    suspend fun getNotesByIds(noteIds: List<Long>): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isTrashed = 1")
    suspend fun getTrashedNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("UPDATE notes SET isTrashed = 1, isPinned = 0, updatedAt = :updatedAt WHERE id IN (:noteIds)")
    suspend fun moveNotesToTrash(noteIds: List<Long>, updatedAt: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNotePermanently(id: Long)

    @Query("DELETE FROM notes WHERE id IN (:noteIds)")
    suspend fun deleteNotesPermanently(noteIds: List<Long>)

    @Query("DELETE FROM notes WHERE isTrashed = 1")
    suspend fun emptyTrash()

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeFolders(): Flow<List<FolderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity): Long

    @Query("DELETE FROM folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: Long)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun unassignFolderNotes(folderId: Long)

    // Custom Ink Palette SQLite Table Operations
    @Query("SELECT * FROM custom_ink_colors ORDER BY createdAt ASC")
    fun observeCustomInkColors(): Flow<List<CustomInkColorEntity>>

    @Query("SELECT * FROM custom_ink_colors WHERE colorKey = :colorKey LIMIT 1")
    suspend fun getCustomInkColorByKey(colorKey: String): CustomInkColorEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomInkColor(color: CustomInkColorEntity): Long

    @Query("DELETE FROM custom_ink_colors WHERE colorKey = :colorKey")
    suspend fun deleteCustomInkColorByKey(colorKey: String)

    // App Preferences SQLite Table Operations
    @Query("SELECT * FROM app_preferences")
    fun observeAppPreferences(): Flow<List<AppPreferenceEntity>>

    @Query("SELECT prefValue FROM app_preferences WHERE prefKey = :key LIMIT 1")
    suspend fun getAppPreferenceValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAppPreference(preference: AppPreferenceEntity)
}

@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        CustomInkColorEntity::class,
        AppPreferenceEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NoteDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN imagesJson TEXT NOT NULL DEFAULT '[]'")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `custom_ink_colors` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `colorKey` TEXT NOT NULL,
                        `label` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `app_preferences` (
                        `prefKey` TEXT NOT NULL,
                        `prefValue` TEXT NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`prefKey`)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "notestudio_room.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
