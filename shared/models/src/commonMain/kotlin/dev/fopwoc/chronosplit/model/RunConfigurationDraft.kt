package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SegmentConfigurationDraft(
    val id: String,
    val name: String,
    val iconPngBase64: String? = null,
    val splitTimeMilliseconds: Long? = null,
    val bestSegmentMilliseconds: Long? = null,
)

@Serializable
data class RunConfigurationDraft(
    val id: String? = null,
    val title: String,
    val gameName: String? = null,
    val categoryName: String? = null,
    val iconPngBase64: String? = null,
    val attemptCount: Int = 0,
    val offsetMilliseconds: Long = 0,
    val segments: List<SegmentConfigurationDraft>,
)

fun RunDefinition.toConfigurationDraft(): RunConfigurationDraft = RunConfigurationDraft(
    id = id,
    title = title,
    gameName = gameName,
    categoryName = categoryName,
    iconPngBase64 = gameIconPngBase64,
    attemptCount = attemptCount,
    offsetMilliseconds = offsetMilliseconds,
    segments = segments.map { segment ->
        SegmentConfigurationDraft(
            id = segment.id,
            name = segment.title,
            iconPngBase64 = segment.iconPngBase64,
            splitTimeMilliseconds = segment.personalBestTimeMilliseconds,
            bestSegmentMilliseconds = segment.goldTimeMilliseconds,
        )
    },
)

fun RunConfigurationDraft.toRunDefinition(
    layout: LayoutDefinition = LayoutDefinition(),
): RunDefinition {
    require(title.isNotBlank()) { "Run title must not be blank" }
    require(segments.isNotEmpty()) { "A run requires at least one segment" }
    require(segments.all { it.name.isNotBlank() }) { "Segment names must not be blank" }
    require(segments.map { it.id }.filter(String::isNotBlank).distinct().size == segments.count { it.id.isNotBlank() }) {
        "Segment identifiers must be unique"
    }
    require(segments.all { (it.splitTimeMilliseconds ?: 0) >= 0 && (it.bestSegmentMilliseconds ?: 0) >= 0 }) {
        "Split and best-segment times must not be negative"
    }
    val splitTimes = segments.mapNotNull { it.splitTimeMilliseconds }
    require(splitTimes.zipWithNext().all { (previous, next) -> next >= previous }) {
        "Split times must not decrease"
    }
    return RunDefinition(
        id = id?.takeIf(String::isNotBlank) ?: configurationIdForTitle(title),
        title = title.trim(),
        gameName = gameName?.trim()?.takeIf(String::isNotEmpty),
        categoryName = categoryName?.trim()?.takeIf(String::isNotEmpty),
        gameIconPngBase64 = iconPngBase64,
        attemptCount = attemptCount.coerceAtLeast(0),
        offsetMilliseconds = offsetMilliseconds,
        layout = layout,
        segments = segments.mapIndexed { index, segment ->
            SegmentDefinition(
                id = segment.id.takeIf(String::isNotBlank) ?: "segment-${index + 1}",
                title = segment.name.trim(),
                iconPngBase64 = segment.iconPngBase64,
                personalBestTimeMilliseconds = segment.splitTimeMilliseconds,
                goldTimeMilliseconds = segment.bestSegmentMilliseconds,
            )
        },
    )
}

private val DraftJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

fun RunDefinition.toConfigurationDraftJson(): String =
    DraftJson.encodeToString(toConfigurationDraft())

fun parseConfigurationDraftJson(content: String): RunConfigurationDraft =
    DraftJson.decodeFromString(content)
