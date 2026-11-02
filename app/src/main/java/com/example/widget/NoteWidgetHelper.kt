package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.example.MainActivity
import com.example.R
import com.example.data.ChecklistItem
import com.example.data.NoteDatabase
import com.example.data.NoteEntity
import com.example.data.NoteSerializer
import com.example.data.PaperTemplate
import com.example.util.CanvasPngExportHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Coordinates Home Screen App Widget intents, pinning, and `RemoteViews` updates across
 * `QuickCaptureWidgetProvider` and `NotePreviewWidgetProvider` while strictly respecting
 * Android's `RemoteViews` bitmap memory ceiling.
 */
object NoteWidgetHelper {

    const val EXTRA_WIDGET_ACTION = "com.example.widget.EXTRA_WIDGET_ACTION"
    const val EXTRA_TARGET_NOTE_ID = "com.example.widget.EXTRA_TARGET_NOTE_ID"

    const val ACTION_OPEN_WORKSPACE = "OPEN_WORKSPACE"
    const val ACTION_NEW_PAGE = "NEW_PAGE"
    const val ACTION_NEW_INK = "NEW_INK"
    const val ACTION_NEW_CHECKLIST = "NEW_CHECKLIST"
    const val ACTION_OPEN_NOTE = "OPEN_NOTE"
    const val ACTION_PIN_NOTE_CALLBACK = "com.example.widget.ACTION_PIN_NOTE_CALLBACK"

    private const val PREFS_WIDGET_BINDINGS = "notestudio_widget_bindings"
    private const val KEY_PREFIX_NOTE_ID = "bound_note_id_"

    private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun saveBoundNoteForWidget(context: Context, appWidgetId: Int, noteId: Long) {
        context.applicationContext
            .getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
            .edit()
            .putLong("$KEY_PREFIX_NOTE_ID$appWidgetId", noteId)
            .apply()
    }

    fun getBoundNoteForWidget(context: Context, appWidgetId: Int): Long {
        return context.applicationContext
            .getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
            .getLong("$KEY_PREFIX_NOTE_ID$appWidgetId", 0L)
    }

    fun clearBoundNoteForWidget(context: Context, appWidgetId: Int) {
        context.applicationContext
            .getSharedPreferences(PREFS_WIDGET_BINDINGS, Context.MODE_PRIVATE)
            .edit()
            .remove("$KEY_PREFIX_NOTE_ID$appWidgetId")
            .apply()
    }

    /**
     * Triggers an asynchronous refresh of all placed NoteStudio Home Screen widgets whenever
     * notes are created, edited, starred, pinned, or deleted.
     */
    fun refreshAllWidgets(context: Context) {
        val appCtx = context.applicationContext
        widgetScope.launch {
            updateQuickCaptureWidgetsNow(appCtx)
            updateNotePreviewWidgetsNow(appCtx)
        }
    }

