package dev.fopwoc.chronosplit.app.session.model

data class AttemptDetail(
    val id: String,
    val runId: String,
    val runTitle: String,
    val gameName: String?,
    val categoryName: String?,
    val startedAtEpochMilliseconds: Long,
    val completedAtEpochMilliseconds: Long?,
    val elapsedMilliseconds: Long?,
    val segments: List<AttemptSegmentDetail>,
)

data class AttemptSegmentDetail(
    val id: String,
    val title: String,
    val segmentDurationMilliseconds: Long?,
    val elapsedAtEndMilliseconds: Long?,
    val isBestSegment: Boolean,
    val bestSegmentDeltaMilliseconds: Long?,
    val comparisonDeltaMilliseconds: Long?,
)
