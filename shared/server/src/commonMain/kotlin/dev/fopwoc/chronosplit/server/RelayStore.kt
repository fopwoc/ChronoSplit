package dev.fopwoc.chronosplit.server

import dev.fopwoc.chronosplit.model.StateMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow

class RelayStore {
    val current = MutableStateFlow<StateMessage?>(null)

    private val mobileSessionMutex = Mutex()
    private var activeMobileSessionId: String? = null

    suspend fun tryClaimMobileSession(sessionId: String): Boolean = mobileSessionMutex.withLock {
        if (activeMobileSessionId != null) return@withLock false
        activeMobileSessionId = sessionId
        true
    }

    suspend fun releaseMobileSession(sessionId: String) {
        mobileSessionMutex.withLock {
            if (activeMobileSessionId == sessionId) activeMobileSessionId = null
        }
    }

    fun publish(message: StateMessage) {
        current.value = message
    }
}
