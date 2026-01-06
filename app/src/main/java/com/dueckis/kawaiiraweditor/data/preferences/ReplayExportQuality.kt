package com.dueckis.kawaiiraweditor.data.preferences

internal enum class ReplayExportQuality(
    val preferenceValue: String,
    val title: String,
    val description: String,
    val maxDimension: Int
) {
    Faster(
        preferenceValue = "faster",
        title = "Faster",
        description = "Quick export at 1080p",
        maxDimension = 1080
    ),
    Balanced(
        preferenceValue = "balanced",
        title = "Balanced",
        description = "Balanced quality at 1440p",
        maxDimension = 1440
    ),
    HighFidelity(
        preferenceValue = "high_fidelity",
        title = "High Fidelity",
        description = "Maximum detail at 2160p",
        maxDimension = 2160
    );

    companion object {
        fun fromPreference(value: String?): ReplayExportQuality {
            if (value.isNullOrBlank()) return Balanced
            return entries.firstOrNull { it.preferenceValue == value } ?: Balanced
        }
    }
}
