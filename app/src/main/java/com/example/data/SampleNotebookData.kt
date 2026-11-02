package com.example.data

import java.util.UUID

/**
 * Isolated starter notes and folders seeded once on a fresh install so the
 * digital notebook workspace immediately demonstrates Samsung Notes workflows
 * (paper templates, checklists, and vector ink sketches).
 */
object SampleNotebookData {

    val initialFolders = listOf(
        FolderEntity(name = "Product Specs"),
        FolderEntity(name = "Field Sketches")
    )

    fun buildStarterNotes(
        specsFolderId: Long,
        sketchesFolderId: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): List<NoteEntity> {
        val architectureChecklist = listOf(
            ChecklistItem(id = UUID.randomUUID().toString(), text = "Unify S-Pen ink layer with structured checklists", isChecked = true),
            ChecklistItem(id = UUID.randomUUID().toString(), text = "Add Blank, Ruled, Grid, Dotted, and Cornell templates", isChecked = true),
            ChecklistItem(id = UUID.randomUUID().toString(), text = "Verify native PDF export and share sheet", isChecked = false)
        )

        // Clean geometric wave / diagram sketch in normalized canvas space (dp coordinates)
        val sampleSketchStrokes = listOf(
            InkStroke(
                id = UUID.randomUUID().toString(),
                points = listOf(
                    StrokePoint(36f, 110f),
                    StrokePoint(92f, 52f),
                    StrokePoint(164f, 128f),
                    StrokePoint(242f, 48f),
                    StrokePoint(308f, 96f)
                ),
                colorKey = "AMBER",
                widthDp = 3.2f,
                isHighlighter = false
            ),
            InkStroke(
                id = UUID.randomUUID().toString(),
                points = listOf(
                    StrokePoint(36f, 148f),
                    StrokePoint(308f, 148f)
                ),
                colorKey = "INK",
                widthDp = 1.8f,
                isHighlighter = false
            ),
            InkStroke(
                id = UUID.randomUUID().toString(),
                points = listOf(
                    StrokePoint(76f, 88f),
                    StrokePoint(210f, 88f)
                ),
                colorKey = "AMBER",
                widthDp = 14f,
                isHighlighter = true
            )
        )

        return listOf(
            NoteEntity(
                title = "System Architecture",
                body = "Core notebook engine separates rich text blocks, actionable checklists, and pressure-smooth Bezier strokes over selectable paper grids.",
                checklistJson = NoteSerializer.encodeChecklist(architectureChecklist),
                strokesJson = "[]",
                template = PaperTemplate.GRID.name,
                folderId = specsFolderId,
                isFavorite = true,
                isPinned = true,
                createdAt = nowMillis - 3_600_000L,
                updatedAt = nowMillis - 600_000L
            ),
            NoteEntity(
                title = "Signal Curve Sketch",
                body = "Damping envelope drafted on dotted paper. Tap Ink to annotate or erase strokes directly.",
                checklistJson = "[]",
                strokesJson = NoteSerializer.encodeStrokes(sampleSketchStrokes),
                template = PaperTemplate.DOTTED.name,
                folderId = sketchesFolderId,
                isFavorite = true,
                isPinned = false,
                createdAt = nowMillis - 7_200_000L,
                updatedAt = nowMillis - 1_800_000L
            ),
            NoteEntity(
                title = "Cornell Lecture Notes",
                body = "Left margin cues keep key definitions scannable while main column captures synthesis and takeaways.",
                checklistJson = "[]",
                strokesJson = "[]",
                template = PaperTemplate.CORNELL.name,
                folderId = null,
                isFavorite = false,
                isPinned = false,
                createdAt = nowMillis - 86_400_000L,
                updatedAt = nowMillis - 43_200_000L
            )
        )
    }
}
