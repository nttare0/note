package com.example

import com.example.data.ChecklistItem
import com.example.data.InkStroke
import com.example.data.NoteImageAttachment
import com.example.data.NoteSerializer
import com.example.data.PaperTemplate
import com.example.data.SampleNotebookData
import com.example.data.StrokePoint
import com.example.util.CanvasHistoryStack
import com.example.util.StrokeEraserEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `encode and decode ink strokes, checklists, and note images accurately`() {
        val strokes = listOf(
            InkStroke(
                id = "s1",
                points = listOf(
                    StrokePoint(x = 12f, y = 24f, pressure = 0.85f),
                    StrokePoint(x = 48f, y = 64f, pressure = 1.15f)
                ),
                colorKey = "AMBER",
                widthDp = 3.2f,
                isHighlighter = false
            )
        )
        val checklist = listOf(
            ChecklistItem(id = "c1", text = "Test S-Pen canvas", isChecked = true)
        )
        val images = listOf(
            NoteImageAttachment(
                id = "img1",
                localPath = "/data/user/0/com.example/files/note_images/img_1.jpg",
                widthPx = 1024,
                heightPx = 768
            )
        )

        val decodedStrokes = NoteSerializer.decodeStrokes(NoteSerializer.encodeStrokes(strokes))
        assertEquals(1, decodedStrokes.size)
        assertEquals(0.85f, decodedStrokes.first().points[0].pressure, 0.001f)

        val decodedChecklist = NoteSerializer.decodeChecklist(NoteSerializer.encodeChecklist(checklist))
        assertEquals(1, decodedChecklist.size)
        assertTrue(decodedChecklist.first().isChecked)

        val decodedImages = NoteSerializer.decodeImages(NoteSerializer.encodeImages(images))
        assertEquals(1, decodedImages.size)
        assertEquals("img1", decodedImages.first().id)
        assertEquals(1024, decodedImages.first().widthPx)
    }

    @Test
    fun `canvas undo and redo stack reverts and restores strokes deterministically`() {
        val s1 = InkStroke("1", listOf(StrokePoint(10f, 10f)), "INK", 3.4f)
        val s2 = InkStroke("2", listOf(StrokePoint(30f, 30f)), "AMBER", 3.4f)

        var history = CanvasHistoryStack()
        var current = emptyList<InkStroke>()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)

        // Draw stroke 1
        history = history.recordBeforeChange(current)
        current = current + s1
        assertTrue(history.canUndo)

        // Draw stroke 2
        history = history.recordBeforeChange(current)
        current = current + s2
        assertEquals(2, current.size)

        // Undo stroke 2 -> leaves stroke 1
        val undo1 = history.undo(current)!!
        current = undo1.first
        history = undo1.second
        assertEquals(1, current.size)
        assertEquals("1", current.first().id)
        assertTrue(history.canRedo)

        // Undo stroke 1 -> leaves empty canvas
        val undo2 = history.undo(current)!!
        current = undo2.first
        history = undo2.second
        assertTrue(current.isEmpty())
        assertNull(history.undo(current))

        // Redo stroke 1
        val redo1 = history.redo(current)!!
        current = redo1.first
        history = redo1.second
        assertEquals(1, current.size)
        assertEquals("1", current.first().id)
    }

    @Test
    fun `segment eraser splits horizontal stroke cleanly into two surviving segments`() {
        val line = InkStroke(
            id = "line_1",
            points = listOf(
                StrokePoint(x = 0f, y = 100f),
                StrokePoint(x = 200f, y = 100f)
            ),
            colorKey = "INK",
            widthDp = 3.4f,
            isHighlighter = false
        )

        val pass = StrokeEraserEngine.eraseSegmentsAlongSweep(
            strokes = listOf(line),
            fromXDp = 100f,
            fromYDp = 70f,
            toXDp = 100f,
            toYDp = 130f,
            eraserRadiusDp = 12f
        )

        assertTrue(pass.modified)
        assertEquals(2, pass.updatedStrokes.size)
        assertTrue(pass.updatedStrokes[0].points.all { it.x < 88f })
        assertTrue(pass.updatedStrokes[1].points.all { it.x > 112f })
    }

    @Test
    fun `segment eraser leaves distant strokes untouched`() {
        val line = InkStroke(
            id = "line_safe",
            points = listOf(StrokePoint(10f, 10f), StrokePoint(60f, 10f)),
            colorKey = "BLUE",
            widthDp = 2.0f
        )
        val pass = StrokeEraserEngine.eraseSegmentsAlongSweep(
            strokes = listOf(line),
            fromXDp = 200f,
            fromYDp = 200f,
            toXDp = 220f,
            toYDp = 220f,
            eraserRadiusDp = 15f
        )
        assertFalse(pass.modified)
        assertEquals(1, pass.updatedStrokes.size)
    }

    @Test
    fun `starter notebook data builds valid templates and ink sketches`() {
        val starter = SampleNotebookData.buildStarterNotes(1L, 2L, 1_700_000_000_000L)
        assertEquals(3, starter.size)
        assertEquals(PaperTemplate.GRID.name, starter[0].template)
        assertTrue(NoteSerializer.decodeStrokes(starter[1].strokesJson).isNotEmpty())
    }

    @Test
    fun `canvas grid background toggle switches cleanly and restores previous non-grid template`() {
        // Blank -> Grid -> Blank
        val fromBlank = com.example.ui.NotebookViewModel.computeToggledGridTemplate(
            currentTemplate = PaperTemplate.BLANK,
            previousNonGridTemplate = PaperTemplate.BLANK
        )
        assertEquals(PaperTemplate.GRID, fromBlank)

        val backToBlank = com.example.ui.NotebookViewModel.computeToggledGridTemplate(
            currentTemplate = fromBlank,
            previousNonGridTemplate = PaperTemplate.BLANK
        )
        assertEquals(PaperTemplate.BLANK, backToBlank)

        // Ruled -> Grid -> Ruled
        val fromRuled = com.example.ui.NotebookViewModel.computeToggledGridTemplate(
            currentTemplate = PaperTemplate.RULED,
            previousNonGridTemplate = PaperTemplate.RULED
        )
        assertEquals(PaperTemplate.GRID, fromRuled)

        val restoredRuled = com.example.ui.NotebookViewModel.computeToggledGridTemplate(
            currentTemplate = fromRuled,
            previousNonGridTemplate = PaperTemplate.RULED
        )
        assertEquals(PaperTemplate.RULED, restoredRuled)
    }

    @Test
    fun `drag and drop reorder helper moves items and computes slot indices accurately`() {
        val initial = listOf("first", "second", "third", "fourth")
        val movedDown = com.example.util.DragDropReorderHelper.moveItem(initial, fromIndex = 0, toIndex = 2)
        assertEquals(listOf("second", "third", "first", "fourth"), movedDown)

        val movedUp = com.example.util.DragDropReorderHelper.moveItem(movedDown, fromIndex = 3, toIndex = 1)
        assertEquals(listOf("second", "fourth", "third", "first"), movedUp)

        // Out-of-bounds or no-op returns identical reference
        val unchanged = com.example.util.DragDropReorderHelper.moveItem(initial, fromIndex = 1, toIndex = 1)
        assertEquals(initial, unchanged)

        // Slot computation clamps safely to list bounds
        assertEquals(2, com.example.util.DragDropReorderHelper.computeTargetIndex(0, 4, 96f, 48f))
        assertEquals(0, com.example.util.DragDropReorderHelper.computeTargetIndex(1, 4, -200f, 48f))
        assertEquals(3, com.example.util.DragDropReorderHelper.computeTargetIndex(1, 4, 400f, 48f))
    }

    @Test
    fun `widget note summary formats body text or checklist tasks cleanly`() {
        val checklist = listOf(
            ChecklistItem(id = "1", text = "Sketch grid layout", isChecked = true),
            ChecklistItem(id = "2", text = "Export PNG", isChecked = false)
        )
        // Body takes priority when present
        val bodySummary = com.example.widget.NoteWidgetHelper.formatWidgetNoteSummary(
            body = "Architecture review notes",
            checklist = checklist
        )
        assertEquals("Architecture review notes", bodySummary)

        // Falls back to formatted checklist markers when body is blank
        val checklistSummary = com.example.widget.NoteWidgetHelper.formatWidgetNoteSummary(
            body = "   ",
            checklist = checklist
        )
        assertEquals("✓ Sketch grid layout\n○ Export PNG", checklistSummary)
    }

    @Test
    fun `lasso selection engine selects enclosed strokes and canvas images while ignoring outside elements`() {
        val insideStroke = InkStroke(
            id = "s_in",
            points = listOf(StrokePoint(40f, 40f), StrokePoint(60f, 50f), StrokePoint(80f, 60f)),
            colorKey = "INK",
            widthDp = 3.4f
        )
        val outsideStroke = InkStroke(
            id = "s_out",
            points = listOf(StrokePoint(220f, 220f), StrokePoint(260f, 240f)),
            colorKey = "AMBER",
            widthDp = 3.4f
        )
        val insideCanvasImage = NoteImageAttachment(
            id = "img_in",
            localPath = "/tmp/in.jpg",
            widthPx = 800,
            heightPx = 600,
            placedOnCanvas = true,
            canvasXDp = 30f,
            canvasYDp = 30f,
            canvasWidthDp = 60f,
            canvasHeightDp = 50f
        )
        val inlineOnlyImage = NoteImageAttachment(
            id = "img_inline",
            localPath = "/tmp/inline.jpg",
            widthPx = 800,
            heightPx = 600,
            placedOnCanvas = false,
            canvasXDp = 35f,
            canvasYDp = 35f
        )

        // Draw rectangular lasso loop from (15, 15) to (120, 120)
        val lassoLoop = listOf(
            StrokePoint(15f, 15f),
            StrokePoint(120f, 15f),
            StrokePoint(120f, 120f),
            StrokePoint(15f, 120f)
        )

        val selection = com.example.util.LassoSelectionEngine.findSelectedElements(
            lassoPoints = lassoLoop,
            strokes = listOf(insideStroke, outsideStroke),
            images = listOf(insideCanvasImage, inlineOnlyImage)
        )

        assertEquals(setOf("s_in"), selection.selectedStrokeIds)
        assertEquals(setOf("img_in"), selection.selectedImageIds)
        assertEquals(2, selection.totalCount)
    }

    @Test
    fun `lasso transformation moves, scales, and rotates selected strokes and canvas images accurately`() {
        val stroke = InkStroke(
            id = "s1",
            points = listOf(StrokePoint(110f, 100f)),
            colorKey = "INK",
            widthDp = 3.0f
        )
        val image = NoteImageAttachment(
            id = "img1",
            localPath = "/tmp/canvas.jpg",
            widthPx = 400,
            heightPx = 400,
            placedOnCanvas = true,
            canvasXDp = 50f,
            canvasYDp = 50f,
            canvasWidthDp = 100f,
            canvasHeightDp = 100f,
            rotationDeg = 0f
        )

        // Rotate +90 deg, scale 2x, and translate (+15, -10) around pivot (100, 100)
        val transformedStrokes = com.example.util.LassoSelectionEngine.transformSelectedStrokes(
            strokes = listOf(stroke),
            selectedStrokeIds = setOf("s1"),
            pivotXDp = 100f,
            pivotYDp = 100f,
            dxDp = 15f,
            dyDp = -10f,
            scaleFactor = 2.0f,
            rotationDeltaDeg = 90f
        )
        // rel = (10 * 2, 0) = (20, 0) -> rotated +90 deg = (0, 20) -> + pivot(100,100) + d(15,-10) = (115, 110)
        val pt = transformedStrokes.first().points.first()
        assertEquals(115f, pt.x, 0.05f)
        assertEquals(110f, pt.y, 0.05f)
        assertEquals(6.0f, transformedStrokes.first().widthDp, 0.05f)

        val transformedImages = com.example.util.LassoSelectionEngine.transformSelectedImages(
            images = listOf(image),
            selectedImageIds = setOf("img1"),
            pivotXDp = 100f,
            pivotYDp = 100f,
            dxDp = 15f,
            dyDp = -10f,
            scaleFactor = 1.5f,
            rotationDeltaDeg = 45f
        )
        val updatedImg = transformedImages.first()
        assertEquals(150f, updatedImg.canvasWidthDp, 0.05f)
        assertEquals(150f, updatedImg.canvasHeightDp, 0.05f)
        assertEquals(45f, updatedImg.rotationDeg, 0.05f)
        // Image center started at (100, 100) == pivot, so new center is (115, 90) -> top-left = (40, 15)
        assertEquals(40f, updatedImg.canvasXDp, 0.05f)
        assertEquals(15f, updatedImg.canvasYDp, 0.05f)
    }

    @Test
    fun `canvas history stack undoState and redoState restore transformed canvas images`() {
        val originalImage = NoteImageAttachment(
            id = "img_hist",
            localPath = "/tmp/hist.jpg",
            widthPx = 500,
            heightPx = 500,
            placedOnCanvas = true,
            canvasXDp = 20f,
            canvasYDp = 20f,
            rotationDeg = 0f
        )
        val movedImage = originalImage.copy(canvasXDp = 80f, canvasYDp = 95f, rotationDeg = 30f)

        var history = CanvasHistoryStack()
        history = history.recordBeforeChange(emptyList(), listOf(originalImage))

        val undone = history.undoState(emptyList(), listOf(movedImage))!!
        assertEquals(20f, undone.first.images.first().canvasXDp, 0.01f)
        assertEquals(0f, undone.first.images.first().rotationDeg, 0.01f)

        val redone = undone.second.redoState(undone.first.strokes, undone.first.images)!!
        assertEquals(80f, redone.first.images.first().canvasXDp, 0.01f)
        assertEquals(30f, redone.first.images.first().rotationDeg, 0.01f)
    }
}
