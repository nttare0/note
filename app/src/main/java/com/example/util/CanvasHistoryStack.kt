package com.example.util

import com.example.data.InkStroke
import com.example.data.NoteImageAttachment

/**
 * Immutable snapshot of both freehand strokes and canvas/note images so any pen stroke,
 * eraser pass, canvas clear, or Lasso move/scale/rotate/delete can be undone and redone cleanly.
 */
data class CanvasSnapshot(
    val strokes: List<InkStroke> = emptyList(),
    val images: List<NoteImageAttachment> = emptyList()
)

/**
 * Pure, bounded Undo/Redo stack specifically designed for freehand drawing, erasing,
 * and Lasso transformations of strokes and canvas images.
 */
data class CanvasHistoryStack(
    val undoSnapshots: List<CanvasSnapshot> = emptyList(),
    val redoSnapshots: List<CanvasSnapshot> = emptyList(),
    val maxDepth: Int = 50
) {
    val canUndo: Boolean
        get() = undoSnapshots.isNotEmpty()

    val canRedo: Boolean
        get() = redoSnapshots.isNotEmpty()

    fun recordBeforeChange(
        previousStrokes: List<InkStroke>,
        previousImages: List<NoteImageAttachment> = emptyList()
    ): CanvasHistoryStack {
        val snap = CanvasSnapshot(strokes = previousStrokes, images = previousImages)
        return copy(
            undoSnapshots = (undoSnapshots + listOf(snap)).takeLast(maxDepth),
            redoSnapshots = emptyList()
        )
    }

    fun undoState(
        currentStrokes: List<InkStroke>,
        currentImages: List<NoteImageAttachment> = emptyList()
    ): Pair<CanvasSnapshot, CanvasHistoryStack>? {
        if (undoSnapshots.isEmpty()) return null
        val target = undoSnapshots.last()
        val currentSnap = CanvasSnapshot(strokes = currentStrokes, images = currentImages)
        val nextHistory = copy(
            undoSnapshots = undoSnapshots.dropLast(1),
            redoSnapshots = (redoSnapshots + listOf(currentSnap)).takeLast(maxDepth)
        )
        return target to nextHistory
    }

    fun redoState(
        currentStrokes: List<InkStroke>,
        currentImages: List<NoteImageAttachment> = emptyList()
    ): Pair<CanvasSnapshot, CanvasHistoryStack>? {
        if (redoSnapshots.isEmpty()) return null
        val target = redoSnapshots.last()
        val currentSnap = CanvasSnapshot(strokes = currentStrokes, images = currentImages)
        val nextHistory = copy(
            undoSnapshots = (undoSnapshots + listOf(currentSnap)).takeLast(maxDepth),
            redoSnapshots = redoSnapshots.dropLast(1)
        )
        return target to nextHistory
    }

    // Convenience overload preserving existing stroke-only unit tests
    fun undo(currentStrokes: List<InkStroke>): Pair<List<InkStroke>, CanvasHistoryStack>? {
        val popped = undoState(currentStrokes) ?: return null
        return popped.first.strokes to popped.second
    }

    fun redo(currentStrokes: List<InkStroke>): Pair<List<InkStroke>, CanvasHistoryStack>? {
        val restored = redoState(currentStrokes) ?: return null
        return restored.first.strokes to restored.second
    }
}
