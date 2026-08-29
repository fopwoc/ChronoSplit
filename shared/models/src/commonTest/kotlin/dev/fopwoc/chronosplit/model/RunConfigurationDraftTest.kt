package dev.fopwoc.chronosplit.model

import kotlin.test.Test
import kotlin.test.assertFailsWith

class RunConfigurationDraftTest {
    @Test
    fun rejectsDuplicateSegmentIdentifiers() {
        val draft = RunConfigurationDraft(
            title = "Run",
            segments = listOf(
                SegmentConfigurationDraft(id = "same", name = "First"),
                SegmentConfigurationDraft(id = "same", name = "Second"),
            ),
        )

        assertFailsWith<IllegalArgumentException> { draft.toRunDefinition() }
    }

    @Test
    fun rejectsDecreasingSplitTimes() {
        val draft = RunConfigurationDraft(
            title = "Run",
            segments = listOf(
                SegmentConfigurationDraft(id = "first", name = "First", splitTimeMilliseconds = 2_000),
                SegmentConfigurationDraft(id = "second", name = "Second", splitTimeMilliseconds = 1_000),
            ),
        )

        assertFailsWith<IllegalArgumentException> { draft.toRunDefinition() }
    }
}
