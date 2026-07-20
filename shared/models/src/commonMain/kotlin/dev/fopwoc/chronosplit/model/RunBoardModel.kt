package dev.fopwoc.chronosplit.model

data class RunBoardPreviousSegmentModel(
    val label: String,
    val deltaMilliseconds: Long?,
    val possibleTimeSaveMilliseconds: Long?,
    val semanticColor: RunBoardSemanticColor,
)

enum class RunBoardColumnValueType { TIME, DELTA, SEGMENT_TIME }

data class RunBoardColumnModel(
    val name: String,
    val valueMilliseconds: Long?,
    val valueType: RunBoardColumnValueType,
    val semanticColor: RunBoardSemanticColor,
)

enum class RunBoardSemanticColor {
    DEFAULT,
    AHEAD_GAINING_TIME,
    AHEAD_LOSING_TIME,
    BEHIND_GAINING_TIME,
    BEHIND_LOSING_TIME,
    BEST_SEGMENT,
    PERSONAL_BEST,
    PAUSED,
    NOT_RUNNING,
}

data class RunBoardSegmentModel(
    val title: String,
    val iconPngBase64: String?,
    val splitTimeMilliseconds: Long?,
    val segmentTimeMilliseconds: Long?,
    val liveTimeMilliseconds: Long?,
    val liveSplitTimeMilliseconds: Long?,
    val goldTimeMilliseconds: Long?,
    val goldDeltaMilliseconds: Long?,
    val personalBestTimeMilliseconds: Long?,
    val comparisonDeltaMilliseconds: Long?,
    val comparisonSegmentTimeMilliseconds: Long?,
    val segmentDeltaMilliseconds: Long?,
    val showLiveDelta: Boolean,
    val columns: List<RunBoardColumnModel>,
    val semanticColor: RunBoardSemanticColor,
    val isActive: Boolean,
    val isCompleted: Boolean,
    val isGold: Boolean,
)

data class RunBoardModel(
    val runId: String,
    val title: String,
    val gameName: String?,
    val categoryName: String?,
    val gameIconPngBase64: String?,
    val attemptCount: Int,
    val status: RunStatus,
    val elapsedMilliseconds: Long,
    val goldTimeMilliseconds: Long?,
    val layout: LayoutDefinition,
    val segments: List<RunBoardSegmentModel>,
    val previousSegment: RunBoardPreviousSegmentModel?,
    val timerSemanticColor: RunBoardSemanticColor,
    val primaryActionTitle: String,
    val pauseActionTitle: String,
)

