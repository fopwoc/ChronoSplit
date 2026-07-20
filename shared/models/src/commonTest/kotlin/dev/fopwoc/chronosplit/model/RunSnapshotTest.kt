package dev.fopwoc.chronosplit.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RunSnapshotTest {
    private val definition = RunDefinition(
        id = "any-percent",
        title = "Any Percent",
        segments = listOf(SegmentDefinition("finish", "Finish")),
    )

    @Test
    fun runningSnapshotTicksFromCaptureTime() {
        val snapshot = RunSnapshot(
            definition = definition,
            state = RunState(
                runId = definition.id,
                status = RunStatus.RUNNING,
                startedAtEpochMilliseconds = 10_000,
            ),
            capturedAtEpochMilliseconds = 12_000,
            elapsedMilliseconds = 2_000,
        )

        assertEquals(2_750, snapshot.elapsedAt(12_750))
        assertEquals(2_000, snapshot.elapsedAt(11_000))
    }

    @Test
    fun stoppedSnapshotDoesNotTick() {
        val snapshot = RunSnapshot(
            definition = definition,
            state = RunState(runId = definition.id, status = RunStatus.PAUSED),
            capturedAtEpochMilliseconds = 12_000,
            elapsedMilliseconds = 2_000,
        )

        assertEquals(2_000, snapshot.elapsedAt(20_000))
    }

    @Test
    fun boardModelContainsPresentationRowsAndActions() {
        val snapshot = RunSnapshot(
            definition = RunDefinition(
                id = "any-percent",
                title = "Any Percent",
                segments = listOf(
                    SegmentDefinition(
                        "intro",
                        "Intro",
                        goldTimeMilliseconds = 1_000,
                        personalBestTimeMilliseconds = 1_000,
                    ),
                    SegmentDefinition("finish", "Finish", personalBestTimeMilliseconds = 1_500),
                ),
            ),
            state = RunState(
                runId = "any-percent",
                status = RunStatus.RUNNING,
                activeSegmentIndex = 1,
                results = listOf(
                    SegmentResult(
                        segmentId = "intro",
                        segmentDurationMilliseconds = 1_250,
                        elapsedAtEndMilliseconds = 1_250,
                    ),
                ),
            ),
            capturedAtEpochMilliseconds = 10_000,
            elapsedMilliseconds = 1_250,
        )

        val model = snapshot.toRunBoardModel(
            nowEpochMilliseconds = 10_500,
            primaryActionTitle = "Next Segment",
            pauseActionTitle = "Pause",
        )

        assertEquals("Any Percent", model.title)
        assertEquals(1_750, model.elapsedMilliseconds)
        assertEquals(250, model.segments.first().goldDeltaMilliseconds)
        assertEquals(250, model.segments.first().comparisonDeltaMilliseconds)
        assertEquals(250, model.segments[1].comparisonDeltaMilliseconds)
        assertNull(model.segments[1].segmentDeltaMilliseconds)
        assertEquals(1_750, model.segments[1].liveSplitTimeMilliseconds)
        assertEquals(1_000, model.segments.first().goldTimeMilliseconds)
        assertEquals(false, model.segments.first().isGold)
        assertEquals(true, model.segments[1].isActive)
        assertEquals(true, model.segments.first().isCompleted)
        assertEquals("Next Segment", model.primaryActionTitle)
    }

    @Test
    fun goldDeltaUsesSegmentDurationInsteadOfCumulativeElapsedTime() {
        val definition = RunDefinition(
            id = "any-percent",
            title = "Any Percent",
            segments = listOf(
                SegmentDefinition("intro", "Intro", goldTimeMilliseconds = 1_000),
                SegmentDefinition("finish", "Finish", goldTimeMilliseconds = 500),
            ),
        )
        val snapshot = RunSnapshot(
            definition = definition,
            state = RunState(
                runId = definition.id,
                status = RunStatus.RUNNING,
                activeSegmentIndex = 1,
                results = listOf(
                    SegmentResult("intro", 1_200, 1_200),
                    SegmentResult("finish", 700, 1_900),
                ),
            ),
            capturedAtEpochMilliseconds = 10_000,
            elapsedMilliseconds = 1_900,
        )

        val model = snapshot.toRunBoardModel()

        assertEquals(200, model.segments[0].goldDeltaMilliseconds)
        assertEquals(200, model.segments[1].goldDeltaMilliseconds)
        assertEquals(1_500, model.goldTimeMilliseconds)
    }

    @Test
    fun recordedBestSegmentIsMarkedAsGoldAfterGoldTimeIsUpdated() {
        val definition = RunDefinition(
            id = "gold",
            title = "Gold",
            segments = listOf(SegmentDefinition("finish", "Finish", goldTimeMilliseconds = 500)),
        )
        val snapshot = RunSnapshot(
            definition = definition,
            state = RunState(
                runId = definition.id,
                status = RunStatus.FINISHED,
                results = listOf(SegmentResult("finish", 500, 500, isBestSegment = true)),
            ),
            capturedAtEpochMilliseconds = 10_000,
            elapsedMilliseconds = 500,
        )

        assertEquals(true, snapshot.toRunBoardModel().segments.single().isGold)
    }

    @Test
    fun splitColumnsProgressFromFirstAttemptToPersonalBestComparison() {
        val emptyDefinition = RunDefinition(
            id = "progression",
            title = "Progression",
            segments = listOf(
                SegmentDefinition("one", "One"),
                SegmentDefinition("two", "Two"),
            ),
        )
        val firstResults = listOf(
            SegmentResult("one", 3_000, 3_000, isBestSegment = true),
            SegmentResult("two", 3_000, 6_000, isBestSegment = true),
        )
        val firstDefinition = emptyDefinition.withGoldSplits(firstResults)
        val firstFinish = RunSnapshot(
            definition = firstDefinition,
            state = RunState(
                runId = firstDefinition.id,
                status = RunStatus.FINISHED,
                activeSegmentIndex = 1,
                results = firstResults,
            ),
            capturedAtEpochMilliseconds = 6_000,
            elapsedMilliseconds = 6_000,
        ).toRunBoardModel()

        assertEquals(listOf(3_000L, 6_000L), firstFinish.segments.map { it.columns[0].valueMilliseconds })
        assertEquals(listOf(null, null), firstFinish.segments.map { it.columns[1].valueMilliseconds })
        assertEquals(
            listOf(RunBoardSemanticColor.BEST_SEGMENT, RunBoardSemanticColor.BEST_SEGMENT),
            firstFinish.segments.map { it.columns[1].semanticColor },
        )

        val personalBestDefinition = firstDefinition.withPersonalBest(firstResults)
        val afterReset = RunSnapshot(
            definition = personalBestDefinition,
            state = RunState(runId = personalBestDefinition.id),
            capturedAtEpochMilliseconds = 6_000,
            elapsedMilliseconds = 0,
        ).toRunBoardModel()

        assertEquals(listOf(3_000L, 6_000L), afterReset.segments.map { it.columns[0].valueMilliseconds })
        assertEquals(listOf(null, null), afterReset.segments.map { it.columns[1].valueMilliseconds })

        val nextAttempt = RunSnapshot(
            definition = personalBestDefinition,
            state = RunState(
                runId = personalBestDefinition.id,
                status = RunStatus.RUNNING,
                activeSegmentIndex = 1,
                results = listOf(SegmentResult("one", 5_000, 5_000)),
            ),
            capturedAtEpochMilliseconds = 5_000,
            elapsedMilliseconds = 5_000,
        ).toRunBoardModel()

        assertEquals(5_000, nextAttempt.segments[0].columns[0].valueMilliseconds)
        assertEquals(2_000, nextAttempt.segments[0].columns[1].valueMilliseconds)
        assertEquals(6_000, nextAttempt.segments[1].columns[0].valueMilliseconds)
        assertNull(nextAttempt.segments[1].columns[1].valueMilliseconds)
    }

    @Test
    fun liveSegmentDeltaAppearsOnlyAfterLiveSplitThreshold() {
        val definition = RunDefinition(
            id = "threshold",
            title = "Threshold",
            segments = listOf(
                SegmentDefinition(
                    id = "finish",
                    title = "Finish",
                    goldTimeMilliseconds = 1_000,
                    personalBestTimeMilliseconds = 1_500,
                ),
            ),
        )

        fun modelAt(elapsed: Long) = RunSnapshot(
            definition = definition,
            state = RunState(
                runId = definition.id,
                status = RunStatus.RUNNING,
                startedAtEpochMilliseconds = 0,
            ),
            capturedAtEpochMilliseconds = elapsed,
            elapsedMilliseconds = elapsed,
        ).toRunBoardModel()

        assertNull(modelAt(900).segments.single().segmentDeltaMilliseconds)
        assertNull(modelAt(900).previousSegment?.deltaMilliseconds)

        assertNull(modelAt(999).segments.single().segmentDeltaMilliseconds)
        assertEquals("Previous Segment", modelAt(999).previousSegment?.label)
        assertNull(modelAt(999).previousSegment?.deltaMilliseconds)

        val overGold = modelAt(1_001)
        assertEquals(-499, overGold.segments.single().segmentDeltaMilliseconds)
        assertEquals("Live Segment", overGold.previousSegment?.label)
        assertEquals(-499, overGold.previousSegment?.deltaMilliseconds)
        assertEquals(
            RunBoardSemanticColor.AHEAD_GAINING_TIME,
            overGold.previousSegment?.semanticColor,
        )

        val columns = overGold.segments.single().columns
        assertEquals(1_500, columns[0].valueMilliseconds)
        assertEquals(-499, columns[1].valueMilliseconds)
    }

    @Test
    fun missingBestCanStillShowTimeLossAfterFallingBehindComparison() {
        val definition = RunDefinition(
            id = "no-best",
            title = "No Best",
            segments = listOf(
                SegmentDefinition(
                    id = "finish",
                    title = "Finish",
                    personalBestTimeMilliseconds = 1_000,
                ),
            ),
        )

        val running = RunSnapshot(
            definition = definition,
            state = RunState(
                runId = definition.id,
                status = RunStatus.RUNNING,
                startedAtEpochMilliseconds = 0,
            ),
            capturedAtEpochMilliseconds = 1_500,
            elapsedMilliseconds = 1_500,
        ).toRunBoardModel()

        assertEquals(500, running.segments.single().segmentDeltaMilliseconds)
        assertEquals("Live Segment", running.previousSegment?.label)
        assertEquals(500, running.previousSegment?.deltaMilliseconds)

        val completed = RunSnapshot(
            definition = definition,
            state = RunState(
                runId = definition.id,
                status = RunStatus.FINISHED,
                results = listOf(
                    SegmentResult(
                        segmentId = "finish",
                        segmentDurationMilliseconds = 1_500,
                        elapsedAtEndMilliseconds = 1_500,
                        isBestSegment = true,
                        bestSegmentTimeMilliseconds = null,
                        hasBestSegmentTime = true,
                    ),
                ),
            ),
            capturedAtEpochMilliseconds = 1_500,
            elapsedMilliseconds = 1_500,
        ).toRunBoardModel()

        assertEquals(500, completed.segments.single().segmentDeltaMilliseconds)
        assertEquals("Previous Segment", completed.previousSegment?.label)
        assertEquals(500, completed.previousSegment?.deltaMilliseconds)
    }

    @Test
    fun previousSegmentHonorsItsComparisonOverride() {
        val definition = RunDefinition(
            id = "override",
            title = "Override",
            layout = LayoutDefinition(
                previousSegment = LayoutPreviousSegment(
                    enabled = true,
                    comparisonOverride = "Best Segments",
                ),
            ),
            segments = listOf(
                SegmentDefinition(
                    id = "finish",
                    title = "Finish",
                    goldTimeMilliseconds = 1_000,
                    personalBestTimeMilliseconds = 1_500,
                ),
            ),
        )
        val model = RunSnapshot(
            definition = definition,
            state = RunState(
                runId = definition.id,
                status = RunStatus.RUNNING,
                startedAtEpochMilliseconds = 0,
            ),
            capturedAtEpochMilliseconds = 1_001,
            elapsedMilliseconds = 1_001,
        ).toRunBoardModel()

        assertEquals("Live Segment (Best Segments)", model.previousSegment?.label)
        assertEquals(1, model.previousSegment?.deltaMilliseconds)
    }

    @Test
    fun completedSegmentDeltaUsesPersonalBestSegmentTime() {
        val definition = RunDefinition(
            id = "gold",
            title = "Gold",
            segments = listOf(
                SegmentDefinition("intro", "Intro", 1_000, 1_500),
                SegmentDefinition("finish", "Finish", 1_000, 3_000),
            ),
        )
        val snapshot = RunSnapshot(
            definition = definition,
            state = RunState(
                runId = definition.id,
                status = RunStatus.RUNNING,
                activeSegmentIndex = 1,
                results = listOf(
                    SegmentResult(
                        "intro",
                        900,
                        900,
                        isBestSegment = true,
                        bestSegmentTimeMilliseconds = 1_000,
                        hasBestSegmentTime = true,
                    ),
                ),
            ),
            capturedAtEpochMilliseconds = 900,
            elapsedMilliseconds = 900,
        )

        val model = snapshot.toRunBoardModel()

        assertEquals(-600, model.segments.first().segmentDeltaMilliseconds)
        assertEquals(RunBoardSemanticColor.BEST_SEGMENT, model.segments.first().semanticColor)
        assertEquals("Previous Segment", model.previousSegment?.label)
        assertEquals(RunBoardSemanticColor.BEST_SEGMENT, model.previousSegment?.semanticColor)
    }

    @Test
    fun improvedBestKeepsDeltaAgainstBestAtTimeSegmentWasPassed() {
        val snapshot = RunSnapshot(
            definition = RunDefinition(
                id = "gold",
                title = "Gold",
                segments = listOf(SegmentDefinition("finish", "Finish", goldTimeMilliseconds = 900)),
            ),
            state = RunState(
                runId = "gold",
                status = RunStatus.FINISHED,
                results = listOf(
                    SegmentResult(
                        segmentId = "finish",
                        segmentDurationMilliseconds = 900,
                        elapsedAtEndMilliseconds = 900,
                        isBestSegment = true,
                        bestSegmentTimeMilliseconds = 1_000,
                        hasBestSegmentTime = true,
                    ),
                ),
            ),
            capturedAtEpochMilliseconds = 900,
            elapsedMilliseconds = 900,
        )

        val segment = snapshot.toRunBoardModel().segments.single()

        assertEquals(-100, segment.goldDeltaMilliseconds)
        assertNull(segment.segmentDeltaMilliseconds)
    }

    @Test
    fun mainTimerUsesRunningPausedAndFinishedColorsWithoutGold() {
        val definition = RunDefinition(
            id = "colors",
            title = "Colors",
            segments = listOf(SegmentDefinition("finish", "Finish", personalBestTimeMilliseconds = 1_000)),
        )

        fun color(status: RunStatus, elapsed: Long) = RunSnapshot(
            definition = definition,
            state = RunState(runId = definition.id, status = status),
            capturedAtEpochMilliseconds = elapsed,
            elapsedMilliseconds = elapsed,
        ).toRunBoardModel().timerSemanticColor

        assertEquals(RunBoardSemanticColor.AHEAD_GAINING_TIME, color(RunStatus.RUNNING, 500))
        assertEquals(RunBoardSemanticColor.PAUSED, color(RunStatus.PAUSED, 500))
        assertEquals(RunBoardSemanticColor.PERSONAL_BEST, color(RunStatus.FINISHED, 900))
        assertEquals(RunBoardSemanticColor.BEHIND_LOSING_TIME, color(RunStatus.FINISHED, 1_100))
    }
}
