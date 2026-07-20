package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable

@Serializable
data class RunDefinition(
    val id: String,
    val title: String,
    val segments: List<SegmentDefinition>,
    val layout: LayoutDefinition = LayoutDefinition(),
    val gameName: String? = null,
    val categoryName: String? = null,
    val gameIconPngBase64: String? = null,
    val attemptCount: Int = 0,
    val offsetMilliseconds: Long = 0,
) {
    init {
        require(id.isNotBlank()) { "Run id must not be blank" }
        require(title.isNotBlank()) { "Run title must not be blank" }
        require(segments.isNotEmpty()) { "A run requires at least one segment" }
        require(segments.all { it.id.isNotBlank() && it.title.isNotBlank() }) { "Segment ids and titles must not be blank" }
        require(segments.map { it.id }.distinct().size == segments.size) { "Segment ids must be unique" }
        require(attemptCount >= 0) { "Attempt count must not be negative" }
    }
}

/**
 * Applies completed segment results to this configuration's gold splits.
 *
 * A gold split is the fastest segment duration recorded for this configuration.
 */
fun RunDefinition.withGoldSplits(results: List<SegmentResult>): RunDefinition {
    if (results.isEmpty()) return this

    val resultBySegmentId = results.associateBy { it.segmentId }
    return copy(
        segments = segments.map { segment ->
            val result = resultBySegmentId[segment.id]
            val currentGold = segment.goldTimeMilliseconds
            val nextGold = result?.segmentDurationMilliseconds?.let { duration ->
                minOf(currentGold ?: duration, duration)
            } ?: currentGold

            if (nextGold == segment.goldTimeMilliseconds) {
                segment
            } else {
                segment.copy(goldTimeMilliseconds = nextGold)
            }
        },
    )
}

/**
 * Promotes a completed faster attempt to the Personal Best comparison.
 * Personal Best values are cumulative split times, unlike per-segment golds.
 */
fun RunDefinition.withPersonalBest(results: List<SegmentResult>): RunDefinition {
    if (results.size != segments.size) return this
    val resultBySegmentId = results.associateBy(SegmentResult::segmentId)
    if (segments.any { it.id !in resultBySegmentId }) return this

    val finalTime = resultBySegmentId.getValue(segments.last().id).elapsedAtEndMilliseconds
    val currentPersonalBest = segments.last().personalBestTimeMilliseconds
    if (currentPersonalBest != null && finalTime >= currentPersonalBest) return this

    return copy(
        segments = segments.map { segment ->
            segment.copy(
                personalBestTimeMilliseconds = resultBySegmentId
                    .getValue(segment.id)
                    .elapsedAtEndMilliseconds,
            )
        },
    )
}

fun RunDefinition.goldTimeMilliseconds(): Long? {
    if (segments.any { it.goldTimeMilliseconds == null }) return null
    return segments.sumOf { requireNotNull(it.goldTimeMilliseconds) }
}

fun configurationIdForTitle(title: String): String =
    title.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
        .ifBlank { "run" }
