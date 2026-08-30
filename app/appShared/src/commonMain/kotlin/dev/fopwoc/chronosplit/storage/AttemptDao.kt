package dev.fopwoc.chronosplit.storage

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AttemptDao {
    @Query("SELECT * FROM attempts ORDER BY startedAtEpochMilliseconds DESC")
    fun observeAll(): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts WHERE runId = :runId ORDER BY startedAtEpochMilliseconds DESC")
    fun observeForRun(runId: String): Flow<List<AttemptEntity>>

    @Upsert
    suspend fun upsert(attempt: AttemptEntity)

    @Query("DELETE FROM attempts WHERE runId = :runId")
    suspend fun deleteForRun(runId: String)
}
