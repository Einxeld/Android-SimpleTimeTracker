package com.example.util.simpletimetracker.feature_widget.goals

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.example.util.simpletimetracker.feature_widget.R
import com.example.util.simpletimetracker.feature_widget.goals.interactor.WidgetGoalsViewDataInteractor
import com.example.util.simpletimetracker.feature_widget.goals.viewData.WidgetGoalViewData
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class WidgetGoalsRemoteViewsService : RemoteViewsService() {

    @Inject
    lateinit var widgetGoalsViewDataInteractor: WidgetGoalsViewDataInteractor

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return WidgetGoalsRemoteViewsFactory(
            context = applicationContext,
            interactor = widgetGoalsViewDataInteractor,
        )
    }
}

private class WidgetGoalsRemoteViewsFactory(
    private val context: Context,
    private val interactor: WidgetGoalsViewDataInteractor,
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<WidgetGoalViewData> = emptyList()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        items = runBlocking { interactor.getViewData() }
    }

    override fun onDestroy() {
        items = emptyList()
    }

    override fun getCount(): Int = items.size

    override fun getViewAt(position: Int): RemoteViews? {
        val item = items.getOrNull(position) ?: return null
        return RemoteViews(context.packageName, R.layout.widget_goal_item_layout).apply {
            setTextViewText(R.id.tvWidgetGoalName, "${item.period} · ${item.name}")
            setInt(R.id.tvWidgetGoalName, "setTextColor", item.color)
            setTextViewText(R.id.tvWidgetGoalPercent, item.percent)
            setTextViewText(
                R.id.tvWidgetGoalDetails,
                "${item.current} / ${item.target} · ${item.subtype}",
            )
            setProgressBar(R.id.progressWidgetGoal, 100, item.progress, false)
            setOnClickFillInIntent(
                R.id.containerWidgetGoalItem,
                Intent().putExtra(EXTRA_GOAL_ID, item.id),
            )
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        const val EXTRA_GOAL_ID = "com.example.util.simpletimetracker.feature_widget.goals.goalId"
    }
}
