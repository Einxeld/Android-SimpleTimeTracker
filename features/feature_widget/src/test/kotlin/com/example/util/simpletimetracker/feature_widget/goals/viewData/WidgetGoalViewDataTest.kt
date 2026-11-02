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
}
