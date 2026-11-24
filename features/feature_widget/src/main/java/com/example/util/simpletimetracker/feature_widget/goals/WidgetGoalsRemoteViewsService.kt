package com.example.util.simpletimetracker.feature_widget.goals

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
            if (item.dailyProgress != null) {
                setViewVisibility(R.id.progressWidgetGoal, View.GONE)
                setViewVisibility(R.id.widgetGoalProgressSegments, View.GONE)
                setViewVisibility(R.id.imageWidgetGoalDailyProgress, View.VISIBLE)
                setImageViewBitmap(R.id.imageWidgetGoalDailyProgress, createDailyProgressBitmap(item.dailyProgress))
            } else if (item.progressSegments.isEmpty()) {
                setViewVisibility(R.id.progressWidgetGoal, View.VISIBLE)
                setViewVisibility(R.id.widgetGoalProgressSegments, View.GONE)
                setViewVisibility(R.id.imageWidgetGoalDailyProgress, View.GONE)
                setProgressBar(R.id.progressWidgetGoal, 100, item.progress, false)
            } else {
                setViewVisibility(R.id.progressWidgetGoal, View.GONE)
                setViewVisibility(R.id.widgetGoalProgressSegments, View.VISIBLE)
                setViewVisibility(R.id.imageWidgetGoalDailyProgress, View.GONE)
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

    private fun createDailyProgressBitmap(
        progress: WidgetGoalViewData.DailyProgress,
    ): Bitmap {
        val width = 600
        val height = 18
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val cornerRadius = height / 2f
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, paint.apply { color = TRACK_COLOR })

        val carry = progress.carry
        val target = progress.target.coerceAtLeast(1L)
        val deficit = (-carry).coerceAtLeast(0L)
        val goalPosition = if (carry < 0L) target + deficit else target
        val extraCapacity = maxOf(target / 4, HOUR_IN_MILLIS)
        val scale = (goalPosition + extraCapacity).toDouble()
        val path = Path().apply {
            addRoundRect(bounds, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.save()
        canvas.clipPath(path)

        fun drawRange(start: Long, end: Long, color: Int) {
            val left = (start.coerceAtLeast(0L) / scale * width).toFloat().coerceIn(0f, width.toFloat())
            val right = (end.coerceAtLeast(0L) / scale * width).toFloat().coerceIn(0f, width.toFloat())
            if (right <= left) return
            paint.color = color
            canvas.drawRect(left, 0f, right, height.toFloat(), paint)
        }

        if (carry < 0L) {
            drawRange(0L, deficit, DEFICIT_COLOR)
            drawRange(0L, minOf(progress.current, deficit), RECOVERING_COLOR)
            drawRange(deficit, minOf(progress.current, goalPosition), NORMAL_PROGRESS_COLOR)
            drawRange(goalPosition, progress.current, OVERTIME_COLOR)
        } else {
            val progressEnd = carry + progress.current
            drawRange(carry, minOf(progressEnd, target), NORMAL_PROGRESS_COLOR)
            drawRange(0L, carry, OVERTIME_COLOR)
            drawRange(target, progressEnd, OVERTIME_COLOR)
        }

        val markerX = (goalPosition / scale * width).toFloat().coerceIn(0f, width.toFloat())
        paint.color = TARGET_MARKER_COLOR
        paint.strokeWidth = 2f
        canvas.drawLine(markerX, 1f, markerX, height - 1f, paint)
        canvas.restore()
        return bitmap
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.id ?: position.toLong()

    override fun hasStableIds(): Boolean = true

    private companion object {
        const val EXTRA_GOAL_ID = "com.example.util.simpletimetracker.feature_widget.goals.goalId"
        const val HOUR_IN_MILLIS = 60 * 60 * 1000L
        const val TRACK_COLOR = 0x55FFFFFF
        const val NORMAL_PROGRESS_COLOR = 0xFF64B5F6.toInt()
        const val OVERTIME_COLOR = 0xFF63C174.toInt()
        const val DEFICIT_COLOR = 0xFFE57373.toInt()
        const val RECOVERING_COLOR = 0xFFFFB74D.toInt()
        const val TARGET_MARKER_COLOR = 0x99FFFFFF.toInt()
    }
}
