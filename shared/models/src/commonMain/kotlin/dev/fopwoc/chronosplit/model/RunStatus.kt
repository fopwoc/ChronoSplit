package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable

@Serializable
enum class RunStatus { READY, RUNNING, PAUSED, FINISHED }
