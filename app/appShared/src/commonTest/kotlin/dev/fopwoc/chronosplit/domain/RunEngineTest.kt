package dev.fopwoc.chronosplit.domain

import dev.fopwoc.chronosplit.model.RunDefinition
import dev.fopwoc.chronosplit.model.RunStatus
import dev.fopwoc.chronosplit.model.SegmentDefinition
import kotlin.test.Test
import kotlin.test.assertEquals

class RunEngineTest {
    private val run = RunDefinition(
        id = "any-percent",
        title = "Any Percent",
        segments = listOf(
            SegmentDefinition("intro", "Intro", 1_000),
            SegmentDefinition("finish", "Finish", 2_500),
        ),
    )

    @Test
    fun primaryActionStartsAdvancesAndFinishes() {
        val engine = RunEngine(run)

        engine.primaryAction(10_000)
        assertEquals(RunStatus.RUNNING, engine.state.status)

        engine.primaryAction(11_200)
        assertEquals(1, engine.state.activeSegmentIndex)
        assertEquals(1_200, engine.state.results.single().segmentDurationMilliseconds)
        assertEquals(false, engine.state.results.single().isBestSegment)
        assertEquals(1_000, engine.state.results.single().bestSegmentTimeMilliseconds)
        assertEquals(true, engine.state.results.single().hasBestSegmentTime)

        engine.primaryAction(12_900)
        assertEquals(RunStatus.FINISHED, engine.state.status)
        assertEquals(2_900, engine.snapshot(99_000).elapsedMilliseconds)
        assertEquals(1_700, engine.state.results.last().segmentDurationMilliseconds)
        assertEquals(true, engine.state.results.last().isBestSegment)
    }

    @Test
    fun pausedTimeIsNotIncluded() {
        val engine = RunEngine(run)
        engine.start(1_000)
        engine.pause(1_500)
        engine.resume(2_500)

        assertEquals(1_000, engine.snapshot(3_000).elapsedMilliseconds)
    }

    @Test
    fun captureActiveSegmentRecordsTimeWithoutAdvancingRun() {
        val engine = RunEngine(run)
        engine.start(10_000)

        engine.captureActiveSegment(10_800)

        assertEquals(RunStatus.RUNNING, engine.state.status)
        assertEquals(0, engine.state.activeSegmentIndex)
        assertEquals(800, engine.state.results.single().segmentDurationMilliseconds)
        assertEquals(800, engine.state.results.single().elapsedAtEndMilliseconds)
        assertEquals(true, engine.state.results.single().isBestSegment)
        assertEquals(1_000, engine.state.results.single().bestSegmentTimeMilliseconds)
    }

    @Test
    fun captureActiveSegmentUsesPausedElapsedTime() {
        val engine = RunEngine(run)
        engine.start(10_000)
        engine.pause(10_700)

        engine.captureActiveSegment(15_000)

        assertEquals(700, engine.state.results.single().segmentDurationMilliseconds)
        assertEquals(700, engine.state.results.single().elapsedAtEndMilliseconds)
    }
}
