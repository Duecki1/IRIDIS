package com.dueckis.kawaiiraweditor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dueckis.kawaiiraweditor.MainActivity
import com.dueckis.kawaiiraweditor.R

class EditedGalleryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_edited_gallery)

            // Connect StackView to the RemoteViewsService
            val svcIntent = Intent(context, EditedGalleryWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.stackView, svcIntent)
            views.setEmptyView(R.id.stackView, R.id.emptyText)

            // Clicking an image opens MainActivity (app entry) — update if you have a detail activity
            val clickIntent = Intent(context, MainActivity::class.java)
            val clickPI = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.stackView, clickPI)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.stackView)
        }
    }
}
