package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class LayoutColor(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float = 1f,
) {
    init {
        require(red in 0f..1f && green in 0f..1f && blue in 0f..1f && alpha in 0f..1f) {
            "Layout colors must use normalized RGBA values"
        }
    }
}

@Serializable
enum class LayoutDirection { VERTICAL, HORIZONTAL }

@Serializable
enum class LayoutAccuracy { SECONDS, TENTHS, HUNDREDTHS, MILLISECONDS }

@Serializable
enum class LayoutColumnStartWith { EMPTY, COMPARISON_TIME, COMPARISON_SEGMENT_TIME, POSSIBLE_TIME_SAVE }

@Serializable
enum class LayoutColumnUpdateWith {
    DONT_UPDATE,
    SPLIT_TIME,
    DELTA,
    DELTA_WITH_FALLBACK,
    SEGMENT_TIME,
    SEGMENT_DELTA,
    SEGMENT_DELTA_WITH_FALLBACK,
}

@Serializable
enum class LayoutColumnUpdateTrigger { ON_STARTING_SEGMENT, CONTEXTUAL, ON_ENDING_SEGMENT }

@Serializable
data class LayoutSplitColumn(
    val name: String,
    val startWith: LayoutColumnStartWith = LayoutColumnStartWith.EMPTY,
    val updateWith: LayoutColumnUpdateWith = LayoutColumnUpdateWith.DONT_UPDATE,
    val updateTrigger: LayoutColumnUpdateTrigger = LayoutColumnUpdateTrigger.CONTEXTUAL,
    val comparisonOverride: String? = null,
)

private fun defaultSplitColumns() = listOf(
    LayoutSplitColumn(
        name = "Time",
        startWith = LayoutColumnStartWith.COMPARISON_TIME,
        updateWith = LayoutColumnUpdateWith.SPLIT_TIME,
        updateTrigger = LayoutColumnUpdateTrigger.ON_ENDING_SEGMENT,
    ),
    LayoutSplitColumn(
        name = "+/−",
        updateWith = LayoutColumnUpdateWith.DELTA,
        updateTrigger = LayoutColumnUpdateTrigger.CONTEXTUAL,
    ),
)

@Serializable
data class LayoutGeneral(
    val background: LayoutColor = LayoutColor(0.06f, 0.06f, 0.06f),
    val bestSegmentColor: LayoutColor = LayoutColor(1f, 0.8333334f, 0f),
    val aheadGainingTimeColor: LayoutColor = LayoutColor(0f, 0.8f, 0.21333352f),
    val aheadLosingTimeColor: LayoutColor = LayoutColor(0.38f, 0.82f, 0.49733347f),
    val behindGainingTimeColor: LayoutColor = LayoutColor(0.82f, 0.38f, 0.38f),
    val behindLosingTimeColor: LayoutColor = LayoutColor(0.8f, 0f, 0f),
    val notRunningColor: LayoutColor = LayoutColor(0.67f, 0.67f, 0.67f),
    val personalBestColor: LayoutColor = LayoutColor(0.08f, 0.64733326f, 1f),
    val pausedColor: LayoutColor = LayoutColor(0.48f, 0.48f, 0.48f),
    val thinSeparatorsColor: LayoutColor = LayoutColor(1f, 1f, 1f, 0.09f),
    val separatorsColor: LayoutColor = LayoutColor(1f, 1f, 1f, 0.35f),
    val textColor: LayoutColor = LayoutColor(1f, 1f, 1f),
)

@Serializable
data class LayoutTitle(
    val enabled: Boolean = true,
    val showGameName: Boolean = true,
    val showCategoryName: Boolean = true,
    val showAttemptCount: Boolean = true,
    val showFinishedRunsCount: Boolean = false,
    val showVariables: Boolean = true,
)

@Serializable
data class LayoutSplits(
    val visualSplitCount: Int? = null,
    val splitPreviewCount: Int = 0,
    val showThinSeparators: Boolean = true,
    val separatorLastSplit: Boolean = true,
    val alwaysShowLastSplit: Boolean = true,
    val fillWithBlankSpace: Boolean = true,
    val displayTwoRows: Boolean = false,
    val showColumnLabels: Boolean = false,
    val splitTimeAccuracy: LayoutAccuracy = LayoutAccuracy.HUNDREDTHS,
    val segmentTimeAccuracy: LayoutAccuracy = LayoutAccuracy.HUNDREDTHS,
    val deltaTimeAccuracy: LayoutAccuracy = LayoutAccuracy.HUNDREDTHS,
    val deltaDropDecimals: Boolean = false,
    val currentSplitGradientStart: LayoutColor = LayoutColor(0.2f, 0.4509804f, 0.95686275f),
    val currentSplitGradientEnd: LayoutColor = LayoutColor(0.08235294f, 0.20784314f, 0.45490196f),
    val columns: List<LayoutSplitColumn> = defaultSplitColumns(),
)

