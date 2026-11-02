package com.example.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home Screen Quick Capture Bar Widget (4x1 resizable):
 * Lets users create a New Note, open a blank S-Pen Ink Canvas, or start a Checklist in one tap.
 */
class QuickCaptureWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NoteWidgetHelper.updateQuickCaptureWidgetsNow(appCtx, appWidgetIds)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
