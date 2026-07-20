package dev.fopwoc.chronosplit.model

import kotlin.test.Test
import kotlin.test.assertEquals

class LayoutDefinitionTest {
    @Test
    fun importsTheLiveSplitComponentSettingsUsedByTheSampleLayout() {
        val layout = parseLs1lLayout(
            """
            {
              "components": [
                {"Title": {"show_game_name": true, "show_category_name": true, "show_attempt_count": true}},
                {"Splits": {
                  "visual_split_count": 16,
                  "split_preview_count": 1,
                  "show_thin_separators": true,
                  "show_column_labels": false,
                  "split_time_accuracy": "Seconds",
                  "segment_time_accuracy": "Hundredths",
                  "delta_time_accuracy": "Tenths",
                  "delta_drop_decimals": true,
                  "columns": [
                    {"name": "Time", "start_with": "ComparisonTime", "update_with": "SplitTime", "update_trigger": "OnEndingSegment", "comparison_override": null},
                    {"name": "+/−", "start_with": "Empty", "update_with": "Delta", "update_trigger": "Contextual", "comparison_override": null}
                  ],
                  "current_split_gradient": {"Vertical": [[0.2, 0.45, 0.95, 1], [0.08, 0.2, 0.45, 1]]}
                }},
                {"Timer": {"height": 60, "show_gradient": true, "accuracy": "Hundredths", "is_segment_timer": false}},
                {"PreviousSegment": {"accuracy": "Tenths", "drop_decimals": true}}
              ],
              "general": {
                "direction": "Vertical",
                "background": {"Plain": [0.06, 0.06, 0.06, 1]},
                "best_segment_color": [1, 0.8333334, 0, 1],
                "behind_losing_time_color": [0.8, 0, 0, 1],
                "text_color": [1, 1, 1, 1]
              }
            }
            """.trimIndent(),
        )

        assertEquals(LayoutDirection.VERTICAL, layout.direction)
        assertEquals(true, layout.title.enabled)
        assertEquals(16, layout.splits.visualSplitCount)
        assertEquals(1, layout.splits.splitPreviewCount)
        assertEquals(false, layout.splits.showColumnLabels)
        assertEquals(LayoutAccuracy.SECONDS, layout.splits.splitTimeAccuracy)
        assertEquals(LayoutAccuracy.TENTHS, layout.splits.deltaTimeAccuracy)
        assertEquals(true, layout.splits.deltaDropDecimals)
        assertEquals(LayoutColumnStartWith.COMPARISON_TIME, layout.splits.columns[0].startWith)
        assertEquals(LayoutColumnUpdateWith.SPLIT_TIME, layout.splits.columns[0].updateWith)
        assertEquals(LayoutColumnUpdateTrigger.ON_ENDING_SEGMENT, layout.splits.columns[0].updateTrigger)
        assertEquals(LayoutColumnUpdateWith.DELTA, layout.splits.columns[1].updateWith)
        assertEquals(LayoutColumnUpdateTrigger.CONTEXTUAL, layout.splits.columns[1].updateTrigger)
        assertEquals(60, layout.timer.height)
        assertEquals(true, layout.previousSegment.enabled)
        assertEquals(1f, layout.general.bestSegmentColor.red)
    }

    @Test
    fun rejectsJsonThatIsNotAnLs1lLayout() {
        val result = runCatching { parseLs1lLayout("{}") }

        assertEquals(true, result.isFailure)
    }

    @Test
    fun exportedLayoutRoundTripsEditableSettings() {
        val layout = LayoutDefinition(
            title = LayoutTitle(showAttemptCount = false),
            splits = LayoutSplits(
                visualSplitCount = 8,
                splitPreviewCount = 2,
                showColumnLabels = true,
                fillWithBlankSpace = false,
                deltaTimeAccuracy = LayoutAccuracy.TENTHS,
            ),
            timer = LayoutTimer(
                accuracy = LayoutAccuracy.MILLISECONDS,
                isSegmentTimer = true,
                showGradient = false,
            ),
            previousSegment = LayoutPreviousSegment(enabled = true),
        )

        val imported = parseLs1lLayout(exportLs1lLayout(layout))

        assertEquals(layout, imported)
    }
}
