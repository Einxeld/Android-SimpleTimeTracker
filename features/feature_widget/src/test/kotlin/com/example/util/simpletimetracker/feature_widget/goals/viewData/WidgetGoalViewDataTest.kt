package com.example.util.simpletimetracker.feature_widget.goals.viewData

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGoalViewDataTest {

    @Test
    fun `progress stays within horizontal bar bounds`() {
        assertEquals(0, WidgetGoalViewData.progressForPercent(-15))
        assertEquals(0, WidgetGoalViewData.progressForPercent(0))
        assertEquals(46, WidgetGoalViewData.progressForPercent(46))
        assertEquals(100, WidgetGoalViewData.progressForPercent(100))
        assertEquals(100, WidgetGoalViewData.progressForPercent(175))
    }

    @Test
    fun `weekly segments show overtime spilling into next day`() {
        val hour = 60 * 60 * 1000L

        assertEquals(
            listOf(100, 13, 0, 0, 0),
            WidgetGoalViewData.progressForSegments(current = 9 * hour, target = 40 * hour, count = 5),
        )
    }

    @Test
    fun `monthly segments are one quarter of the goal each`() {
        val hour = 60 * 60 * 1000L

        assertEquals(
            listOf(100, 0, 0, 0),
            WidgetGoalViewData.progressForSegments(current = 40 * hour, target = 160 * hour, count = 4),
        )
        assertEquals(emptyList<Int>(), WidgetGoalViewData.progressForSegments(current = 1, target = 0, count = 4))
    }

    @Test
    fun `daily balance consumes carried overtime before showing new overtime`() {
        val minute = 60 * 1000L
        val hour = 60 * minute

        assertEquals(20 * minute, WidgetGoalViewData.dailyBalanceForDisplay(20 * minute, 0, 8 * hour))
        assertEquals(10 * minute, WidgetGoalViewData.dailyBalanceForDisplay(20 * minute, 10 * minute, 8 * hour))
        assertEquals(0L, WidgetGoalViewData.dailyBalanceForDisplay(20 * minute, 7 * hour + 40 * minute, 8 * hour))
        assertEquals(20 * minute, WidgetGoalViewData.dailyBalanceForDisplay(20 * minute, 8 * hour, 8 * hour))
    }

    @Test
    fun `daily work first recovers carried deficit`() {
        val minute = 60 * 1000L
        val hour = 60 * minute

        assertEquals(-20 * minute, WidgetGoalViewData.dailyBalanceForDisplay(-20 * minute, 0, 8 * hour))
        assertEquals(-10 * minute, WidgetGoalViewData.dailyBalanceForDisplay(-20 * minute, 10 * minute, 8 * hour))
        assertEquals(0L, WidgetGoalViewData.dailyBalanceForDisplay(-20 * minute, 20 * minute, 8 * hour))
        assertEquals(20 * minute, WidgetGoalViewData.dailyBalanceForDisplay(-20 * minute, 8 * hour + 40 * minute, 8 * hour))
    }
}