@Serializable
data class LayoutTimer(
    val height: Int? = null,
    val colorOverride: LayoutColor? = null,
    val showGradient: Boolean = true,
    val accuracy: LayoutAccuracy = LayoutAccuracy.HUNDREDTHS,
    val isSegmentTimer: Boolean = false,
)

@Serializable
data class LayoutPreviousSegment(
    val enabled: Boolean = false,
    val comparisonOverride: String? = null,
    val displayTwoRows: Boolean = false,
    val labelColor: LayoutColor? = null,
    val dropDecimals: Boolean = true,
    val accuracy: LayoutAccuracy = LayoutAccuracy.TENTHS,
    val showPossibleTimeSave: Boolean = false,
)

@Serializable
data class LayoutDefinition(
    val direction: LayoutDirection = LayoutDirection.VERTICAL,
    val general: LayoutGeneral = LayoutGeneral(),
    val title: LayoutTitle = LayoutTitle(),
    val splits: LayoutSplits = LayoutSplits(),
    val timer: LayoutTimer = LayoutTimer(),
    val previousSegment: LayoutPreviousSegment = LayoutPreviousSegment(),
)

class LayoutImportException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

fun parseLs1lLayout(content: String): LayoutDefinition {
    val root = try {
        Json.parseToJsonElement(content).jsonObject
    } catch (error: Throwable) {
        throw LayoutImportException("Layout is not valid JSON", error)
    }

    val components = root["components"] as? JsonArray
        ?: throw LayoutImportException("Layout must contain a components array")
    val generalObject = root["general"] as? JsonObject
        ?: throw LayoutImportException("Layout must contain a general object")

    var title = LayoutTitle(enabled = false)
    var splits = LayoutSplits()
    var timer = LayoutTimer()
    var previousSegment = LayoutPreviousSegment()

    components.forEach { componentElement ->
        val component = componentElement as? JsonObject ?: return@forEach
        val entry = component.entries.singleOrNull() ?: return@forEach
        val settings = entry.value as? JsonObject ?: return@forEach

        when (entry.key) {
            "Title" -> {
                title = LayoutTitle(
                    enabled = true,
                    showGameName = settings.boolean("show_game_name", true),
                    showCategoryName = settings.boolean("show_category_name", true),
                    showAttemptCount = settings.boolean("show_attempt_count", true),
                    showFinishedRunsCount = settings.boolean("show_finished_runs_count", false),
                    showVariables = settings.boolean("show_variables", true),
                )
            }

            "Splits" -> {
                val gradient = settings.gradient("current_split_gradient")
                val defaults = LayoutSplits()
                val columns = (settings["columns"] as? JsonArray)
                    ?.mapNotNull { it as? JsonObject }
                    ?.map { column ->
                        LayoutSplitColumn(
                            name = column.string("name", "Column"),
                            startWith = column.columnStartWith("start_with"),
                            updateWith = column.columnUpdateWith("update_with"),
                            updateTrigger = column.columnUpdateTrigger("update_trigger"),
                            comparisonOverride = column.nullableString("comparison_override"),
                        )
                    }
                    ?.takeIf { it.isNotEmpty() }
                    ?: defaults.columns
                splits = LayoutSplits(
                    visualSplitCount = settings.intOrNull("visual_split_count")?.takeIf { it > 0 },
                    splitPreviewCount = settings.int("split_preview_count", 0).coerceAtLeast(0),
                    showThinSeparators = settings.boolean("show_thin_separators", true),
                    separatorLastSplit = settings.boolean("separator_last_split", true),
                    alwaysShowLastSplit = settings.boolean("always_show_last_split", true),
                    fillWithBlankSpace = settings.boolean("fill_with_blank_space", true),
                    displayTwoRows = settings.boolean("display_two_rows", false),
                    showColumnLabels = settings.boolean("show_column_labels", defaults.showColumnLabels),
                    splitTimeAccuracy = settings.accuracy("split_time_accuracy", LayoutAccuracy.HUNDREDTHS),
                    segmentTimeAccuracy = settings.accuracy("segment_time_accuracy", LayoutAccuracy.HUNDREDTHS),
                    deltaTimeAccuracy = settings.accuracy("delta_time_accuracy", LayoutAccuracy.HUNDREDTHS),
                    deltaDropDecimals = settings.boolean("delta_drop_decimals", false),
                    currentSplitGradientStart = gradient?.first ?: defaults.currentSplitGradientStart,
                    currentSplitGradientEnd = gradient?.second ?: defaults.currentSplitGradientEnd,
                    columns = columns,
                )
            }

            "Timer" -> {
                timer = LayoutTimer(
                    height = settings.intOrNull("height")?.takeIf { it > 0 },
                    colorOverride = settings["color_override"].layoutColorOrNull(),
                    showGradient = settings.boolean("show_gradient", true),
                    accuracy = settings.accuracy("accuracy", LayoutAccuracy.HUNDREDTHS),
                    isSegmentTimer = settings.boolean("is_segment_timer", false),
                )
            }

            "PreviousSegment" -> {
                previousSegment = LayoutPreviousSegment(
                    enabled = true,
                    comparisonOverride = settings.nullableString("comparison_override"),
                    displayTwoRows = settings.boolean("display_two_rows", false),
                    labelColor = settings["label_color"].layoutColorOrNull(),
                    dropDecimals = settings.boolean("drop_decimals", true),
                    accuracy = settings.accuracy("accuracy", LayoutAccuracy.TENTHS),
                    showPossibleTimeSave = settings.boolean("show_possible_time_save", false),
                )
            }
        }
    }

    val defaults = LayoutGeneral()
    val general = LayoutGeneral(
        background = generalObject["background"].layoutColorOrNull() ?: defaults.background,
        bestSegmentColor = generalObject.color("best_segment_color", defaults.bestSegmentColor),
        aheadGainingTimeColor = generalObject.color("ahead_gaining_time_color", defaults.aheadGainingTimeColor),
        aheadLosingTimeColor = generalObject.color("ahead_losing_time_color", defaults.aheadLosingTimeColor),
        behindGainingTimeColor = generalObject.color("behind_gaining_time_color", defaults.behindGainingTimeColor),
        behindLosingTimeColor = generalObject.color("behind_losing_time_color", defaults.behindLosingTimeColor),
        notRunningColor = generalObject.color("not_running_color", defaults.notRunningColor),
        personalBestColor = generalObject.color("personal_best_color", defaults.personalBestColor),
        pausedColor = generalObject.color("paused_color", defaults.pausedColor),
        thinSeparatorsColor = generalObject.color("thin_separators_color", defaults.thinSeparatorsColor),
        separatorsColor = generalObject.color("separators_color", defaults.separatorsColor),
        textColor = generalObject.color("text_color", defaults.textColor),
    )

    return LayoutDefinition(
        direction = when (generalObject.string("direction", "Vertical").lowercase()) {
            "horizontal" -> LayoutDirection.HORIZONTAL
            else -> LayoutDirection.VERTICAL
        },
        general = general,
        title = title,
        splits = splits,
        timer = timer,
        previousSegment = previousSegment,
    )
}

