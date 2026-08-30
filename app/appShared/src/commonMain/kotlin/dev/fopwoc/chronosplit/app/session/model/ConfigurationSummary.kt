package dev.fopwoc.chronosplit.app.session.model

data class ConfigurationSummary(
    val id: String,
    val title: String,
    val segmentCount: Int,
    val gameName: String? = null,
    val categoryName: String? = null,
    val iconPngBase64: String? = null,
)
