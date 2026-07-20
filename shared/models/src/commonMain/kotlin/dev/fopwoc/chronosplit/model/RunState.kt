package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable

@Serializable
data class RunState(
    val runId: String,
    val revision: Long = 0,
    val status: RunStatus = RunStatus.READY,
    val activeSegmentIndex: Int = 0,
    val startedAtEpochMilliseconds: Long? = null,
    val pausedAtEpochMilliseconds: Long? = null,
    val accumulatedPausedMilliseconds: Long = 0,
    val results: List<SegmentResult> = emptyList(),
)
