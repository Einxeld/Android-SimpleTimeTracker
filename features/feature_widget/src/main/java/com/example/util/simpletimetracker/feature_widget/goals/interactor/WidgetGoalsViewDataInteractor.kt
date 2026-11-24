package com.example.util.simpletimetracker.feature_widget.goals.interactor

import com.example.util.simpletimetracker.core.interactor.FilterGoalsByDayOfWeekInteractor
import com.example.util.simpletimetracker.core.interactor.StatisticsMediator
import com.example.util.simpletimetracker.core.mapper.GoalViewDataMapper
import com.example.util.simpletimetracker.core.mapper.TimeMapper
import com.example.util.simpletimetracker.domain.base.DurationFormat
import com.example.util.simpletimetracker.domain.prefs.interactor.PrefsInteractor
import com.example.util.simpletimetracker.domain.recordType.extension.adjustedValue
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
import java.util.Calendar
import kotlin.math.abs
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
        val filterTypes = listOf(
            ChartFilterType.ACTIVITY,
            ChartFilterType.CATEGORY,
            ChartFilterType.RECORD_TAG,
        )
        val weekRange = timeMapper.getRangeStartAndEnd(
            rangeLength = RangeLength.Week,
            shift = 0,
            firstDayOfWeek = firstDayOfWeek,
            startOfDayShift = startOfDayShift,
        )
        val todayStatisticsByFilter = filterTypes.associateWith { filterType ->
            statisticsMediator.getStatistics(
                filterType = filterType,
                filteredIds = emptyList(),
                range = dailyRange,
                forceSeconds = true,
            )
        }
        val weekStatisticsByFilter = filterTypes.associateWith { filterType ->
            statisticsMediator.getStatistics(
                filterType = filterType,
                filteredIds = emptyList(),
                range = weekRange,
                forceSeconds = true,
            )
        }
        val periods = listOf(
            Period(RecordTypeGoal.Range.Daily, RangeLength.Day),
            Period(RecordTypeGoal.Range.Weekly, RangeLength.Week),
            Period(RecordTypeGoal.Range.Monthly, RangeLength.Month),
            Period(RecordTypeGoal.Range.Yearly, RangeLength.Year),
            Period(RecordTypeGoal.Range.Overall, RangeLength.All),
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

                val range = if (period.rangeLength is RangeLength.Week) {
                    weekRange
                } else {
                    timeMapper.getRangeStartAndEnd(
                        rangeLength = period.rangeLength,
                        shift = 0,
                        firstDayOfWeek = firstDayOfWeek,
                        startOfDayShift = startOfDayShift,
                    )
                }

                filterTypes.forEach filterLoop@ { filterType ->
                    val goalsForFilter = periodGoals.filter { goal ->
                        when (filterType) {
                            ChartFilterType.ACTIVITY -> goal.idData is RecordTypeGoal.IdData.Type
                            ChartFilterType.CATEGORY -> goal.idData is RecordTypeGoal.IdData.Category
                            ChartFilterType.RECORD_TAG -> goal.idData is RecordTypeGoal.IdData.Tag
                        }
                    }
                    if (goalsForFilter.isEmpty()) return@filterLoop

                    val statistics = when (period.rangeLength) {
                        is RangeLength.Day -> todayStatisticsByFilter.getValue(filterType)
                        is RangeLength.Week -> weekStatisticsByFilter.getValue(filterType)
                        else -> statisticsMediator.getStatistics(
                            filterType = filterType,
                            filteredIds = emptyList(),
                            range = range,
                            forceSeconds = true,
                        )
                    }
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
                        showZeroMinutes = false,
                    )

                    mappedGoals.forEach mappedLoop@ { mapped ->
                        val goal = goalsForFilter.firstOrNull { it.id == mapped.id } ?: return@mappedLoop
                        val isDurationGoal = goal.type is RecordTypeGoal.Type.Duration
                        val currentDuration = statistics
                            .firstOrNull { it.id == goal.idData.value }
                            ?.data?.duration ?: 0L
                        val currentTodayDuration = todayStatisticsByFilter
                            .getValue(filterType)
                            .firstOrNull { it.id == goal.idData.value }
                            ?.data?.duration ?: 0L
                        val currentWeekDuration = weekStatisticsByFilter
                            .getValue(filterType)
                            .firstOrNull { it.id == goal.idData.value }
                            ?.data?.duration ?: 0L
                        val relatedGoals = applicableGoals.filter {
                            it.idData == goal.idData && it.subtype == goal.subtype &&
                                it.type is RecordTypeGoal.Type.Duration
                        }
                        val dailyGoal = relatedGoals.firstOrNull { it.range is RecordTypeGoal.Range.Daily }
                        val weeklyGoal = goals.firstOrNull {
                            it.idData == goal.idData && it.subtype == goal.subtype &&
                                it.type is RecordTypeGoal.Type.Duration &&
                                it.range is RecordTypeGoal.Range.Weekly
                        }
                        val monthlyGoal = goals.firstOrNull {
                            it.idData == goal.idData && it.subtype == goal.subtype &&
                                it.type is RecordTypeGoal.Type.Duration &&
                                it.range is RecordTypeGoal.Range.Monthly
                        }
                        val dailyTarget = dailyGoal?.adjustedValue
                            ?: weeklyGoal?.adjustedValue?.div(WORKDAYS_PER_WEEK)
                            ?: monthlyGoal?.adjustedValue?.div(STANDARD_WORKDAYS_PER_MONTH)
                        val workdaysBeforeTodayInWeek = countWorkdays(
                            start = weekRange.timeStarted,
                            end = dailyRange.timeStarted,
                        )
                        val previousWorkdays = when (period.goalRange) {
                            is RecordTypeGoal.Range.Daily, is RecordTypeGoal.Range.Weekly ->
                                workdaysBeforeTodayInWeek
                            is RecordTypeGoal.Range.Monthly -> countWorkdays(
                                start = range.timeStarted,
                                end = dailyRange.timeStarted,
                            )
                            else -> 0
                        }
                        val expectedBeforeToday = dailyTarget?.let { targetPerDay ->
                            val expected = previousWorkdays * targetPerDay
                            when (period.goalRange) {
                                is RecordTypeGoal.Range.Weekly,
                                is RecordTypeGoal.Range.Monthly,
                                -> expected.coerceAtMost(goal.adjustedValue)
                                else -> expected
                            }
                        }
                        val dailyCarry = if (
                            isDurationGoal && goal.subtype == RecordTypeGoal.Subtype.Goal &&
                            period.goalRange is RecordTypeGoal.Range.Daily && dailyTarget != null
                        ) {
                            val previousDuration = (currentWeekDuration - currentTodayDuration).coerceAtLeast(0L)
                            val expected = (workdaysBeforeTodayInWeek * dailyTarget)
                                .coerceAtMost(weeklyGoal?.adjustedValue ?: dailyTarget * WORKDAYS_PER_WEEK)
                            previousDuration - expected
                        } else {
                            null
                        }
                        val balance = if (!isDurationGoal || goal.subtype != RecordTypeGoal.Subtype.Goal) {
                            null
                        } else when (period.goalRange) {
                            is RecordTypeGoal.Range.Daily -> dailyCarry?.let {
                                WidgetGoalViewData.dailyBalanceForDisplay(
                                    carry = it,
                                    current = currentTodayDuration,
                                    target = goal.adjustedValue,
                                )
                            }?.takeIf { it != 0L }
                            is RecordTypeGoal.Range.Weekly, is RecordTypeGoal.Range.Monthly -> {
                                if (expectedBeforeToday == null) null else {
                                    val previousDuration = (currentDuration - currentTodayDuration).coerceAtLeast(0L)
                                    (previousDuration - expectedBeforeToday).takeIf { it != 0L }
                                }
                            }
                            else -> null
                        }?.let { formatSignedDuration(it, durationFormat, showSeconds) }
                        val progressSegments = if (isDurationGoal) {
                            when (period.goalRange) {
                                is RecordTypeGoal.Range.Weekly -> WidgetGoalViewData.progressForSegments(
                                    current = currentDuration,
                                    target = goal.adjustedValue,
                                    count = WORKDAYS_PER_WEEK,
                                )
                                is RecordTypeGoal.Range.Monthly -> WidgetGoalViewData.progressForSegments(
                                    current = currentDuration,
                                    target = goal.adjustedValue,
                                    count = WEEKS_PER_MONTH,
                                )
                                else -> emptyList()
                            }
                        } else {
                            emptyList()
                        }
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
                                progressSegments = progressSegments,
                                balance = balance,
                                dailyProgress = dailyCarry?.let {
                                    WidgetGoalViewData.DailyProgress(
                                        carry = it,
                                        current = currentTodayDuration,
                                        target = goal.adjustedValue,
                                    )
                                },
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

    private fun countWorkdays(
        start: Long,
        end: Long,
    ): Long {
        val calendar = Calendar.getInstance().apply { timeInMillis = start }
        var result = 0L
        while (calendar.timeInMillis < end) {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            if (dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY) {
                result++
            }
            calendar.add(Calendar.DATE, 1)
        }
        return result
    }

    private fun formatSignedDuration(
        duration: Long,
        durationFormat: DurationFormat,
        showSeconds: Boolean,
    ): String {
        val sign = if (duration >= 0L) "+" else "−"
        val formatted = timeMapper.formatInterval(
            interval = abs(duration),
            forceSeconds = showSeconds,
            durationFormat = durationFormat,
            showZeroMinutes = false,
        )
        return "$sign$formatted"
    }

    private companion object {
        // Goal pacing is based on a standard Monday-Friday workweek.
        const val WORKDAYS_PER_WEEK = 5
        const val WEEKS_PER_MONTH = 4
        const val STANDARD_WORKDAYS_PER_MONTH = WORKDAYS_PER_WEEK * WEEKS_PER_MONTH
    }
}
