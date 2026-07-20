package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LayoutEditorDraft(
    val titleEnabled: Boolean,
    val showGameName: Boolean,
    val showCategoryName: Boolean,
    val showAttemptCount: Boolean,
    val previousSegmentEnabled: Boolean,
    val showThinSeparators: Boolean,
    val fillWithBlankSpace: Boolean,
    val alwaysShowLastSplit: Boolean,
    val showColumnLabels: Boolean,
    val visualSplitCount: Int?,
    val splitPreviewCount: Int,
    val splitTimeAccuracy: LayoutAccuracy,
    val deltaTimeAccuracy: LayoutAccuracy,
    val timerAccuracy: LayoutAccuracy,
    val segmentTimer: Boolean,
    val timerGradient: Boolean,
)

fun LayoutDefinition.toEditorDraft(): LayoutEditorDraft = LayoutEditorDraft(
    titleEnabled = title.enabled,
    showGameName = title.showGameName,
    showCategoryName = title.showCategoryName,
    showAttemptCount = title.showAttemptCount,
    previousSegmentEnabled = previousSegment.enabled,
    showThinSeparators = splits.showThinSeparators,
    fillWithBlankSpace = splits.fillWithBlankSpace,
    alwaysShowLastSplit = splits.alwaysShowLastSplit,
    showColumnLabels = splits.showColumnLabels,
    visualSplitCount = splits.visualSplitCount,
    splitPreviewCount = splits.splitPreviewCount,
    splitTimeAccuracy = splits.splitTimeAccuracy,
    deltaTimeAccuracy = splits.deltaTimeAccuracy,
    timerAccuracy = timer.accuracy,
    segmentTimer = timer.isSegmentTimer,
    timerGradient = timer.showGradient,
)

fun LayoutDefinition.withEditorDraft(draft: LayoutEditorDraft): LayoutDefinition = copy(
    title = title.copy(
        enabled = draft.titleEnabled,
        showGameName = draft.showGameName,
        showCategoryName = draft.showCategoryName,
        showAttemptCount = draft.showAttemptCount,
    ),
    splits = splits.copy(
        showThinSeparators = draft.showThinSeparators,
        fillWithBlankSpace = draft.fillWithBlankSpace,
        alwaysShowLastSplit = draft.alwaysShowLastSplit,
        showColumnLabels = draft.showColumnLabels,
        visualSplitCount = draft.visualSplitCount?.takeIf { it > 0 },
        splitPreviewCount = draft.splitPreviewCount.coerceAtLeast(0),
        splitTimeAccuracy = draft.splitTimeAccuracy,
        deltaTimeAccuracy = draft.deltaTimeAccuracy,
    ),
    timer = timer.copy(
        accuracy = draft.timerAccuracy,
        isSegmentTimer = draft.segmentTimer,
        showGradient = draft.timerGradient,
    ),
    previousSegment = previousSegment.copy(enabled = draft.previousSegmentEnabled),
)

private val LayoutDraftJson = Json { ignoreUnknownKeys = true }

fun LayoutDefinition.toEditorDraftJson(): String =
    LayoutDraftJson.encodeToString(toEditorDraft())

fun parseLayoutEditorDraftJson(content: String): LayoutEditorDraft =
    LayoutDraftJson.decodeFromString(content)