fun exportLs1lLayout(layout: LayoutDefinition): String = buildJsonObject {
    put("general", buildJsonObject {
        put("direction", JsonPrimitive(if (layout.direction == LayoutDirection.HORIZONTAL) "Horizontal" else "Vertical"))
        put("background", layout.general.background.toJson())
        put("best_segment_color", layout.general.bestSegmentColor.toJson())
        put("ahead_gaining_time_color", layout.general.aheadGainingTimeColor.toJson())
        put("ahead_losing_time_color", layout.general.aheadLosingTimeColor.toJson())
        put("behind_gaining_time_color", layout.general.behindGainingTimeColor.toJson())
        put("behind_losing_time_color", layout.general.behindLosingTimeColor.toJson())
        put("not_running_color", layout.general.notRunningColor.toJson())
        put("personal_best_color", layout.general.personalBestColor.toJson())
        put("paused_color", layout.general.pausedColor.toJson())
        put("thin_separators_color", layout.general.thinSeparatorsColor.toJson())
        put("separators_color", layout.general.separatorsColor.toJson())
        put("text_color", layout.general.textColor.toJson())
    })
    put("components", buildJsonArray {
        if (layout.title.enabled) add(buildJsonObject {
            put("Title", buildJsonObject {
                put("show_game_name", layout.title.showGameName)
                put("show_category_name", layout.title.showCategoryName)
                put("show_attempt_count", layout.title.showAttemptCount)
                put("show_finished_runs_count", layout.title.showFinishedRunsCount)
                put("show_variables", layout.title.showVariables)
            })
        })
        add(buildJsonObject {
            put("Splits", buildJsonObject {
                layout.splits.visualSplitCount?.let { put("visual_split_count", it) }
                put("split_preview_count", layout.splits.splitPreviewCount)
                put("show_thin_separators", layout.splits.showThinSeparators)
                put("separator_last_split", layout.splits.separatorLastSplit)
                put("always_show_last_split", layout.splits.alwaysShowLastSplit)
                put("fill_with_blank_space", layout.splits.fillWithBlankSpace)
                put("display_two_rows", layout.splits.displayTwoRows)
                put("show_column_labels", layout.splits.showColumnLabels)
                put("split_time_accuracy", layout.splits.splitTimeAccuracy.ls1lName())
                put("segment_time_accuracy", layout.splits.segmentTimeAccuracy.ls1lName())
                put("delta_time_accuracy", layout.splits.deltaTimeAccuracy.ls1lName())
                put("delta_drop_decimals", layout.splits.deltaDropDecimals)
                put("current_split_gradient", buildJsonObject {
                    put("Gradient", buildJsonArray {
                        add(layout.splits.currentSplitGradientStart.toJson())
                        add(layout.splits.currentSplitGradientEnd.toJson())
                    })
                })
                put("columns", buildJsonArray {
                    layout.splits.columns.forEach { column ->
                        add(buildJsonObject {
                            put("name", column.name)
                            put("start_with", column.startWith.ls1lName())
                            put("update_with", column.updateWith.ls1lName())
                            put("update_trigger", column.updateTrigger.ls1lName())
                            column.comparisonOverride?.let { put("comparison_override", it) }
                        })
                    }
                })
            })
        })
        add(buildJsonObject {
            put("Timer", buildJsonObject {
                layout.timer.height?.let { put("height", it) }
                layout.timer.colorOverride?.let { put("color_override", it.toJson()) }
                put("show_gradient", layout.timer.showGradient)
                put("accuracy", layout.timer.accuracy.ls1lName())
                put("is_segment_timer", layout.timer.isSegmentTimer)
            })
        })
        if (layout.previousSegment.enabled) add(buildJsonObject {
            put("PreviousSegment", buildJsonObject {
                layout.previousSegment.comparisonOverride?.let { put("comparison_override", it) }
                put("display_two_rows", layout.previousSegment.displayTwoRows)
                layout.previousSegment.labelColor?.let { put("label_color", it.toJson()) }
                put("drop_decimals", layout.previousSegment.dropDecimals)
                put("accuracy", layout.previousSegment.accuracy.ls1lName())
                put("show_possible_time_save", layout.previousSegment.showPossibleTimeSave)
            })
        })
    })
}.toString()

