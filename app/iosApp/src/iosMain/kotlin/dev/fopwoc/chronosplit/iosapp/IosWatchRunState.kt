package dev.fopwoc.chronosplit.iosapp

data class IosWatchRunState(
    val configurationId: String,
    val configurationTitle: String,
    val segmentName: String,
    val segmentIndex: Int,
    val segmentCount: Int,
    val status: String,
    val elapsedMilliseconds: Long,
    val capturedAtEpochMilliseconds: Long,
    val deltaMilliseconds: Long,
    val hasDelta: Boolean,
    val primaryActionTitle: String,
    val pauseActionTitle: String,
    val relayConnected: Boolean,
)
