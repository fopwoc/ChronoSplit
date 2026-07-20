package dev.fopwoc.chronosplit.model

import kotlin.test.Test
import kotlin.test.assertEquals

class GoldSplitTest {
    @Test
    fun fastestResultBecomesTheGoldSplit() {
        val definition = RunDefinition(
            id = "demo",
            title = "Demo",
            segments = listOf(
                SegmentDefinition("intro", "Intro", goldTimeMilliseconds = 1_000),
                SegmentDefinition("finish", "Finish"),
            ),
        )

        val updated = definition.withGoldSplits(
            listOf(
                SegmentResult("intro", 900, 900),
                SegmentResult("finish", 1_400, 2_300),
            ),
        )

        assertEquals(900, updated.segments[0].goldTimeMilliseconds)
        assertEquals(1_400, updated.segments[1].goldTimeMilliseconds)
        assertEquals(2_300, updated.goldTimeMilliseconds())
    }

    @Test
    fun existingGoldIsNeverMadeSlower() {
        val definition = RunDefinition(
            id = "demo",
            title = "Demo",
            segments = listOf(SegmentDefinition("finish", "Finish", goldTimeMilliseconds = 1_000)),
        )

        val updated = definition.withGoldSplits(listOf(SegmentResult("finish", 1_100, 1_100)))

        assertEquals(1_000, updated.segments.single().goldTimeMilliseconds)
    }

    @Test
    fun everyPassedSegmentCanEstablishOrImproveItsOwnBest() {
        val definition = RunDefinition(
            id = "demo",
            title = "Demo",
            segments = listOf(
                SegmentDefinition("one", "One"),
                SegmentDefinition("two", "Two"),
            ),
        )

        val firstAttempt = definition.withGoldSplits(
            listOf(SegmentResult("one", 1_200, 1_200)),
        )
        val secondAttempt = firstAttempt.withGoldSplits(
            listOf(
                SegmentResult("one", 900, 900),
                SegmentResult("two", 1_500, 2_400),
            ),
        )

        assertEquals(900, secondAttempt.segments[0].goldTimeMilliseconds)
        assertEquals(1_500, secondAttempt.segments[1].goldTimeMilliseconds)
    }

    @Test
    fun partialAttemptUpdatesOnlyPassedSegmentsAndSumOfBest() {
        val definition = RunDefinition(
            id = "demo",
            title = "Demo",
            segments = listOf(
                SegmentDefinition("one", "One", goldTimeMilliseconds = 1_000),
                SegmentDefinition("two", "Two", goldTimeMilliseconds = 2_000),
                SegmentDefinition("three", "Three", goldTimeMilliseconds = 3_000),
            ),
        )

        val updated = definition.withGoldSplits(
            listOf(
                SegmentResult("one", 900, 900),
                SegmentResult("two", 2_100, 3_000),
            ),
        )

        assertEquals(900, updated.segments[0].goldTimeMilliseconds)
        assertEquals(2_000, updated.segments[1].goldTimeMilliseconds)
        assertEquals(3_000, updated.segments[2].goldTimeMilliseconds)
        assertEquals(5_900, updated.goldTimeMilliseconds())
        assertEquals(6_000, definition.goldTimeMilliseconds())
    }

    @Test
    fun firstCompletedAttemptBecomesPersonalBestComparison() {
        val definition = RunDefinition(
            id = "demo",
            title = "Demo",
            segments = listOf(
                SegmentDefinition("one", "One"),
                SegmentDefinition("two", "Two"),
                SegmentDefinition("three", "Three"),
            ),
        )

        val updated = definition.withPersonalBest(
            listOf(
                SegmentResult("one", 3_000, 3_000),
                SegmentResult("two", 3_000, 6_000),
                SegmentResult("three", 2_000, 8_000),
            ),
        )

        assertEquals(listOf(3_000L, 6_000L, 8_000L), updated.segments.map { it.personalBestTimeMilliseconds })
    }

    @Test
    fun personalBestRequiresACompleteFasterAttempt() {
        val definition = RunDefinition(
            id = "demo",
            title = "Demo",
            segments = listOf(
                SegmentDefinition("one", "One", personalBestTimeMilliseconds = 3_000),
                SegmentDefinition("two", "Two", personalBestTimeMilliseconds = 6_000),
            ),
        )

        val partial = definition.withPersonalBest(listOf(SegmentResult("one", 2_000, 2_000)))
        val slower = definition.withPersonalBest(
            listOf(
                SegmentResult("one", 2_000, 2_000),
                SegmentResult("two", 5_000, 7_000),
            ),
        )

        assertEquals(definition, partial)
        assertEquals(definition, slower)
    }
}
