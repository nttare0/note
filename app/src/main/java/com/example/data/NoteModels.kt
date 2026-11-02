package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

enum class PaperTemplate(val labelKey: String) {
    BLANK("Blank"),
    RULED("Ruled"),
    GRID("Grid"),
    DOTTED("Dotted"),
    CORNELL("Cornell")
}

enum class InkTool {
    PEN,
    MARKER,
    ERASER,
    LASSO
}

enum class EditorMode {
    PAGE, // Combined Samsung Notes view: title + images + text/checklist + ink canvas
    TYPE, // Focused text, images & checklist
    INK   // Full-screen freehand ink canvas
}

@JsonClass(generateAdapter = true)
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f
)

@JsonClass(generateAdapter = true)
data class InkStroke(
    val id: String,
    val points: List<StrokePoint>,
    val colorKey: String, // "INK", "AMBER", "SLATE", "BLUE", "GREEN", "CORAL"
    val widthDp: Float,
    val isHighlighter: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ChecklistItem(
    val id: String,
    val text: String,
    val isChecked: Boolean = false
)

@JsonClass(generateAdapter = true)
data class NoteImageAttachment(
    val id: String,
    val localPath: String,
    val widthPx: Int = 0,
    val heightPx: Int = 0,
    val addedAt: Long = System.currentTimeMillis(),
    val placedOnCanvas: Boolean = false,
    val canvasXDp: Float = 24f,
    val canvasYDp: Float = 24f,
    val canvasWidthDp: Float = 160f,
    val canvasHeightDp: Float = 120f,
    val rotationDeg: Float = 0f
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val body: String = "",
    val checklistJson: String = "[]",
    val strokesJson: String = "[]",
    val imagesJson: String = "[]",
    val template: String = PaperTemplate.BLANK.name,
    val folderId: Long? = null,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val isTrashed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
