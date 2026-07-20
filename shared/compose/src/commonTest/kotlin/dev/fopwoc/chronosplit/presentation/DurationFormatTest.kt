package dev.fopwoc.chronosplit.presentation

import dev.fopwoc.chronosplit.model.LayoutAccuracy
import dev.fopwoc.chronosplit.model.LayoutDefinition
import dev.fopwoc.chronosplit.model.LayoutTimer
import dev.fopwoc.chronosplit.model.RunDefinition
import dev.fopwoc.chronosplit.model.RunSnapshot
import dev.fopwoc.chronosplit.model.RunState
import dev.fopwoc.chronosplit.model.RunStatus
import dev.fopwoc.chronosplit.model.SegmentDefinition
import dev.fopwoc.chronosplit.model.SegmentResult
import dev.fopwoc.chronosplit.model.toRunBoardModel
import kotlin.test.Test
import kotlin.test.assertEquals

class DurationFormatTest {
    @Test
    fun formatsMinutesSecondsAndHundredths() {
        assertEquals("0:00.00", formatDuration(0))
        assertEquals("2:03.45", formatDuration(123_456))
    }

    @Test
    fun timerOmitsZeroMinutesLikeLiveSplit() {
        assertEquals("4.94", formatTimerDuration(4_940))
        assertEquals("2:03.45", formatTimerDuration(123_456))
    }

    @Test
    fun dropDecimalsOnlyAppliesToDeltasOfAtLeastOneMinute() {
        assertEquals("−4.9", formatDelta(-4_940, LayoutAccuracy.TENTHS, dropDecimals = true))
        assertEquals("+1:03", formatDelta(63_450, LayoutAccuracy.TENTHS, dropDecimals = true))
    }

    @Test
    fun normalTimerShowsTotalWhileSegmentTimerShowsOnlyCurrentSegment() {
        fun model(segmentTimer: Boolean) = RunSnapshot(
            definition = RunDefinition(
                id = "timer",
                title = "Timer",
                layout = LayoutDefinition(timer = LayoutTimer(isSegmentTimer = segmentTimer)),
                segments = listOf(
                    SegmentDefinition("one", "One"),
                    SegmentDefinition("two", "Two"),
                ),
            ),
            state = RunState(
                runId = "timer",
                status = RunStatus.RUNNING,
                activeSegmentIndex = 1,
                results = listOf(SegmentResult("one", 2_000, 2_000)),
            ),
            capturedAtEpochMilliseconds = 2_500,
            elapsedMilliseconds = 2_500,
        ).toRunBoardModel()

        assertEquals(2_500, runTimerMilliseconds(model(segmentTimer = false)))
        assertEquals(500, runTimerMilliseconds(model(segmentTimer = true)))
    }
}
