package dev.fopwoc.chronosplit.app.session.model

data class AttemptSummary(
    val id: String,
    val runId: String,
    val runTitle: String,
    val startedAtEpochMilliseconds: Long,
    val completed: Boolean,
    val completedSegmentCount: Int,
    val elapsedMilliseconds: Long?,
)
