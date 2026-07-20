package dev.fopwoc.chronosplit.storage

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Upsert
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "run_configurations")
data class RunConfigurationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val definitionJson: String,
    val updatedAtEpochMilliseconds: Long,
)

@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val startedAtEpochMilliseconds: Long,
    val completedAtEpochMilliseconds: Long?,
    val recordJson: String,
)

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

@Database(
    entities = [RunConfigurationEntity::class, AttemptEntity::class],
    version = 1,
)
@ConstructedBy(ChronoDatabaseConstructor::class)
abstract class ChronoDatabase : RoomDatabase() {
    abstract fun runConfigurationDao(): RunConfigurationDao
    abstract fun attemptDao(): AttemptDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object ChronoDatabaseConstructor : RoomDatabaseConstructor<ChronoDatabase> {
    override fun initialize(): ChronoDatabase
}

fun createChronoDatabase(path: String): ChronoDatabase =
    Room.databaseBuilder<ChronoDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.Default)
        .build()
