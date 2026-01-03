package com.dueckis.kawaiiraweditor.data.preferences

import android.content.Context
import com.dueckis.kawaiiraweditor.data.immich.ImmichAuthMode
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

    fun getImmichServerUrl(): String = prefs.getString(KEY_IMMICH_SERVER_URL, "").orEmpty()

    fun setImmichServerUrl(url: String) {
        prefs.edit().putString(KEY_IMMICH_SERVER_URL, url.trim()).apply()
    }

    fun getImmichAuthMode(): ImmichAuthMode {
        val raw = prefs.getString(KEY_IMMICH_AUTH_MODE, null)
        if (raw != null) {
            return if (raw == "api_key") ImmichAuthMode.ApiKey else ImmichAuthMode.Login
        }
        val apiKey = getImmichApiKey()
        return if (apiKey.isNotBlank()) ImmichAuthMode.ApiKey else ImmichAuthMode.Login
    }

    fun setImmichAuthMode(mode: ImmichAuthMode) {
        val raw = if (mode == ImmichAuthMode.ApiKey) "api_key" else "login"
        prefs.edit().putString(KEY_IMMICH_AUTH_MODE, raw).apply()
    }

    fun getImmichLoginEmail(): String = prefs.getString(KEY_IMMICH_LOGIN_EMAIL, "").orEmpty()

    fun setImmichLoginEmail(email: String) {
        prefs.edit().putString(KEY_IMMICH_LOGIN_EMAIL, email.trim()).apply()
    }

    fun getImmichAccessToken(): String = prefs.getString(KEY_IMMICH_ACCESS_TOKEN, "").orEmpty()

    fun setImmichAccessToken(token: String) {
        prefs.edit().putString(KEY_IMMICH_ACCESS_TOKEN, token.trim()).apply()
    }

    fun getImmichApiKey(): String = prefs.getString(KEY_IMMICH_API_KEY, "").orEmpty()

    fun setImmichApiKey(key: String) {
        prefs.edit().putString(KEY_IMMICH_API_KEY, key.trim()).apply()
    }

    fun getImmichOAuthState(): String = prefs.getString(KEY_IMMICH_OAUTH_STATE, "").orEmpty()

    fun getImmichOAuthVerifier(): String = prefs.getString(KEY_IMMICH_OAUTH_VERIFIER, "").orEmpty()

    fun setImmichOAuthPending(state: String, verifier: String) {
        prefs.edit()
            .putString(KEY_IMMICH_OAUTH_STATE, state)
            .putString(KEY_IMMICH_OAUTH_VERIFIER, verifier)
            .apply()
    }

    fun clearImmichOAuthPending() {
        prefs.edit()
            .remove(KEY_IMMICH_OAUTH_STATE)
            .remove(KEY_IMMICH_OAUTH_VERIFIER)
            .apply()
    }

    enum class ImmichWorkMode {
        Local,
        Immich
    }

    fun getImmichWorkMode(): ImmichWorkMode {
        val raw = prefs.getString(KEY_IMMICH_WORK_MODE, null)?.trim().orEmpty()
        if (raw.equals("immich", ignoreCase = true)) return ImmichWorkMode.Immich
        if (raw.equals("local", ignoreCase = true)) return ImmichWorkMode.Local

        val legacy = prefs.getBoolean(KEY_IMMICH_AUTO_UPLOAD_EDITS_ENABLED_LEGACY, false)
        return if (legacy) ImmichWorkMode.Immich else ImmichWorkMode.Local
    }

    fun setImmichWorkMode(mode: ImmichWorkMode) {
        val raw = if (mode == ImmichWorkMode.Immich) "immich" else "local"
        prefs.edit().putString(KEY_IMMICH_WORK_MODE, raw).apply()
    }

    fun getImmichLocalExportRelativePath(): String =
        prefs.getString(KEY_IMMICH_LOCAL_EXPORT_RELATIVE_PATH, DEFAULT_IMMICH_LOCAL_EXPORT_RELATIVE_PATH).orEmpty()

    fun setImmichLocalExportRelativePath(relativePath: String) {
        prefs.edit().putString(KEY_IMMICH_LOCAL_EXPORT_RELATIVE_PATH, relativePath.trim()).apply()
    }

    fun isImmichDescriptionSyncEnabled(): Boolean =
        prefs.getBoolean(KEY_IMMICH_DESCRIPTION_SYNC_ENABLED, false)

    fun setImmichDescriptionSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IMMICH_DESCRIPTION_SYNC_ENABLED, enabled).apply()
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

    fun getGallerySortField(): String = prefs.getString(KEY_GALLERY_SORT_FIELD, "date").orEmpty()

    fun setGallerySortField(field: String) {
        prefs.edit().putString(KEY_GALLERY_SORT_FIELD, field.trim().lowercase()).apply()
    }

    fun getGallerySortOrder(): String = prefs.getString(KEY_GALLERY_SORT_ORDER, "desc").orEmpty()

    fun setGallerySortOrder(order: String) {
        prefs.edit().putString(KEY_GALLERY_SORT_ORDER, order.trim().lowercase()).apply()
    }

    fun getGalleryGridColumns(): Int = prefs.getInt(KEY_GALLERY_GRID_COLUMNS, 0)

    fun setGalleryGridColumns(columns: Int) {
        prefs.edit().putInt(KEY_GALLERY_GRID_COLUMNS, columns).apply()
    }

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
        private const val KEY_IMMICH_SERVER_URL = "immich_server_url"
        private const val KEY_IMMICH_API_KEY = "immich_api_key"
        private const val KEY_IMMICH_AUTH_MODE = "immich_auth_mode"
        private const val KEY_IMMICH_LOGIN_EMAIL = "immich_login_email"
        private const val KEY_IMMICH_ACCESS_TOKEN = "immich_access_token"
        private const val KEY_IMMICH_OAUTH_STATE = "immich_oauth_state"
        private const val KEY_IMMICH_OAUTH_VERIFIER = "immich_oauth_verifier"
        private const val KEY_IMMICH_WORK_MODE = "immich_work_mode"
        private const val KEY_IMMICH_AUTO_UPLOAD_EDITS_ENABLED_LEGACY = "immich_auto_upload_edits_enabled"
        private const val KEY_IMMICH_LOCAL_EXPORT_RELATIVE_PATH = "immich_local_export_relative_path"
        private const val KEY_IMMICH_DESCRIPTION_SYNC_ENABLED = "immich_description_sync_enabled"
        private const val KEY_GALLERY_SORT_FIELD = "gallery_sort_field"
        private const val KEY_GALLERY_SORT_ORDER = "gallery_sort_order"
        private const val KEY_GALLERY_GRID_COLUMNS = "gallery_grid_columns"
        private const val DEFAULT_IMMICH_LOCAL_EXPORT_RELATIVE_PATH = "Pictures/IRIDIS/Immich"
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
