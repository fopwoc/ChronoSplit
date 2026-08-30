package dev.fopwoc.chronosplit.storage

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = AttemptEntity.TABLE_NAME)
data class AttemptEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val startedAtEpochMilliseconds: Long,
    val completedAtEpochMilliseconds: Long?,
    val recordJson: String,
) {
    companion object {
        const val TABLE_NAME = "attempts"
    }
}
