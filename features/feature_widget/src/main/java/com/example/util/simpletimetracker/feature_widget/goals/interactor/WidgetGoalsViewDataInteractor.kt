package com.example.util.simpletimetracker.feature_widget.goals.interactor

import com.example.util.simpletimetracker.core.interactor.FilterGoalsByDayOfWeekInteractor
import com.example.util.simpletimetracker.core.interactor.StatisticsMediator
import com.example.util.simpletimetracker.core.mapper.GoalViewDataMapper
import com.example.util.simpletimetracker.core.mapper.TimeMapper
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.recordType.extension.value
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeGoalInteractor
import com.example.util.simpletimetracker.domain.recordType.interactor.RecordTypeInteractor
import com.example.util.simpletimetracker.domain.recordType.model.RecordType
import com.example.util.simpletimetracker.domain.recordType.model.RecordTypeGoal
import com.example.util.simpletimetracker.domain.statistics.model.ChartFilterType
import com.example.util.simpletimetracker.domain.statistics.model.RangeLength
import com.example.util.simpletimetracker.feature_widget.goals.viewData.WidgetGoalViewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class WidgetGoalsViewDataInteractor @Inject constructor(
    private val recordTypeGoalInteractor: RecordTypeGoalInteractor,
    private val recordTypeInteractor: RecordTypeInteractor,
    private val statisticsMediator: StatisticsMediator,
    private val prefsInteractor: PrefsInteractor,
    private val timeMapper: TimeMapper,
    private val filterGoalsByDayOfWeekInteractor: FilterGoalsByDayOfWeekInteractor,
    private val goalViewDataMapper: GoalViewDataMapper,
) {

    suspend fun getViewData(): List<WidgetGoalViewData> = withContext(Dispatchers.IO) {
        val goals = recordTypeGoalInteractor.getAll()
            .filter { it.value > 0L && it.range !is RecordTypeGoal.Range.Session }
        if (goals.isEmpty()) return@withContext emptyList()

        val types = recordTypeInteractor.getAll().associateBy(RecordType::id)
        val firstDayOfWeek = prefsInteractor.getFirstDayOfWeek()
        val startOfDayShift = prefsInteractor.getStartOfDayShift()
        val dailyRange = timeMapper.getRangeStartAndEnd(
            rangeLength = RangeLength.Day,
            shift = 0,
            firstDayOfWeek = firstDayOfWeek,
            startOfDayShift = startOfDayShift,
        )
        // Filter weekday-specific daily goals against today's day, not the start day of
        // each weekly, monthly, or yearly range.
        val applicableGoals = filterGoalsByDayOfWeekInteractor.execute(
            goals = goals,
            range = dailyRange,
            startOfDayShift = startOfDayShift,
        )
        val durationFormat = prefsInteractor.getDurationFormat()
        val showSeconds = prefsInteractor.getShowSeconds()
        val isDarkTheme = prefsInteractor.getDarkMode()
        val periods = listOf(
            Period(RecordTypeGoal.Range.Daily, RangeLength.Day),
            Period(RecordTypeGoal.Range.Weekly, RangeLength.Week),
            Period(RecordTypeGoal.Range.Monthly, RangeLength.Month),
            Period(RecordTypeGoal.Range.Yearly, RangeLength.Year),
            Period(RecordTypeGoal.Range.Overall, RangeLength.All),
        )
        val filterTypes = listOf(
            ChartFilterType.ACTIVITY,
            ChartFilterType.CATEGORY,
            ChartFilterType.RECORD_TAG,
        )
        val dataHoldersByFilter = filterTypes.associateWith { filterType ->
            statisticsMediator.getDataHolders(
                filterType = filterType,
                types = types,
            )
        }

        buildList {
            periods.forEach periodLoop@ { period ->
                val periodGoals = applicableGoals.filter { it.range == period.goalRange }
                if (periodGoals.isEmpty()) return@periodLoop

                val range = timeMapper.getRangeStartAndEnd(
                    rangeLength = period.rangeLength,
                    shift = 0,
                    firstDayOfWeek = firstDayOfWeek,
                    startOfDayShift = startOfDayShift,
                )

                filterTypes.forEach filterLoop@ { filterType ->
                    val goalsForFilter = periodGoals.filter { goal ->
                        when (filterType) {
                            ChartFilterType.ACTIVITY -> goal.idData is RecordTypeGoal.IdData.Type
                            ChartFilterType.CATEGORY -> goal.idData is RecordTypeGoal.IdData.Category
                            ChartFilterType.RECORD_TAG -> goal.idData is RecordTypeGoal.IdData.Tag
                        }
                    }
                    if (goalsForFilter.isEmpty()) return@filterLoop

                    val statistics = statisticsMediator.getStatistics(
                        filterType = filterType,
                        filteredIds = emptyList(),
                        range = range,
                        forceSeconds = true,
                    )
                    val mappedGoals = goalViewDataMapper.mapStatisticsList(
                        goals = goalsForFilter,
                        types = types,
                        filterType = filterType,
                        filteredIds = emptyList(),
                        rangeLength = period.rangeLength,
                        statistics = statistics,
                        data = dataHoldersByFilter.getValue(filterType),
                        isDarkTheme = isDarkTheme,
                        durationFormat = durationFormat,
                        showSeconds = showSeconds,
                    )

                    mappedGoals.forEach mappedLoop@ { mapped ->
                        val goal = goalsForFilter.firstOrNull { it.id == mapped.id } ?: return@mappedLoop
                        add(
                            WidgetGoalViewData(
                                id = mapped.id,
                                name = mapped.name,
                                period = goalViewDataMapper.mapType(goal.range),
                                subtype = goalViewDataMapper.mapSubtype(goal.subtype),
                                current = mapped.goal.goalCurrent,
                                target = mapped.goal.goal.substringAfter(" - "),
                                percent = mapped.goal.goalPercent,
                                progress = WidgetGoalViewData.progressForPercent(mapped.goal.percent),
                                color = mapped.color,
                            ),
                        )
                    }
                }
            }
        }
    }

    private data class Period(
        val goalRange: RecordTypeGoal.Range,
        val rangeLength: RangeLength,
    )
}
