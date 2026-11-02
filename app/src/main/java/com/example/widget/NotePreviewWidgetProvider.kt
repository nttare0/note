package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home Screen Note Page Preview Widget (3x2 resizable):
 * Displays a pinned or most-recent Samsung Notes page with title, text/checklist summary, and
 * downsampled ink/image thumbnail, opening that exact note when tapped.
 */
class NotePreviewWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NoteWidgetHelper.updateNotePreviewWidgetsNow(appCtx, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        appWidgetIds.forEach { id ->
            NoteWidgetHelper.clearBoundNoteForWidget(context, id)
        }
    }
}
