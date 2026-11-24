package com.example.util.simpletimetracker.feature_widget.goals.viewData

import androidx.annotation.ColorInt
import kotlin.math.roundToInt

data class WidgetGoalViewData(
    val id: Long,
    val name: String,
    val period: String,
    val subtype: String,
    val current: String,
    val target: String,
    val percent: String,
    val progress: Int,
    @ColorInt val color: Int,
    val progressSegments: List<Int> = emptyList(),
    val balance: String? = null,
    val todayRemaining: String? = null,
) {

    companion object {
        fun progressForPercent(percent: Long): Int = percent.coerceIn(0L, 100L).toInt()

        fun progressForSegments(current: Long, target: Long, count: Int): List<Int> {
            if (target <= 0L || count <= 0) return emptyList()
            val segmentSize = target.toDouble() / count
            return List(count) { index ->
                val segmentProgress = (current - segmentSize * index) / segmentSize * 100
                segmentProgress.roundToInt().coerceIn(0, 100)
            }
        }
    }
}
