package dev.fopwoc.chronosplit.storage

import dev.fopwoc.chronosplit.model.AttemptRecord
import dev.fopwoc.chronosplit.model.RunDefinition
import kotlinx.coroutines.flow.Flow

interface HistoryStore {
    fun observeConfigurations(): Flow<List<RunDefinition>>
    suspend fun loadLatestConfiguration(): RunDefinition?
    suspend fun saveConfiguration(definition: RunDefinition, nowEpochMilliseconds: Long)
    suspend fun deleteConfiguration(id: String)
    fun observeAttempts(runId: String): Flow<List<AttemptRecord>>
    fun observeAllAttempts(): Flow<List<AttemptRecord>>
    suspend fun loadAttempts(): List<AttemptRecord>
    suspend fun loadAttempts(runId: String): List<AttemptRecord>
    suspend fun saveAttempt(record: AttemptRecord)
    suspend fun saveAttempts(records: List<AttemptRecord>)
    suspend fun deleteAttempts(runId: String)
}
