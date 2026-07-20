package dev.fopwoc.chronosplit.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface MobileClientMessage {
    @Serializable
    @SerialName("state")
    data class State(
        val message: StateMessage,
    ) : MobileClientMessage
}

@Serializable
sealed interface MobileServerMessage {
    @Serializable
    @SerialName("stateAccepted")
    data class StateAccepted(
        val revision: Long,
    ) : MobileServerMessage
}

const val MobileSessionHeader = "X-ChronoSplit-Session"
const val MobileAuthHeader = "X-ChronoSplit-Auth"
const val MobileWebSocketPingPeriodSeconds = 15
const val MobileWebSocketTimeoutSeconds = 15
