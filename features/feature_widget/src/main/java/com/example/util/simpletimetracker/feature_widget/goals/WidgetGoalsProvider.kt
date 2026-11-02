package com.example.util.simpletimetracker.feature_widget.goals

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.example.util.simpletimetracker.core.utils.PendingIntents
import com.example.util.simpletimetracker.core.utils.SHORTCUT_NAVIGATION_KEY
import com.example.util.simpletimetracker.core.utils.SHORTCUT_NAVIGATION_STATISTICS
import com.example.util.simpletimetracker.feature_widget.R
import com.example.util.simpletimetracker.navigation.Router
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WidgetGoalsProvider : AppWidgetProvider() {

    @Inject
    lateinit var router: Router

    override fun onUpdate(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetIds: IntArray?,
    ) {
        if (context == null || appWidgetManager == null) return
        appWidgetIds?.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context?,
        appWidgetManager: AppWidgetManager?,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        if (context == null || appWidgetManager == null) return
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val adapterIntent = Intent(context, WidgetGoalsRemoteViewsService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        val views = RemoteViews(context.packageName, R.layout.widget_goals_layout).apply {
            setRemoteAdapter(R.id.listWidgetGoals, adapterIntent)
            setEmptyView(R.id.listWidgetGoals, R.id.tvWidgetGoalsEmpty)
            setPendingIntentTemplate(R.id.listWidgetGoals, getOpenIntent(context, appWidgetId))
            setOnClickPendingIntent(R.id.btnWidgetGoalsRefresh, getRefreshIntent(context, appWidgetId))
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.listWidgetGoals)
    }

    private fun getOpenIntent(context: Context, widgetId: Int): PendingIntent {
        val intent = router.getMainStartIntent().apply {
            putExtra(SHORTCUT_NAVIGATION_KEY, SHORTCUT_NAVIGATION_STATISTICS)
        }
        return PendingIntent.getActivity(context, widgetId, intent, PendingIntents.getFlags())
    }

    private fun getRefreshIntent(context: Context, widgetId: Int): PendingIntent {
        val provider = WidgetGoalsProvider::class.java
        val intent = Intent(context, provider).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
        }
        return PendingIntent.getBroadcast(context, widgetId, intent, PendingIntents.getFlags())
    }

}
