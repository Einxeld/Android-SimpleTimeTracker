package com.example.util.simpletimetracker.feature_widget.goals.viewData

import androidx.annotation.ColorInt

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
) {

    companion object {
        fun progressForPercent(percent: Long): Int = percent.coerceIn(0L, 100L).toInt()
    }
}
