package dev.fopwoc.chronosplit.model

import kotlinx.serialization.Serializable

@Serializable
data class SegmentDefinition(
    val id: String,
    val title: String,
    val goldTimeMilliseconds: Long? = null,
    val personalBestTimeMilliseconds: Long? = null,
    val iconPngBase64: String? = null,
) {
    init {
        require(goldTimeMilliseconds == null || goldTimeMilliseconds >= 0) {
            "Gold split time must not be negative"
        }
        require(personalBestTimeMilliseconds == null || personalBestTimeMilliseconds >= 0) {
            "Personal best split time must not be negative"
        }
    }
}
