package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable

@Serializable
data class AttemptRecord(
    val id: String,
    val definition: RunDefinition,
    val startedAtEpochMilliseconds: Long,
    val completedAtEpochMilliseconds: Long?,
    val results: List<SegmentResult>,
    val elapsedMilliseconds: Long? = results.lastOrNull()?.elapsedAtEndMilliseconds,
)