    suspend fun updateQuickCaptureWidgetsNow(
        context: Context,
        targetIds: IntArray? = null
    ) {
        val appCtx = context.applicationContext
        val manager = AppWidgetManager.getInstance(appCtx)
        val ids = targetIds ?: manager.getAppWidgetIds(
            ComponentName(appCtx, QuickCaptureWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val activeCount = runCatching {
            NoteDatabase.getInstance(appCtx).noteDao().countActiveNotes()
        }.getOrDefault(0)

        val countLabel = if (activeCount == 1) {
            appCtx.getString(R.string.notes_count_single)
        } else {
            appCtx.getString(R.string.notes_count_format, activeCount)
        }

        for (widgetId in ids) {
            val views = RemoteViews(appCtx.packageName, R.layout.widget_quick_capture)
            views.setTextViewText(R.id.widget_quick_count, countLabel)

            views.setOnClickPendingIntent(
                R.id.widget_quick_root,
                buildLaunchPendingIntent(appCtx, ACTION_OPEN_WORKSPACE, 0L, 1000 + widgetId)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_new_page,
                buildLaunchPendingIntent(appCtx, ACTION_NEW_PAGE, 0L, 2000 + widgetId)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_new_ink,
                buildLaunchPendingIntent(appCtx, ACTION_NEW_INK, 0L, 3000 + widgetId)
            )
            views.setOnClickPendingIntent(
                R.id.widget_btn_new_checklist,
                buildLaunchPendingIntent(appCtx, ACTION_NEW_CHECKLIST, 0L, 4000 + widgetId)
            )

            manager.updateAppWidget(widgetId, views)
        }
    }

    suspend fun updateNotePreviewWidgetsNow(
        context: Context,
        targetIds: IntArray? = null
    ) {
        val appCtx = context.applicationContext
        val manager = AppWidgetManager.getInstance(appCtx)
        val ids = targetIds ?: manager.getAppWidgetIds(
            ComponentName(appCtx, NotePreviewWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return

        val dao = NoteDatabase.getInstance(appCtx).noteDao()
        val topFallbackNote = runCatching { dao.getTopPreviewNote() }.getOrNull()

        for (widgetId in ids) {
            val boundNoteId = getBoundNoteForWidget(appCtx, widgetId)
            val resolvedNote = if (boundNoteId > 0L) {
                runCatching { dao.getNoteById(boundNoteId) }.getOrNull()
                    ?.takeIf { !it.isTrashed } ?: topFallbackNote
            } else {
                topFallbackNote
            }

            val views = buildNotePreviewRemoteViews(appCtx, widgetId, resolvedNote)
            // Always use full updateAppWidget so system_server releases prior bitmap allocations
            manager.updateAppWidget(widgetId, views)
        }
    }

    fun buildNotePreviewRemoteViews(
        context: Context,
        widgetId: Int,
        note: NoteEntity?
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_note_preview)

        views.setOnClickPendingIntent(
            R.id.widget_note_new_btn,
            buildLaunchPendingIntent(context, ACTION_NEW_PAGE, 0L, 5000 + widgetId)
        )

        if (note == null) {
            views.setTextViewText(R.id.widget_note_badge, context.getString(R.string.app_name))
            views.setTextViewText(
                R.id.widget_note_title,
                context.getString(R.string.widget_preview_empty_title)
            )
            views.setTextViewText(
                R.id.widget_note_body,
                context.getString(R.string.widget_preview_empty_body)
            )
            views.setViewVisibility(R.id.widget_note_ink_preview, View.GONE)
            views.setTextViewText(R.id.widget_note_footer, "")
            views.setOnClickPendingIntent(
                R.id.widget_preview_root,
                buildLaunchPendingIntent(context, ACTION_NEW_PAGE, 0L, 6000 + widgetId)
            )
            return views
        }

        val checklist = NoteSerializer.decodeChecklist(note.checklistJson)
        val strokes = NoteSerializer.decodeStrokes(note.strokesJson)
        val images = NoteSerializer.decodeImages(note.imagesJson)

        val badgeText = if (note.isPinned) {
            "${context.getString(R.string.app_name)} · ${context.getString(R.string.badge_pinned)}"
        } else {
            context.getString(R.string.app_name)
        }
        views.setTextViewText(R.id.widget_note_badge, badgeText)

        val displayTitle = note.title.ifBlank { context.getString(R.string.editor_title_placeholder) }
        views.setTextViewText(R.id.widget_note_title, displayTitle)

        val previewSnippet = formatWidgetNoteSummary(note.body, checklist)
        if (previewSnippet.isNotBlank()) {
            views.setViewVisibility(R.id.widget_note_body, View.VISIBLE)
            views.setTextViewText(R.id.widget_note_body, previewSnippet)
        } else {
            views.setViewVisibility(R.id.widget_note_body, View.GONE)
        }

        // Render compact, memory-safe thumbnail (cover photo or ink canvas) capped to 360x140 RGB_565
        val previewBitmap: Bitmap? = when {
            images.isNotEmpty() -> decodeCompactPhotoForWidget(images.first().localPath)
            strokes.isNotEmpty() -> {
                val tmpl = runCatching { PaperTemplate.valueOf(note.template) }
                    .getOrDefault(PaperTemplate.BLANK)
                renderCompactInkForWidget(strokes, tmpl)
            }
            else -> null
        }

        if (previewBitmap != null) {
            views.setViewVisibility(R.id.widget_note_ink_preview, View.VISIBLE)
            views.setImageViewBitmap(R.id.widget_note_ink_preview, previewBitmap)
        } else {
            views.setViewVisibility(R.id.widget_note_ink_preview, View.GONE)
        }

        val dateStr = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(note.updatedAt))
        val metaParts = mutableListOf(dateStr)
        if (checklist.isNotEmpty()) {
            val done = checklist.count { it.isChecked }
            metaParts.add("$done/${checklist.size} done")
        }
        if (strokes.isNotEmpty()) {
            metaParts.add(context.getString(R.string.badge_ink))
        }
        views.setTextViewText(R.id.widget_note_footer, metaParts.joinToString(" · "))

        views.setOnClickPendingIntent(
            R.id.widget_preview_root,
            buildLaunchPendingIntent(context, ACTION_OPEN_NOTE, note.id, 7000 + widgetId)
        )
        return views
    }

    /**
     * Pure formatter for note body + checklist preview text shown on Home Screen widgets.
     */
    fun formatWidgetNoteSummary(body: String, checklist: List<ChecklistItem>): String {
        val cleanBody = body.trim()
        if (cleanBody.isNotEmpty()) return cleanBody
        if (checklist.isEmpty()) return ""
        return checklist.take(3).joinToString("\n") { item ->
            val mark = if (item.isChecked) "✓" else "○"
            "$mark ${item.text.ifBlank { "To-do" }}"
        }
    }

    /**
     * Requests the launcher to pin the Quick Capture bar widget directly from inside NoteStudio.
     */
    fun requestPinQuickCaptureWidget(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = ComponentName(context, QuickCaptureWidgetProvider::class.java)
        return manager.requestPinAppWidget(provider, null, null)
    }

    /**
     * Requests the launcher to pin a specific Note's live Page Preview widget onto the Home Screen.
     */
    fun requestPinNotePreviewWidget(context: Context, noteId: Long): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || noteId <= 0L) return false
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false

        val provider = ComponentName(context, NotePreviewWidgetProvider::class.java)
        val callbackIntent = Intent(context, PinNoteWidgetCallbackReceiver::class.java).apply {
            action = ACTION_PIN_NOTE_CALLBACK
            putExtra(EXTRA_TARGET_NOTE_ID, noteId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        val successCallback = PendingIntent.getBroadcast(
            context,
            noteId.toInt(),
            callbackIntent,
            flags
        )
        val extras = Bundle().apply {
            putLong(EXTRA_TARGET_NOTE_ID, noteId)
        }
        return manager.requestPinAppWidget(provider, extras, successCallback)
    }

    private fun buildLaunchPendingIntent(
        context: Context,
        widgetAction: String,
        noteId: Long,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_WIDGET_ACTION, widgetAction)
            if (noteId > 0L) {
                putExtra(EXTRA_TARGET_NOTE_ID, noteId)
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, requestCode, intent, flags)
    }

    private fun decodeCompactPhotoForWidget(localPath: String): Bitmap? {
        return try {
            val file = File(localPath)
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while ((bounds.outWidth / sample) > 400 || (bounds.outHeight / sample) > 220) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    private fun renderCompactInkForWidget(
        strokes: List<com.example.data.InkStroke>,
        template: PaperTemplate
    ): Bitmap? {
        return try {
            val full = CanvasPngExportHelper.renderCanvasToBitmap(
                strokes = strokes,
                template = template,
                isDarkSurface = false,
                scalePxPerDp = 0.85f
            )
            val targetW = 340
            val targetH = 136
            val scaled = Bitmap.createScaledBitmap(full, targetW, targetH, true)
            if (scaled !== full) full.recycle()
            scaled
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * BroadcastReceiver invoked by the launcher once a user confirms pinning a specific Note widget,
 * binding the newly allocated `appWidgetId` to the chosen `noteId`.
 */
class PinNoteWidgetCallbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        val noteId = intent.getLongExtra(NoteWidgetHelper.EXTRA_TARGET_NOTE_ID, 0L)
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && noteId > 0L) {
            NoteWidgetHelper.saveBoundNoteForWidget(context, appWidgetId, noteId)
            NoteWidgetHelper.refreshAllWidgets(context)
        }
    }
}
