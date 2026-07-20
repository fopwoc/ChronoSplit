package dev.fopwoc.chronosplit.storage

import dev.fopwoc.chronosplit.model.AttemptRecord
import dev.fopwoc.chronosplit.model.RunDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class HistoryRepository(
    database: ChronoDatabase,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val configurations = database.runConfigurationDao()
    private val attempts = database.attemptDao()

    fun observeConfigurations(): Flow<List<RunDefinition>> =
        configurations.observeAll().map { rows ->
            rows.map { json.decodeFromString<RunDefinition>(it.definitionJson) }
        }

    suspend fun loadLatestConfiguration(): RunDefinition? =
        configurations.observeAll().first().firstOrNull()?.let { row ->
            json.decodeFromString<RunDefinition>(row.definitionJson)
        }

    suspend fun saveConfiguration(definition: RunDefinition, nowEpochMilliseconds: Long) {
        configurations.upsert(
            RunConfigurationEntity(
                id = definition.id,
                title = definition.title,
                definitionJson = json.encodeToString(definition),
                updatedAtEpochMilliseconds = nowEpochMilliseconds,
            ),
        )
    }

    suspend fun deleteConfiguration(id: String) {
        configurations.delete(id)
    }

    fun observeAttempts(runId: String): Flow<List<AttemptRecord>> =
        attempts.observeForRun(runId).map { rows ->
            rows.map { json.decodeFromString<AttemptRecord>(it.recordJson) }
        }

    fun observeAllAttempts(): Flow<List<AttemptRecord>> =
        attempts.observeAll().map { rows ->
            rows.map { json.decodeFromString<AttemptRecord>(it.recordJson) }
        }

    suspend fun loadAttempts(): List<AttemptRecord> = attempts.observeAll().first().map { row ->
        json.decodeFromString<AttemptRecord>(row.recordJson)
    }

    suspend fun loadAttempts(runId: String): List<AttemptRecord> =
        attempts.observeForRun(runId).first().map { row ->
            json.decodeFromString<AttemptRecord>(row.recordJson)
        }

    suspend fun saveAttempt(record: AttemptRecord) {
        attempts.upsert(
            AttemptEntity(
                id = record.id,
                runId = record.definition.id,
                startedAtEpochMilliseconds = record.startedAtEpochMilliseconds,
                completedAtEpochMilliseconds = record.completedAtEpochMilliseconds,
                recordJson = json.encodeToString(record),
            ),
        )
    }

    suspend fun saveAttempts(records: List<AttemptRecord>) {
        records.forEach { saveAttempt(it) }
    }

    suspend fun deleteAttempts(runId: String) {
        attempts.deleteForRun(runId)
    }
}
