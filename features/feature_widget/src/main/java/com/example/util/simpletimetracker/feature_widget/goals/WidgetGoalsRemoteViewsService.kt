package com.example.util.simpletimetracker.feature_widget.goals

import android.content.Context
import android.content.Intent
import android.view.View
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
            if (item.progressSegments.isEmpty()) {
                setViewVisibility(R.id.progressWidgetGoal, View.VISIBLE)
                setViewVisibility(R.id.widgetGoalProgressSegments, View.GONE)
                setProgressBar(R.id.progressWidgetGoal, 100, item.progress, false)
            } else {
                setViewVisibility(R.id.progressWidgetGoal, View.GONE)
                setViewVisibility(R.id.widgetGoalProgressSegments, View.VISIBLE)
                val segmentIds = listOf(
                    R.id.progressWidgetGoalSegment1,
                    R.id.progressWidgetGoalSegment2,
                    R.id.progressWidgetGoalSegment3,
                    R.id.progressWidgetGoalSegment4,
                    R.id.progressWidgetGoalSegment5,
                )
                segmentIds.forEachIndexed { index, id ->
                    val progress = item.progressSegments.getOrNull(index)
                    setViewVisibility(id, if (progress == null) View.GONE else View.VISIBLE)
                    if (progress != null) setProgressBar(id, 100, progress, false)
                }
            }
            val balanceText = buildList {
                item.balance?.let(::add)
                item.todayRemaining?.let {
                    add(context.getString(R.string.widget_goals_today_remaining, it))
                }
            }.joinToString(" · ")
            setTextViewText(R.id.tvWidgetGoalBalance, balanceText)
            val balanceColor = when {
                item.balance?.startsWith("+") == true -> 0xFF63C174.toInt()
                item.balance?.startsWith("−") == true -> 0xFFFF8A80.toInt()
                else -> 0xCCFFFFFF.toInt()
            }
            setInt(R.id.tvWidgetGoalBalance, "setTextColor", balanceColor)
            setViewVisibility(
                R.id.tvWidgetGoalBalance,
                if (balanceText.isEmpty()) View.GONE else View.VISIBLE,
            )
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
