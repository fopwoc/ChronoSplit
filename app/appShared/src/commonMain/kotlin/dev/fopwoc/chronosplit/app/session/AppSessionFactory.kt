package dev.fopwoc.chronosplit.app.session

import dev.fopwoc.chronosplit.storage.HistoryRepository
import dev.fopwoc.chronosplit.storage.createChronoDatabase

fun createMobileSession(
    databasePath: String,
    now: () -> Long,
): MobileSession = MobileSession(
    repository = HistoryRepository(createChronoDatabase(databasePath)),
    now = now,
)