private fun LayoutColor.toJson(): JsonArray = buildJsonArray {
    add(JsonPrimitive(red))
    add(JsonPrimitive(green))
    add(JsonPrimitive(blue))
    add(JsonPrimitive(alpha))
}

private fun LayoutAccuracy.ls1lName(): String = when (this) {
    LayoutAccuracy.SECONDS -> "Seconds"
    LayoutAccuracy.TENTHS -> "Tenths"
    LayoutAccuracy.HUNDREDTHS -> "Hundredths"
    LayoutAccuracy.MILLISECONDS -> "Milliseconds"
}

private fun LayoutColumnStartWith.ls1lName(): String = when (this) {
    LayoutColumnStartWith.EMPTY -> "Empty"
    LayoutColumnStartWith.COMPARISON_TIME -> "ComparisonTime"
    LayoutColumnStartWith.COMPARISON_SEGMENT_TIME -> "ComparisonSegmentTime"
    LayoutColumnStartWith.POSSIBLE_TIME_SAVE -> "PossibleTimeSave"
}

private fun LayoutColumnUpdateWith.ls1lName(): String = when (this) {
    LayoutColumnUpdateWith.DONT_UPDATE -> "DontUpdate"
    LayoutColumnUpdateWith.SPLIT_TIME -> "SplitTime"
    LayoutColumnUpdateWith.DELTA -> "Delta"
    LayoutColumnUpdateWith.DELTA_WITH_FALLBACK -> "DeltaWithFallback"
    LayoutColumnUpdateWith.SEGMENT_TIME -> "SegmentTime"
    LayoutColumnUpdateWith.SEGMENT_DELTA -> "SegmentDelta"
    LayoutColumnUpdateWith.SEGMENT_DELTA_WITH_FALLBACK -> "SegmentDeltaWithFallback"
}

