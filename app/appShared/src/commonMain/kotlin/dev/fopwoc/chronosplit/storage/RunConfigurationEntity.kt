package dev.fopwoc.chronosplit.storage

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = RunConfigurationEntity.TABLE_NAME)
data class RunConfigurationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val definitionJson: String,
    val updatedAtEpochMilliseconds: Long,
) {
    companion object {
        const val TABLE_NAME = "run_configurations"
    }
}
