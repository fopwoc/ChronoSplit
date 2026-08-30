package dev.fopwoc.chronosplit.storage

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface RunConfigurationDao {
    @Query("SELECT * FROM run_configurations ORDER BY updatedAtEpochMilliseconds DESC, title")
    fun observeAll(): Flow<List<RunConfigurationEntity>>

    @Query("SELECT * FROM run_configurations WHERE id = :id")
    suspend fun find(id: String): RunConfigurationEntity?

    @Upsert
    suspend fun upsert(configuration: RunConfigurationEntity)

    @Query("DELETE FROM run_configurations WHERE id = :id")
    suspend fun delete(id: String)
}
