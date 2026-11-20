package com.nirvor.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object NirvorWidgetUpdater {

    fun updateAll(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return

        val mainIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, NirvorWidgetProvider::class.java)
        )
        if (mainIds.isNotEmpty()) {
            val intent = Intent(context, NirvorWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, mainIds)
            }
            context.sendBroadcast(intent)
        }

        val islamicIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, NirvorIslamicWidgetProvider::class.java)
        )
        if (islamicIds.isNotEmpty()) {
            val intent = Intent(context, NirvorIslamicWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, islamicIds)
            }
            context.sendBroadcast(intent)
        }
    }
}
