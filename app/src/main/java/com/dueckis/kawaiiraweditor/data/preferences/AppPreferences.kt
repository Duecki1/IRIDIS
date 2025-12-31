package com.dueckis.kawaiiraweditor.data.preferences

import android.content.Context

internal class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isLowQualityPreviewEnabled(): Boolean = prefs.getBoolean(KEY_LOW_QUALITY_PREVIEW_ENABLED, true)

    fun setLowQualityPreviewEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LOW_QUALITY_PREVIEW_ENABLED, enabled).apply()
    }

    fun isAutomaticTaggingEnabled(): Boolean = prefs.getBoolean(KEY_AUTOMATIC_TAGGING_ENABLED, false)

    fun setAutomaticTaggingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOMATIC_TAGGING_ENABLED, enabled).apply()
    }

    fun isEnvironmentMaskingEnabled(): Boolean = prefs.getBoolean(KEY_ENVIRONMENT_MASKING_ENABLED, false)

    fun setEnvironmentMaskingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENVIRONMENT_MASKING_ENABLED, enabled).apply()
    }

    fun isOpenEditorOnImportEnabled(): Boolean =
        prefs.getBoolean(KEY_OPEN_EDITOR_ON_IMPORT_ENABLED, false)

    fun setOpenEditorOnImportEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OPEN_EDITOR_ON_IMPORT_ENABLED, enabled).apply()
    }
    private companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_LOW_QUALITY_PREVIEW_ENABLED = "low_quality_preview_enabled"
        private const val KEY_AUTOMATIC_TAGGING_ENABLED = "automatic_tagging_enabled"
        private const val KEY_ENVIRONMENT_MASKING_ENABLED = "environment_masking_enabled"
        private const val KEY_OPEN_EDITOR_ON_IMPORT_ENABLED = "open_editor_on_import_enabled"
    }
}