fun RunSnapshot.toRunBoardModel(
    nowEpochMilliseconds: Long = capturedAtEpochMilliseconds,
    primaryActionTitle: String = "Start",
    pauseActionTitle: String = "Pause",
): RunBoardModel {
    val currentElapsedMilliseconds = elapsedAt(nowEpochMilliseconds)
    val currentSegmentStartMilliseconds = state.results.lastOrNull()?.elapsedAtEndMilliseconds ?: 0
    val comparisonSegmentTimes = definition.segments.mapIndexed { index, _ ->
        definition.comparisonSegmentTime(index, null)
    }
    val rows = definition.segments.mapIndexed { index, segment ->
        val result = state.results.getOrNull(index)
        val goldTime = segment.goldTimeMilliseconds
        val isActive = state.status != RunStatus.FINISHED && index == state.activeSegmentIndex
        val liveSegmentTime = if (
            isActive && (state.status == RunStatus.RUNNING || state.status == RunStatus.PAUSED)
        ) {
            (currentElapsedMilliseconds - currentSegmentStartMilliseconds).coerceAtLeast(0)
        } else {
            null
        }
        val comparisonSegmentTime = comparisonSegmentTimes[index]
        val bestSegmentDelta = when {
            result != null -> {
                val resultBestSegmentTime = if (result.hasBestSegmentTime) {
                    result.bestSegmentTimeMilliseconds
                } else {
                    goldTime
                }
                resultBestSegmentTime?.let { best -> result.segmentDurationMilliseconds - best }
            }
            liveSegmentTime != null -> goldTime?.let { best -> liveSegmentTime - best }
            else -> null
        }
        val comparisonDelta = segment.personalBestTimeMilliseconds?.let { comparisonTime ->
            when {
                result != null -> result.elapsedAtEndMilliseconds - comparisonTime
                liveSegmentTime != null -> currentElapsedMilliseconds - comparisonTime
                else -> null
            }
        }
        val showLiveDelta = isActive && shouldShowLiveSegment(
            currentElapsedMilliseconds = currentElapsedMilliseconds,
            liveSegmentTime = liveSegmentTime,
            bestSegmentTime = goldTime,
            comparisonSplitTime = segment.personalBestTimeMilliseconds,
            comparisonSegmentTime = comparisonSegmentTime,
        )
        val previousComparisonDelta = rowsComparisonDelta(
            definition = definition,
            results = state.results,
            beforeIndex = index,
        )
        val isGold = result?.isBestSegment == true
        val columns = definition.layout.splits.columns.map { column ->
            columnValue(
                column = column,
                definition = definition,
                index = index,
                result = result,
                isActive = isActive,
                currentElapsedMilliseconds = currentElapsedMilliseconds,
                liveSegmentTime = liveSegmentTime,
                bestSegmentTime = goldTime,
                previousComparisonDelta = previousComparisonDelta,
                isGold = isGold,
            )
        }
        val segmentComparisonDelta = when {
            result != null -> comparisonSegmentTime?.let { result.segmentDurationMilliseconds - it }
            liveSegmentTime != null && showLiveDelta -> comparisonSegmentTime?.let { liveSegmentTime - it }
            else -> null
        }

        RunBoardSegmentModel(
            title = segment.title,
            iconPngBase64 = segment.iconPngBase64,
            splitTimeMilliseconds = result?.elapsedAtEndMilliseconds,
            segmentTimeMilliseconds = result?.segmentDurationMilliseconds,
            liveTimeMilliseconds = liveSegmentTime,
            liveSplitTimeMilliseconds = currentElapsedMilliseconds.takeIf { liveSegmentTime != null },
            goldTimeMilliseconds = goldTime,
            goldDeltaMilliseconds = bestSegmentDelta,
            personalBestTimeMilliseconds = segment.personalBestTimeMilliseconds,
            comparisonDeltaMilliseconds = comparisonDelta,
            comparisonSegmentTimeMilliseconds = comparisonSegmentTime,
            segmentDeltaMilliseconds = segmentComparisonDelta,
            showLiveDelta = showLiveDelta,
            columns = columns,
            semanticColor = when {
                isActive -> RunBoardSemanticColor.DEFAULT
                isGold -> RunBoardSemanticColor.BEST_SEGMENT
                else -> splitSemanticColor(comparisonDelta, previousComparisonDelta, showSegmentDeltas = true)
            },
            isActive = isActive,
            isCompleted = result != null,
            isGold = isGold,
        )
    }
    val activeRow = rows.firstOrNull(RunBoardSegmentModel::isActive)
    val previousRow = state.results.lastOrNull()?.let { result ->
        rows.getOrNull(definition.segments.indexOfFirst { it.id == result.segmentId })
    }
    val previousComparisonOverride = definition.layout.previousSegment.comparisonOverride
    val previousLabelSuffix = previousComparisonOverride?.let { " ($it)" }.orEmpty()
    val activeIndex = rows.indexOfFirst(RunBoardSegmentModel::isActive)
    val activeComparisonSegment = activeIndex.takeIf { it >= 0 }
        ?.let { definition.comparisonSegmentTime(it, previousComparisonOverride) }
    val activeComparisonSplit = activeIndex.takeIf { it >= 0 }
        ?.let { definition.comparisonSplitTime(it, previousComparisonOverride) }
    val showPreviousLiveSegment = activeRow?.liveTimeMilliseconds?.let { liveTime ->
        shouldShowLiveSegment(
            currentElapsedMilliseconds = currentElapsedMilliseconds,
            liveSegmentTime = liveTime,
            bestSegmentTime = activeRow.goldTimeMilliseconds,
            comparisonSplitTime = activeComparisonSplit,
            comparisonSegmentTime = activeComparisonSegment,
        )
    } == true
    val activeLiveDelta = activeRow?.liveTimeMilliseconds?.let { live ->
        activeComparisonSegment?.let { comparison -> live - comparison }
    }
    val previousIndex = previousRow?.let(rows::indexOf)
    val previousComparisonSegment = previousIndex?.takeIf { it >= 0 }
        ?.let { definition.comparisonSegmentTime(it, previousComparisonOverride) }
    val previousComparisonDelta = previousRow?.segmentTimeMilliseconds?.let { duration ->
        previousComparisonSegment?.let { comparison -> duration - comparison }
    }
    val previousSegment = when {
        showPreviousLiveSegment -> RunBoardPreviousSegmentModel(
            label = "Live Segment$previousLabelSuffix",
            deltaMilliseconds = activeLiveDelta,
            possibleTimeSaveMilliseconds = activeComparisonSegment
                ?.let { comparison -> activeRow.goldTimeMilliseconds?.let { best -> comparison - best } },
            semanticColor = splitSemanticColor(activeLiveDelta),
        )
        previousComparisonDelta != null -> RunBoardPreviousSegmentModel(
            label = "Previous Segment$previousLabelSuffix",
            deltaMilliseconds = previousComparisonDelta,
            possibleTimeSaveMilliseconds = previousComparisonSegment
                ?.let { comparison -> previousRow.goldTimeMilliseconds?.let { best -> comparison - best } },
            semanticColor = if (previousRow.isGold) {
                RunBoardSemanticColor.BEST_SEGMENT
            } else {
                splitSemanticColor(previousComparisonDelta)
            },
        )
        else -> RunBoardPreviousSegmentModel(
            label = "Previous Segment$previousLabelSuffix",
            deltaMilliseconds = null,
            possibleTimeSaveMilliseconds = null,
            semanticColor = RunBoardSemanticColor.DEFAULT,
        )
    }
    val timerSemanticColor = when (state.status) {
        RunStatus.READY -> RunBoardSemanticColor.NOT_RUNNING
        RunStatus.PAUSED -> RunBoardSemanticColor.PAUSED
        RunStatus.FINISHED -> {
            val personalBest = definition.segments.last().personalBestTimeMilliseconds
            if (personalBest == null || currentElapsedMilliseconds < personalBest) {
                RunBoardSemanticColor.PERSONAL_BEST
            } else {
                RunBoardSemanticColor.BEHIND_LOSING_TIME
            }
        }
        RunStatus.RUNNING -> {
            val activeIndex = state.activeSegmentIndex
            val currentDelta = rows.getOrNull(activeIndex)?.comparisonDeltaMilliseconds
            val lastDelta = rows
                .take(activeIndex)
                .asReversed()
                .firstNotNullOfOrNull(RunBoardSegmentModel::comparisonDeltaMilliseconds)
            splitSemanticColor(currentDelta, lastDelta, showSegmentDeltas = true)
                .takeUnless { it == RunBoardSemanticColor.DEFAULT }
                ?: RunBoardSemanticColor.AHEAD_GAINING_TIME
        }
    }

    return RunBoardModel(
        runId = state.runId,
        title = definition.title,
        gameName = definition.gameName,
        categoryName = definition.categoryName,
        gameIconPngBase64 = definition.gameIconPngBase64,
        attemptCount = definition.attemptCount,
        status = state.status,
        elapsedMilliseconds = currentElapsedMilliseconds,
        goldTimeMilliseconds = definition.goldTimeMilliseconds(),
        layout = definition.layout,
        segments = rows,
        previousSegment = previousSegment,
        timerSemanticColor = timerSemanticColor,
        primaryActionTitle = primaryActionTitle,
        pauseActionTitle = pauseActionTitle,
    )
}

