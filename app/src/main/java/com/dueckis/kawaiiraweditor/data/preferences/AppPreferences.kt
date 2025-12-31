package com.dueckis.kawaiiraweditor.data.preferences

import android.content.Context
import org.json.JSONArray

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
    fun getMaskRenameTags(): List<String> {
        val raw = prefs.getString(KEY_MASK_RENAME_TAGS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i).trim()
                    if (s.isNotEmpty()) add(s)
                }
            }.distinct()
        }.getOrDefault(emptyList())
    }

    fun setMaskRenameTags(tags: List<String>) {
        val normalized = tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        prefs.edit().putString(KEY_MASK_RENAME_TAGS, JSONArray(normalized).toString()).apply()
    }

    /** Call this once at startup (or before showing settings) */
    fun ensureDefaultMaskRenameTagsSeeded() {
        if (!prefs.contains(KEY_MASK_RENAME_TAGS)) {
            setMaskRenameTags(DEFAULT_MASK_RENAME_TAGS)
        }
    }

    private companion object {
        private const val PREFS_NAME = "app_prefs"
        private const val KEY_MASK_RENAME_TAGS = "mask_rename_tags"
        private const val KEY_LOW_QUALITY_PREVIEW_ENABLED = "low_quality_preview_enabled"
        private const val KEY_AUTOMATIC_TAGGING_ENABLED = "automatic_tagging_enabled"
        private const val KEY_ENVIRONMENT_MASKING_ENABLED = "environment_masking_enabled"
        private const val KEY_OPEN_EDITOR_ON_IMPORT_ENABLED = "open_editor_on_import_enabled"
        private val DEFAULT_MASK_RENAME_TAGS = listOf(
            "Subject",
            "Face",
            "Skin",
            "Hair",
            "Eyes",
            "Sky",
            "Background",
            "Foreground",
            "Highlights",
            "Shadows"
        )
    }


}
