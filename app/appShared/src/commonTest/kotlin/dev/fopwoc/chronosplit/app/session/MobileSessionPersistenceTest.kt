package dev.fopwoc.chronosplit.app.session

import dev.fopwoc.chronosplit.model.AttemptRecord
import dev.fopwoc.chronosplit.model.RunDefinition
import dev.fopwoc.chronosplit.model.SegmentDefinition
import dev.fopwoc.chronosplit.storage.HistoryStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class MobileSessionPersistenceTest {
    private val run = RunDefinition(
        id = "test-run",
        title = "Test Run",
        segments = listOf(
            SegmentDefinition("first", "First"),
            SegmentDefinition("second", "Second"),
        ),
    )

    @Test
    fun finishPersistsAttemptAndPersonalBestBeforeReportingComplete() = runTest {
        val store = FakeHistoryStore(listOf(run))
        var timestamp = 1_000L
        val session = MobileSession(store, { timestamp }, run, backgroundScope)
        runCurrent()

        session.primaryAction()
        timestamp = 1_800L
        session.primaryAction()
        timestamp = 3_000L
        session.primaryAction()

        assertEquals(null, session.awaitPendingPersistence())
        val attempt = store.attempts.value.single()
        val savedDefinition = store.configurations.value.single()
        assertEquals(3_000L, attempt.completedAtEpochMilliseconds)
        assertEquals(2_000L, attempt.elapsedMilliseconds)
        assertEquals(800L, savedDefinition.segments[0].personalBestTimeMilliseconds)
        assertEquals(2_000L, savedDefinition.segments[1].personalBestTimeMilliseconds)
        assertEquals(savedDefinition, attempt.definition)
    }

    @Test
    fun persistenceFailureIsObservableToTheIosCaller() = runTest {
        val store = FakeHistoryStore(listOf(run), failAttemptWrites = true)
        var timestamp = 1_000L
        val session = MobileSession(store, { timestamp }, run, backgroundScope)
        runCurrent()

        session.primaryAction()
        timestamp = 1_800L
        session.primaryAction()
        timestamp = 3_000L
        session.primaryAction()

        assertEquals("attempt write failed", session.awaitPendingPersistence())
        assertEquals("attempt write failed", session.persistenceError.value)
        session.clearPersistenceError()
        assertEquals(null, session.persistenceError.value)
    }

    @Test
    fun editingInactiveConfigurationDoesNotActivateItAfterRelaunch() = runTest {
        val inactive = run.copy(id = "inactive", title = "Inactive")
        val store = FakeHistoryStore(listOf(run, inactive))
        val session = MobileSession(store, { 1_000L }, run, backgroundScope)
        runCurrent()

        assertEquals(true, session.updateConfiguration(inactive.copy(title = "Edited"), preserveGoldSplits = false))
        assertEquals(null, session.awaitPendingPersistence())
        assertEquals(run.id, session.currentConfiguration().id)

        val relaunched = MobileSession(store, { 2_000L }, inactive, backgroundScope)
        runCurrent()
        assertEquals(run.id, relaunched.currentConfiguration().id)
    }
}

private class FakeHistoryStore(
    initialConfigurations: List<RunDefinition>,
    private val failAttemptWrites: Boolean = false,
) : HistoryStore {
    val configurations = MutableStateFlow(initialConfigurations)
    val attempts = MutableStateFlow<List<AttemptRecord>>(emptyList())

    override fun observeConfigurations(): Flow<List<RunDefinition>> = configurations

    override suspend fun loadLatestConfiguration(): RunDefinition? = configurations.value.firstOrNull()

    override suspend fun saveConfiguration(definition: RunDefinition, nowEpochMilliseconds: Long) {
        configurations.value = listOf(definition) + configurations.value.filterNot { it.id == definition.id }
    }

    override suspend fun deleteConfiguration(id: String) {
        configurations.value = configurations.value.filterNot { it.id == id }
    }

    override fun observeAttempts(runId: String): Flow<List<AttemptRecord>> =
        attempts.map { records -> records.filter { it.definition.id == runId } }

    override fun observeAllAttempts(): Flow<List<AttemptRecord>> = attempts

    override suspend fun loadAttempts(): List<AttemptRecord> = attempts.value

    override suspend fun loadAttempts(runId: String): List<AttemptRecord> =
        attempts.value.filter { it.definition.id == runId }

    override suspend fun saveAttempt(record: AttemptRecord) {
        check(!failAttemptWrites) { "attempt write failed" }
        attempts.value = listOf(record) + attempts.value.filterNot { it.id == record.id }
    }

    override suspend fun saveAttempts(records: List<AttemptRecord>) {
        records.forEach { saveAttempt(it) }
    }

    override suspend fun deleteAttempts(runId: String) {
        attempts.value = attempts.value.filterNot { it.definition.id == runId }
    }
}
