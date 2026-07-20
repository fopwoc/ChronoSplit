package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable

@Serializable
data class StateMessage(
    val protocolVersion: Int = 1,
    val snapshot: RunSnapshot,
)
