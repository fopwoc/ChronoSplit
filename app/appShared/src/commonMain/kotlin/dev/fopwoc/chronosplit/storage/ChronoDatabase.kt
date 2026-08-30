package dev.fopwoc.chronosplit.storage

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

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
