package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable

@Serializable
data class SegmentResult(
    val segmentId: String,
    val segmentDurationMilliseconds: Long,
    val elapsedAtEndMilliseconds: Long,
    val isBestSegment: Boolean = false,
    // Captures the comparison before the configuration's best is updated.
    val bestSegmentTimeMilliseconds: Long? = null,
    // Distinguishes a recorded empty comparison from legacy serialized results.
    val hasBestSegmentTime: Boolean = false,
)
