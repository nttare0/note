package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.ChecklistItem
import com.example.data.InkStroke
import com.example.data.NoteDatabase
import com.example.data.NoteEntity
import com.example.data.NoteSerializer
import com.example.data.PaperTemplate
import com.example.data.StrokePoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: NoteDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, NoteDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("NoteStudio", context.getString(R.string.app_name))
    }

    @Test
    fun `persist and serialize strokes and checklists in Room`() = runTest {
        val strokes = listOf(
            InkStroke(
                id = "s1",
                points = listOf(StrokePoint(12f, 24f), StrokePoint(48f, 64f)),
                colorKey = "AMBER",
                widthDp = 3.2f,
                isHighlighter = false
            )
        )
        val checklist = listOf(
            ChecklistItem(id = "c1", text = "Test S-Pen canvas", isChecked = true)
        )

        val noteId = db.noteDao().insertNote(
            NoteEntity(
                title = "Sketch Spec",
                body = "Testing Samsung Notes engine",
                checklistJson = NoteSerializer.encodeChecklist(checklist),
                strokesJson = NoteSerializer.encodeStrokes(strokes),
                template = PaperTemplate.GRID.name,
                isPinned = true
            )
        )

        val notes = db.noteDao().observeAllNotes().first()
        assertEquals(1, notes.size)
        val loaded = notes.first()
        assertEquals(noteId, loaded.id)
        assertTrue(loaded.isPinned)

        val decodedStrokes = NoteSerializer.decodeStrokes(loaded.strokesJson)
        assertEquals(1, decodedStrokes.size)
        assertEquals(2, decodedStrokes.first().points.size)

        val decodedChecklist = NoteSerializer.decodeChecklist(loaded.checklistJson)
        assertEquals("Test S-Pen canvas", decodedChecklist.first().text)
    }
}
