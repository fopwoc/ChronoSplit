package dev.fopwoc.chronosplit.domain

import dev.fopwoc.chronosplit.model.RunDefinition
import dev.fopwoc.chronosplit.model.RunSnapshot
import dev.fopwoc.chronosplit.model.RunState
import dev.fopwoc.chronosplit.model.RunStatus
import dev.fopwoc.chronosplit.model.SegmentResult

/**
 * Deterministic timer state machine. Time is supplied by the platform so the
 * same transitions can be used and tested on every Kotlin target.
 */
class RunEngine(
    val definition: RunDefinition,
    initialState: RunState = RunState(runId = definition.id),
) {
    var state: RunState = initialState
        private set

    init {
        require(initialState.runId == definition.id) { "State belongs to another run" }
        require(initialState.activeSegmentIndex in definition.segments.indices) {
            "Active segment is outside the configured run"
        }
    }

    fun primaryAction(nowEpochMilliseconds: Long): RunState = when (state.status) {
        RunStatus.READY -> start(nowEpochMilliseconds)
        RunStatus.RUNNING -> advance(nowEpochMilliseconds)
        RunStatus.PAUSED,
        RunStatus.FINISHED,
        -> state
    }

    fun start(nowEpochMilliseconds: Long): RunState {
        if (state.status != RunStatus.READY) return state
        return update(
            state.copy(
                status = RunStatus.RUNNING,
                startedAtEpochMilliseconds = nowEpochMilliseconds,
            ),
        )
    }

    fun pause(nowEpochMilliseconds: Long): RunState {
        if (state.status != RunStatus.RUNNING) return state
        return update(
            state.copy(
                status = RunStatus.PAUSED,
                pausedAtEpochMilliseconds = nowEpochMilliseconds,
            ),
        )
    }

    fun resume(nowEpochMilliseconds: Long): RunState {
        if (state.status != RunStatus.PAUSED) return state
        val pausedAt = requireNotNull(state.pausedAtEpochMilliseconds)
        require(nowEpochMilliseconds >= pausedAt) { "Time cannot move backwards" }
        return update(
            state.copy(
                status = RunStatus.RUNNING,
                pausedAtEpochMilliseconds = null,
                accumulatedPausedMilliseconds = state.accumulatedPausedMilliseconds +
                    (nowEpochMilliseconds - pausedAt),
            ),
        )
    }

    fun advance(nowEpochMilliseconds: Long): RunState {
        if (state.status != RunStatus.RUNNING) return state
        val result = activeSegmentResult(nowEpochMilliseconds)
        val isLast = state.activeSegmentIndex == definition.segments.lastIndex
        return update(
            state.copy(
                status = if (isLast) RunStatus.FINISHED else RunStatus.RUNNING,
                activeSegmentIndex = if (isLast) state.activeSegmentIndex else state.activeSegmentIndex + 1,
                results = state.results + result,
            ),
        )
    }

    /**
     * Records the active segment before an interrupted run is reset. Unlike
     * [advance], this does not move to another segment or finish the run.
     */
    fun captureActiveSegment(nowEpochMilliseconds: Long): RunState {
        if (state.status != RunStatus.RUNNING && state.status != RunStatus.PAUSED) return state
        return update(state.copy(results = state.results + activeSegmentResult(nowEpochMilliseconds)))
    }

    fun reset(): RunState {
        state = RunState(runId = definition.id, revision = state.revision + 1)
        return state
    }

    fun snapshot(nowEpochMilliseconds: Long): RunSnapshot = RunSnapshot(
        definition = definition,
        state = state,
        capturedAtEpochMilliseconds = nowEpochMilliseconds,
        elapsedMilliseconds = elapsedAt(nowEpochMilliseconds),
    )

    private fun elapsedAt(nowEpochMilliseconds: Long): Long {
        val startedAt = state.startedAtEpochMilliseconds ?: return 0
        val effectiveNow = state.pausedAtEpochMilliseconds ?: nowEpochMilliseconds
        require(effectiveNow >= startedAt) { "Time cannot move backwards" }
        return when (state.status) {
            RunStatus.FINISHED -> state.results.lastOrNull()?.elapsedAtEndMilliseconds ?: 0
            else -> (effectiveNow - startedAt - state.accumulatedPausedMilliseconds).coerceAtLeast(0)
        }
    }

    private fun activeSegmentResult(nowEpochMilliseconds: Long): SegmentResult {
        val elapsed = elapsedAt(nowEpochMilliseconds)
        val previousElapsed = state.results.lastOrNull()?.elapsedAtEndMilliseconds ?: 0
        require(elapsed >= previousElapsed) { "Time cannot move backwards" }
        val segmentDuration = elapsed - previousElapsed
        val bestSegmentTime = definition.segments[state.activeSegmentIndex].goldTimeMilliseconds
        return SegmentResult(
            segmentId = definition.segments[state.activeSegmentIndex].id,
            segmentDurationMilliseconds = segmentDuration,
            elapsedAtEndMilliseconds = elapsed,
            isBestSegment = bestSegmentTime
                ?.let { best -> segmentDuration < best }
                ?: true,
            bestSegmentTimeMilliseconds = bestSegmentTime,
            hasBestSegmentTime = true,
        )
    }

    private fun update(next: RunState): RunState {
        state = next.copy(revision = state.revision + 1)
        return state
    }
}
