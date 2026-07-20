package dev.fopwoc.chronosplit.app.session

import dev.fopwoc.chronosplit.domain.RunEngine
import dev.fopwoc.chronosplit.model.AttemptRecord
import dev.fopwoc.chronosplit.model.RunDefinition
import dev.fopwoc.chronosplit.model.RunSnapshot
import dev.fopwoc.chronosplit.model.RunStatus
import dev.fopwoc.chronosplit.model.SegmentDefinition
import dev.fopwoc.chronosplit.model.configurationIdForTitle
import dev.fopwoc.chronosplit.model.withGoldSplits
import dev.fopwoc.chronosplit.model.withPersonalBest
import dev.fopwoc.chronosplit.model.LayoutDefinition
import dev.fopwoc.chronosplit.model.LssRunDocument
import dev.fopwoc.chronosplit.model.exportLs1lLayout
import dev.fopwoc.chronosplit.model.exportLssDocument
import dev.fopwoc.chronosplit.storage.HistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class MobileSession(
    private val repository: HistoryRepository,
    private val now: () -> Long,
    initialDefinition: RunDefinition = defaultDefinition(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine = RunEngine(initialDefinition)
    private var attemptId: String? = null
    private var configurationsHydrated = false
    private var configurationSaveJob: Job? = null
    private val mutableConfigurations = MutableStateFlow<List<RunDefinition>>(emptyList())
    private val mutableSnapshot = MutableStateFlow(engine.snapshot(now()))

    val snapshot: StateFlow<RunSnapshot> = mutableSnapshot.asStateFlow()
    val configurations: StateFlow<List<RunDefinition>> = mutableConfigurations.asStateFlow()
    private val currentAttempts = mutableSnapshot
        .map { snapshot -> snapshot.definition.id }
        .distinctUntilChanged()
        .flatMapLatest(repository::observeAttempts)
        .stateIn(scope, SharingStarted.Eagerly, emptyList())
    val history = currentAttempts.map { attempts -> attempts.map(::toSummary) }
    val allHistory = repository.observeAllAttempts().map { attempts -> attempts.map(::toSummary) }
    val attemptDetails = repository.observeAllAttempts().map { attempts -> attempts.map(::toDetail) }

    init {
        scope.launch {
            repository.observeConfigurations().collect { definitions ->
                if (!configurationsHydrated) {
                    configurationsHydrated = true
                    val latest = definitions.firstOrNull()
                    if (latest != null && latest != engine.definition) {
                        activateConfiguration(latest)
                    }
                }
                mutableConfigurations.value = definitions
            }
        }
    }

    fun primaryAction() {
        if (mutableConfigurations.value.none { it.id == engine.definition.id }) return
        val actionTime = now()
        val previousStatus = engine.state.status
        val previousResultCount = engine.state.results.size
        engine.primaryAction(actionTime)
        if (engine.state.results.size != previousResultCount) updateGoldSplits(actionTime)
        if (previousStatus == RunStatus.READY && engine.state.status == RunStatus.RUNNING) {
            val updatedDefinition = engine.definition.copy(attemptCount = engine.definition.attemptCount + 1)
            engine = RunEngine(updatedDefinition, engine.state)
            updateConfigurationList(updatedDefinition)
            saveConfiguration(updatedDefinition, actionTime)
            attemptId = Random.nextLong().toString(16)
        }
        if (engine.state.status == RunStatus.FINISHED) persistAttempt(actionTime)
        publishSnapshot(actionTime)
    }

    fun togglePause() {
        when (engine.state.status) {
            RunStatus.RUNNING -> engine.pause(now())
            RunStatus.PAUSED -> engine.resume(now())
            else -> Unit
        }
        publishSnapshot(now())
    }

    fun reset() {
        val resetTime = now()
        when (engine.state.status) {
            RunStatus.FINISHED -> updatePersonalBest(resetTime)
            RunStatus.RUNNING,
            RunStatus.PAUSED,
            -> {
                engine.captureActiveSegment(resetTime)
                updateGoldSplits(resetTime)
                persistAttempt(null)
            }
            RunStatus.READY -> Unit
        }
        engine.reset()
        attemptId = null
        publishSnapshot(resetTime)
    }

    fun configure(
        definition: RunDefinition,
        preserveGoldSplits: Boolean = true,
    ) {
        if (engine.state.status != RunStatus.READY && engine.state.status != RunStatus.FINISHED) {
            updateGoldSplits(now())
            persistAttempt(null)
        }
        val configuredDefinition = if (preserveGoldSplits) {
            preserveExistingGoldSplits(definition)
        } else {
            definition
        }
        engine = RunEngine(configuredDefinition)
        attemptId = null
        updateConfigurationList(configuredDefinition)
        saveConfiguration(configuredDefinition)
        publishSnapshot(now())
    }

    fun createConfiguration(definition: RunDefinition): RunDefinition {
        val created = definition.copy(id = uniqueConfigurationId(definition.id))
        configure(created, preserveGoldSplits = false)
        return created
    }

    fun copyConfiguration(id: String): RunDefinition? {
        val source = mutableConfigurations.value.firstOrNull { it.id == id } ?: return null
        val copy = source.copy(
            id = uniqueConfigurationId("${source.id}-copy"),
            title = "${source.title} Copy",
            attemptCount = 0,
        )
        configure(copy, preserveGoldSplits = false)
        return copy
    }

    fun importLayout(layout: LayoutDefinition) {
        val importedDefinition = engine.definition.copy(layout = layout)
        engine = RunEngine(importedDefinition, engine.state)
        if (mutableConfigurations.value.any { it.id == importedDefinition.id }) {
            updateConfigurationList(importedDefinition)
            saveConfiguration(importedDefinition)
        }
        publishSnapshot(now())
    }

    fun importRun(definition: RunDefinition): RunDefinition = createConfiguration(definition)

    fun importRun(document: LssRunDocument): RunDefinition {
        val created = createConfiguration(document.definition)
        val importedAttempts = document.attempts.mapIndexed { index, attempt ->
            attempt.copy(
                id = "${created.id}-import-${index + 1}",
                definition = created,
            )
        }
        scope.launch { repository.saveAttempts(importedAttempts) }
        return created
    }

    fun currentConfiguration(): RunDefinition = engine.definition

    fun configuration(id: String): RunDefinition? =
        mutableConfigurations.value.firstOrNull { it.id == id }

    fun exportCurrentRun(): String = exportLssDocument(engine.definition, currentAttempts.value)

    fun exportCurrentLayout(): String = exportLs1lLayout(engine.definition.layout)

    fun selectConfiguration(id: String): Boolean {
        val selected = mutableConfigurations.value.firstOrNull { it.id == id } ?: return false
        if (selected.id == engine.definition.id && selected == engine.definition) return true

        if (engine.state.status != RunStatus.READY && engine.state.status != RunStatus.FINISHED) {
            updateGoldSplits(now())
            persistAttempt(null)
        }
        activateConfiguration(selected)
        saveConfiguration(selected)
        return true
    }

    fun deleteConfiguration(id: String): Boolean {
        if (mutableConfigurations.value.none { it.id == id }) return false
        val wasActive = engine.definition.id == id
        mutableConfigurations.value = mutableConfigurations.value.filterNot { it.id == id }
        scope.launch {
            repository.deleteConfiguration(id)
            repository.deleteAttempts(id)
        }
        if (wasActive) {
            activateConfiguration(mutableConfigurations.value.firstOrNull() ?: defaultDefinition())
        }
        return true
    }

    fun primaryActionTitle(): String = when (engine.state.status) {
        RunStatus.READY -> "Start"
        RunStatus.RUNNING -> if (engine.state.activeSegmentIndex == engine.definition.segments.lastIndex) "Finish" else "Next Segment"
        RunStatus.PAUSED -> "Paused"
        RunStatus.FINISHED -> "Finished"
    }

    fun pauseActionTitle(): String = if (engine.state.status == RunStatus.PAUSED) "Resume" else "Pause"

    suspend fun loadHistory(): List<AttemptSummary> = repository.loadAttempts().map(::toSummary)

    private fun toSummary(attempt: AttemptRecord) = AttemptSummary(
            id = attempt.id,
            runId = attempt.definition.id,
            runTitle = attempt.definition.title,
            startedAtEpochMilliseconds = attempt.startedAtEpochMilliseconds,
            completed = attempt.completedAtEpochMilliseconds != null,
            completedSegmentCount = attempt.results.size,
            elapsedMilliseconds = attempt.elapsedMilliseconds,
    )

    private fun toDetail(attempt: AttemptRecord): AttemptDetail {
        val resultBySegment = attempt.results.associateBy { it.segmentId }
        return AttemptDetail(
            id = attempt.id,
            runId = attempt.definition.id,
            runTitle = attempt.definition.title,
            gameName = attempt.definition.gameName,
            categoryName = attempt.definition.categoryName,
            startedAtEpochMilliseconds = attempt.startedAtEpochMilliseconds,
            completedAtEpochMilliseconds = attempt.completedAtEpochMilliseconds,
            elapsedMilliseconds = attempt.elapsedMilliseconds,
            segments = attempt.definition.segments.map { segment ->
                val result = resultBySegment[segment.id]
                AttemptSegmentDetail(
                    id = segment.id,
                    title = segment.title,
                    segmentDurationMilliseconds = result?.segmentDurationMilliseconds,
                    elapsedAtEndMilliseconds = result?.elapsedAtEndMilliseconds,
                    isBestSegment = result?.isBestSegment ?: false,
                    bestSegmentDeltaMilliseconds = result?.let { completed ->
                        val bestSegmentTime = if (completed.hasBestSegmentTime) {
                            completed.bestSegmentTimeMilliseconds
                        } else {
                            segment.goldTimeMilliseconds
                        }
                        bestSegmentTime?.let { best ->
                            completed.segmentDurationMilliseconds - best
                        }
                    },
                    comparisonDeltaMilliseconds = result?.elapsedAtEndMilliseconds?.let { elapsed ->
                        segment.personalBestTimeMilliseconds?.let { comparison -> elapsed - comparison }
                    },
                )
            },
        )
    }

    private fun publishSnapshot(timestamp: Long) {
        mutableSnapshot.value = engine.snapshot(timestamp)
    }

    private fun updateGoldSplits(timestamp: Long) {
        val updatedDefinition = engine.definition.withGoldSplits(engine.state.results)
        if (updatedDefinition == engine.definition) return

        engine = RunEngine(updatedDefinition, engine.state)
        updateConfigurationList(updatedDefinition)
        saveConfiguration(updatedDefinition, timestamp)
    }

    private fun updatePersonalBest(timestamp: Long) {
        val updatedDefinition = engine.definition.withPersonalBest(engine.state.results)
        if (updatedDefinition == engine.definition) return

        engine = RunEngine(updatedDefinition, engine.state)
        updateConfigurationList(updatedDefinition)
        saveConfiguration(updatedDefinition, timestamp)
    }

    private fun activateConfiguration(definition: RunDefinition) {
        engine = RunEngine(definition)
        attemptId = null
        publishSnapshot(now())
    }

    private fun updateConfigurationList(definition: RunDefinition) {
        mutableConfigurations.value = listOf(definition) +
            mutableConfigurations.value.filterNot { it.id == definition.id }
    }

    private fun preserveExistingGoldSplits(definition: RunDefinition): RunDefinition {
        val previous = mutableConfigurations.value.firstOrNull { it.id == definition.id } ?: return definition
        return definition.copy(
            attemptCount = maxOf(definition.attemptCount, previous.attemptCount),
            segments = definition.segments.map { segment ->
                val previousSegment = previous.segments.firstOrNull { it.id == segment.id }
                val previousGold = previousSegment?.goldTimeMilliseconds
                val mergedGold = listOfNotNull(segment.goldTimeMilliseconds, previousGold).minOrNull()
                if (mergedGold == segment.goldTimeMilliseconds) segment
                else segment.copy(goldTimeMilliseconds = mergedGold)
            },
        )
    }

    private fun saveConfiguration(
        definition: RunDefinition,
        timestamp: Long = now(),
    ) {
        val previousSave = configurationSaveJob
        configurationSaveJob = scope.launch {
            previousSave?.join()
            repository.saveConfiguration(definition, timestamp)
        }
    }

    private fun uniqueConfigurationId(requestedId: String): String {
        val baseId = requestedId.ifBlank { configurationIdForTitle("Run") }
        val existingIds = mutableConfigurations.value.map { it.id }.toSet()
        if (baseId !in existingIds) return baseId

        var suffix = 2
        while ("$baseId-$suffix" in existingIds) suffix += 1
        return "$baseId-$suffix"
    }

    private fun persistAttempt(completedAtEpochMilliseconds: Long?) {
        val id = attemptId ?: return
        val startedAt = engine.state.startedAtEpochMilliseconds ?: return
        val record = AttemptRecord(
            id = id,
            definition = engine.definition,
            startedAtEpochMilliseconds = startedAt,
            completedAtEpochMilliseconds = completedAtEpochMilliseconds,
            results = engine.state.results,
            elapsedMilliseconds = engine.state.results.lastOrNull()?.elapsedAtEndMilliseconds,
        )
        scope.launch {
            repository.saveAttempt(record)
        }
    }

    private companion object {
        fun defaultDefinition() = RunDefinition(
            id = "new-run",
            title = "New Run",
            segments = listOf(
                SegmentDefinition("segment-1", "Segment 1"),
                SegmentDefinition("segment-2", "Segment 2"),
                SegmentDefinition("segment-3", "Segment 3"),
            ),
        )
    }
}

data class AttemptSummary(
    val id: String,
    val runId: String,
    val runTitle: String,
    val startedAtEpochMilliseconds: Long,
    val completed: Boolean,
    val completedSegmentCount: Int,
    val elapsedMilliseconds: Long?,
)

data class ConfigurationSummary(
    val id: String,
    val title: String,
    val segmentCount: Int,
    val gameName: String? = null,
    val categoryName: String? = null,
    val iconPngBase64: String? = null,
)

data class AttemptDetail(
    val id: String,
    val runId: String,
    val runTitle: String,
    val gameName: String?,
    val categoryName: String?,
    val startedAtEpochMilliseconds: Long,
    val completedAtEpochMilliseconds: Long?,
    val elapsedMilliseconds: Long?,
    val segments: List<AttemptSegmentDetail>,
)

data class AttemptSegmentDetail(
    val id: String,
    val title: String,
    val segmentDurationMilliseconds: Long?,
    val elapsedAtEndMilliseconds: Long?,
    val isBestSegment: Boolean,
    val bestSegmentDeltaMilliseconds: Long?,
    val comparisonDeltaMilliseconds: Long?,
)