private fun shouldShowLiveSegment(
    currentElapsedMilliseconds: Long,
    liveSegmentTime: Long?,
    bestSegmentTime: Long?,
    comparisonSplitTime: Long?,
    comparisonSegmentTime: Long?,
    splitDelta: Boolean = false,
): Boolean {
    if (liveSegmentTime == null) return false
    return splitDelta && comparisonSplitTime?.let { currentElapsedMilliseconds > it } == true ||
        bestSegmentTime?.let { liveSegmentTime > it } == true ||
        comparisonSegmentTime?.let { liveSegmentTime > it } == true
}

private fun RunDefinition.comparisonSplitTime(index: Int, override: String?): Long? =
    if (override == "Best Segments") {
        val bests = segments.take(index + 1).map(SegmentDefinition::goldTimeMilliseconds)
        if (bests.any { it == null }) null else bests.sumOf { requireNotNull(it) }
    } else {
        segments[index].personalBestTimeMilliseconds
    }

private fun RunDefinition.comparisonSegmentTime(index: Int, override: String?): Long? {
    if (override == "Best Segments") return segments[index].goldTimeMilliseconds
    val comparison = comparisonSplitTime(index, override) ?: return null
    val previous = (index - 1 downTo 0)
        .firstNotNullOfOrNull { comparisonSplitTime(it, override) }
        ?: 0L
    return comparison - previous
}

