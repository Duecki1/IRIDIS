package com.dueckis.kawaiiraweditor.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dueckis.kawaiiraweditor.MainActivity
import com.dueckis.kawaiiraweditor.R

import android.content.ComponentName

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.os.SystemClock

class EditedGalleryWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_AUTO_UPDATE = "com.dueckis.kawaiiraweditor.widget.ACTION_AUTO_UPDATE"
        private const val UPDATE_INTERVAL_MS = 300_000L // 5 minutes
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_edited_gallery)

            // Connect StackView to the RemoteViewsService
            val svcIntent = Intent(context, EditedGalleryWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.flipperView, svcIntent)
            views.setEmptyView(R.id.flipperView, R.id.emptyText)

            // Clicking an image should open the editor for that project. Route clicks to MainActivity
            // so onNewIntent can hand the project id to the composable app.
            val clickIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val clickPI = PendingIntent.getActivity(
                context,
                0,
                clickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setPendingIntentTemplate(R.id.flipperView, clickPI)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.flipperView)
        }

        // Schedule periodic updates to keep flipping alive
        scheduleNextUpdate(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_AUTO_UPDATE) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, EditedGalleryWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                onUpdate(context, mgr, ids)
            }
            scheduleNextUpdate(context)
        }
    }

    private fun scheduleNextUpdate(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, EditedGalleryWidgetProvider::class.java).apply { action = ACTION_AUTO_UPDATE }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + UPDATE_INTERVAL_MS,
            pendingIntent
        )
        }
    }

