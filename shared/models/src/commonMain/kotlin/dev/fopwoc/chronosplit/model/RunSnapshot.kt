package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable

@Serializable
data class RunSnapshot(
    val definition: RunDefinition,
    val state: RunState,
    val capturedAtEpochMilliseconds: Long,
    val elapsedMilliseconds: Long,
)

fun RunSnapshot.elapsedAt(nowEpochMilliseconds: Long): Long {
    if (state.status != RunStatus.RUNNING) return elapsedMilliseconds

    return elapsedMilliseconds +
        (nowEpochMilliseconds - capturedAtEpochMilliseconds).coerceAtLeast(0)
}
