package dev.fopwoc.chronosplit.android.model

data class EditableSegment(
    val id: String,
    val name: String,
    val iconPngBase64: String? = null,
    val splitTime: String = "",
    val bestSegment: String = "",
)