private fun rowsComparisonDelta(
    definition: RunDefinition,
    results: List<SegmentResult>,
    beforeIndex: Int,
): Long? = results
    .take(beforeIndex)
    .asReversed()
    .firstNotNullOfOrNull { result ->
        val index = definition.segments.indexOfFirst { it.id == result.segmentId }
        definition.segments.getOrNull(index)?.personalBestTimeMilliseconds
            ?.let { result.elapsedAtEndMilliseconds - it }
    }

private fun columnValue(
    column: LayoutSplitColumn,
    definition: RunDefinition,
    index: Int,
    result: SegmentResult?,
    isActive: Boolean,
    currentElapsedMilliseconds: Long,
    liveSegmentTime: Long?,
    bestSegmentTime: Long?,
    previousComparisonDelta: Long?,
    isGold: Boolean,
): RunBoardColumnModel {
    val comparisonSplit = definition.comparisonSplitTime(index, column.comparisonOverride)
    val comparisonSegment = definition.comparisonSegmentTime(index, column.comparisonOverride)
    val segmentBased = column.updateWith in setOf(
        LayoutColumnUpdateWith.SEGMENT_TIME,
        LayoutColumnUpdateWith.SEGMENT_DELTA,
        LayoutColumnUpdateWith.SEGMENT_DELTA_WITH_FALLBACK,
    )
    val updateCurrent = isActive && when (column.updateTrigger) {
        LayoutColumnUpdateTrigger.ON_STARTING_SEGMENT -> true
        LayoutColumnUpdateTrigger.ON_ENDING_SEGMENT -> false
        LayoutColumnUpdateTrigger.CONTEXTUAL -> shouldShowLiveSegment(
            currentElapsedMilliseconds = currentElapsedMilliseconds,
            liveSegmentTime = liveSegmentTime,
            bestSegmentTime = bestSegmentTime,
            comparisonSplitTime = comparisonSplit,
            comparisonSegmentTime = comparisonSegment,
            splitDelta = !segmentBased,
        )
    }
    val shouldUpdate = result != null || updateCurrent
    val updatedValue = if (shouldUpdate) {
        when (column.updateWith) {
            LayoutColumnUpdateWith.DONT_UPDATE -> null
            LayoutColumnUpdateWith.SPLIT_TIME -> (result?.elapsedAtEndMilliseconds ?: currentElapsedMilliseconds) to RunBoardColumnValueType.TIME
            LayoutColumnUpdateWith.DELTA,
            LayoutColumnUpdateWith.DELTA_WITH_FALLBACK,
            -> comparisonSplit?.let { (result?.elapsedAtEndMilliseconds ?: currentElapsedMilliseconds) - it }
                ?.let { it to RunBoardColumnValueType.DELTA }
                ?: if (column.updateWith == LayoutColumnUpdateWith.DELTA_WITH_FALLBACK) {
                    (result?.elapsedAtEndMilliseconds ?: currentElapsedMilliseconds) to RunBoardColumnValueType.TIME
                } else null
            LayoutColumnUpdateWith.SEGMENT_TIME -> (result?.segmentDurationMilliseconds ?: liveSegmentTime)
                ?.let { it to RunBoardColumnValueType.SEGMENT_TIME }
            LayoutColumnUpdateWith.SEGMENT_DELTA,
            LayoutColumnUpdateWith.SEGMENT_DELTA_WITH_FALLBACK,
            -> comparisonSegment?.let { comparison ->
                (result?.segmentDurationMilliseconds ?: liveSegmentTime)?.minus(comparison)
            }?.let { it to RunBoardColumnValueType.DELTA }
                ?: if (column.updateWith == LayoutColumnUpdateWith.SEGMENT_DELTA_WITH_FALLBACK) {
                    (result?.segmentDurationMilliseconds ?: liveSegmentTime)?.let {
                        it to RunBoardColumnValueType.SEGMENT_TIME
                    }
                } else null
        }
    } else null
    val startingValue = when (column.startWith) {
        LayoutColumnStartWith.EMPTY -> null
        LayoutColumnStartWith.COMPARISON_TIME -> comparisonSplit?.let { it to RunBoardColumnValueType.TIME }
        LayoutColumnStartWith.COMPARISON_SEGMENT_TIME -> comparisonSegment?.let { it to RunBoardColumnValueType.SEGMENT_TIME }
        LayoutColumnStartWith.POSSIBLE_TIME_SAVE -> comparisonSegment?.let { comparison ->
            bestSegmentTime?.let { best -> comparison - best }
        }?.let { it to RunBoardColumnValueType.SEGMENT_TIME }
    }
    val (value, type) = updatedValue ?: startingValue ?: (null to when (column.startWith) {
        LayoutColumnStartWith.COMPARISON_SEGMENT_TIME,
        LayoutColumnStartWith.POSSIBLE_TIME_SAVE,
        -> RunBoardColumnValueType.SEGMENT_TIME
        else -> RunBoardColumnValueType.TIME
    })
    val delta = value.takeIf { type == RunBoardColumnValueType.DELTA }
    val semantic = when {
        isActive -> RunBoardSemanticColor.DEFAULT
        isGold && shouldUpdate -> RunBoardSemanticColor.BEST_SEGMENT
        segmentBased -> splitSemanticColor(delta)
        else -> splitSemanticColor(delta, previousComparisonDelta, showSegmentDeltas = true)
    }
    return RunBoardColumnModel(column.name, value, type, semantic)
}

private fun splitSemanticColor(
    delta: Long?,
    lastDelta: Long? = null,
    showSegmentDeltas: Boolean = false,
): RunBoardSemanticColor = when {
    delta == null || delta == 0L -> RunBoardSemanticColor.DEFAULT
    delta < 0L && showSegmentDeltas && lastDelta?.let { delta > it } == true ->
        RunBoardSemanticColor.AHEAD_LOSING_TIME
    delta < 0L -> RunBoardSemanticColor.AHEAD_GAINING_TIME
    showSegmentDeltas && lastDelta?.let { delta < it } == true ->
        RunBoardSemanticColor.BEHIND_GAINING_TIME
    else -> RunBoardSemanticColor.BEHIND_LOSING_TIME
}