private fun LayoutColumnUpdateTrigger.ls1lName(): String = when (this) {
    LayoutColumnUpdateTrigger.ON_STARTING_SEGMENT -> "OnStartingSegment"
    LayoutColumnUpdateTrigger.CONTEXTUAL -> "Contextual"
    LayoutColumnUpdateTrigger.ON_ENDING_SEGMENT -> "OnEndingSegment"
}

private fun JsonObject.boolean(name: String, fallback: Boolean): Boolean =
    this[name]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: fallback

private fun JsonObject.int(name: String, fallback: Int): Int =
    this[name]?.jsonPrimitive?.content?.toIntOrNull() ?: fallback

private fun JsonObject.intOrNull(name: String): Int? =
    this[name]?.jsonPrimitive?.content?.toIntOrNull()

private fun JsonObject.string(name: String, fallback: String): String =
    this[name]?.jsonPrimitive?.content ?: fallback

private fun JsonObject.nullableString(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.columnStartWith(name: String): LayoutColumnStartWith = when (string(name, "")) {
    "ComparisonTime" -> LayoutColumnStartWith.COMPARISON_TIME
    "ComparisonSegmentTime" -> LayoutColumnStartWith.COMPARISON_SEGMENT_TIME
    "PossibleTimeSave" -> LayoutColumnStartWith.POSSIBLE_TIME_SAVE
    else -> LayoutColumnStartWith.EMPTY
}

private fun JsonObject.columnUpdateWith(name: String): LayoutColumnUpdateWith = when (string(name, "")) {
    "SplitTime" -> LayoutColumnUpdateWith.SPLIT_TIME
    "Delta" -> LayoutColumnUpdateWith.DELTA
    "DeltaWithFallback" -> LayoutColumnUpdateWith.DELTA_WITH_FALLBACK
    "SegmentTime" -> LayoutColumnUpdateWith.SEGMENT_TIME
    "SegmentDelta" -> LayoutColumnUpdateWith.SEGMENT_DELTA
    "SegmentDeltaWithFallback" -> LayoutColumnUpdateWith.SEGMENT_DELTA_WITH_FALLBACK
    else -> LayoutColumnUpdateWith.DONT_UPDATE
}

private fun JsonObject.columnUpdateTrigger(name: String): LayoutColumnUpdateTrigger = when (string(name, "")) {
    "OnStartingSegment" -> LayoutColumnUpdateTrigger.ON_STARTING_SEGMENT
    "OnEndingSegment" -> LayoutColumnUpdateTrigger.ON_ENDING_SEGMENT
    else -> LayoutColumnUpdateTrigger.CONTEXTUAL
}

private fun JsonObject.accuracy(name: String, fallback: LayoutAccuracy): LayoutAccuracy =
    when (string(name, "").lowercase()) {
        "seconds" -> LayoutAccuracy.SECONDS
        "tenths" -> LayoutAccuracy.TENTHS
        "hundredths" -> LayoutAccuracy.HUNDREDTHS
        "milliseconds" -> LayoutAccuracy.MILLISECONDS
        else -> fallback
    }

private fun JsonObject.color(name: String, fallback: LayoutColor): LayoutColor =
    this[name].layoutColorOrNull() ?: fallback

private fun JsonObject.gradient(name: String): Pair<LayoutColor, LayoutColor>? {
    val gradient = this[name] as? JsonObject ?: return null
    val colors = gradient.values.firstOrNull() as? JsonArray ?: return null
    if (colors.size < 2) return null
    return colors[0].layoutColorOrNull()?.let { start ->
        colors[1].layoutColorOrNull()?.let { end -> start to end }
    }
}

private fun JsonElement?.layoutColorOrNull(): LayoutColor? {
    val values = when (this) {
        is JsonArray -> this
        is JsonObject -> values.firstOrNull() as? JsonArray
        else -> null
    } ?: return null
    if (values.size < 4) return null

    val channels = values.map { value -> (value as? JsonPrimitive)?.content?.toFloatOrNull() }
    if (channels.any { it == null }) return null
    return LayoutColor(
        red = requireNotNull(channels[0]),
        green = requireNotNull(channels[1]),
        blue = requireNotNull(channels[2]),
        alpha = requireNotNull(channels[3]),
    )
}
