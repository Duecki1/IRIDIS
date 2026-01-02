package com.dueckis.kawaiiraweditor.data.immich

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal enum class ImmichAuthMode {
    Login,
    ApiKey
}

internal const val IMMICH_OAUTH_APP_REDIRECT_URI = "kawaiiraweditor://oauth/immich"

internal fun buildImmichMobileRedirectUri(serverUrl: String): String {
    val base = ImmichConfig(serverUrl, ImmichAuthMode.Login).apiBaseUrl()
    if (base.isBlank()) return ""
    return "$base/oauth/mobile-redirect"
}

internal data class ImmichConfig(
    val serverUrl: String,
    val authMode: ImmichAuthMode,
    val apiKey: String = "",
    val accessToken: String = ""
) {
    fun apiBaseUrl(): String {
        val trimmed = serverUrl.trim().removeSuffix("/")
        if (trimmed.isBlank()) return ""
        return if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
    }
}

internal data class ImmichAsset(
    val id: String,
    val fileName: String,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val type: String = "IMAGE"
)

internal data class ImmichAlbum(
    val id: String,
    val name: String,
    val assetCount: Int,
    val thumbnailAssetId: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val lastModifiedAssetTimestamp: String? = null
)

internal data class ImmichLoginResult(
    val accessToken: String?,
    val userEmail: String? = null,
    val name: String? = null,
    val errorMessage: String? = null,
    val statusCode: Int? = null
)

internal data class ImmichOAuthStartResult(
    val authorizationUrl: String?,
    val state: String? = null,
    val codeVerifier: String? = null,
    val errorMessage: String? = null,
    val statusCode: Int? = null
)

internal data class ImmichAssetPage(
    val items: List<ImmichAsset>,
    val count: Int,
    val total: Int
)

internal suspend fun searchImmichAssets(
    config: ImmichConfig,
    page: Int,
    size: Int
): ImmichAssetPage? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching null

            val payload = JSONObject()
                .put("type", "IMAGE")
                .put("order", "desc")
                .put("page", page)
                .put("size", size)

            val url = "$baseUrl/search/metadata"
            val connection = openJsonConnection(url, "POST", config)
            connection.doOutput = true
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray())
            }

            val status = connection.responseCode
            if (status !in 200..299) return@runCatching null

            val body =
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val assetsJson = json.optJSONObject("assets") ?: return@runCatching null
            val itemsArray = assetsJson.optJSONArray("items")
            val items =
                buildList {
                    if (itemsArray == null) return@buildList
                    for (i in 0 until itemsArray.length()) {
                        val item = itemsArray.optJSONObject(i) ?: continue
                        val parsed = parseImmichAsset(item) ?: continue
                        add(parsed)
                    }
                }

            val count = assetsJson.optInt("count", items.size)
            val total = assetsJson.optInt("total", items.size)
            ImmichAssetPage(items = items, count = count, total = total)
        }.getOrNull()
    }
}

internal suspend fun fetchImmichAlbums(config: ImmichConfig): List<ImmichAlbum>? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching null

            val url = "$baseUrl/albums"
            val connection = openJsonConnection(url, "GET", config)
            val status = connection.responseCode
            if (status !in 200..299) return@runCatching null

            val body =
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val arr = JSONArray(body)
            buildList<ImmichAlbum> {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id").trim()
                    if (id.isBlank()) continue
                    val name = item.optString("albumName").trim().ifBlank { "Untitled" }
                    val assetCount = item.optInt("assetCount", 0)
                    val thumbId = item.optString("albumThumbnailAssetId").ifBlank { null }
                    val createdAt = item.optString("createdAt").ifBlank { null }
                    val updatedAt = item.optString("updatedAt").ifBlank { null }
                    val lastModified = item.optString("lastModifiedAssetTimestamp").ifBlank { null }
                    add(
                        ImmichAlbum(
                            id = id,
                            name = name,
                            assetCount = assetCount,
                            thumbnailAssetId = thumbId,
                            createdAt = createdAt,
                            updatedAt = updatedAt,
                            lastModifiedAssetTimestamp = lastModified
                        )
                    )
                }
            }
        }.getOrNull()
    }
}

internal suspend fun fetchImmichAlbumAssets(
    config: ImmichConfig,
    albumId: String
): List<ImmichAsset>? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching null

            val url = "$baseUrl/albums/$albumId?withoutAssets=false"
            val connection = openJsonConnection(url, "GET", config)
            val status = connection.responseCode
            if (status !in 200..299) return@runCatching null

            val body =
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val assetsArray = json.optJSONArray("assets")
            buildList<ImmichAsset> {
                if (assetsArray == null) return@buildList
                for (i in 0 until assetsArray.length()) {
                    val item = assetsArray.optJSONObject(i) ?: continue
                    val parsed = parseImmichAsset(item) ?: continue
                    add(parsed)
                }
            }
        }.getOrNull()
    }
}

internal suspend fun downloadImmichThumbnail(
    config: ImmichConfig,
    assetId: String,
    size: String = "thumbnail"
): ByteArray? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching null
            val url = "$baseUrl/assets/$assetId/thumbnail?size=$size"
            val connection = openBinaryConnection(url, config)
            val status = connection.responseCode
            if (status !in 200..299) return@runCatching null
            connection.inputStream.use { it.readBytes() }
        }.getOrNull()
    }
}

internal suspend fun downloadImmichOriginal(
    config: ImmichConfig,
    assetId: String
): ByteArray? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching null
            val url = "$baseUrl/assets/$assetId/original"
            val connection = openBinaryConnection(url, config)
            val status = connection.responseCode
            if (status !in 200..299) return@runCatching null
            connection.inputStream.use { it.readBytes() }
        }.getOrNull()
    }
}

internal suspend fun uploadImmichAsset(
    config: ImmichConfig,
    bytes: ByteArray,
    fileName: String,
    mimeType: String
): Boolean {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching false

            val boundary = "----KawaiiRawEditor${System.currentTimeMillis()}"
            val url = "$baseUrl/assets"
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                applyAuthHeader(this, config)
            }

            val lineBreak = "\r\n"
            val boundaryPrefix = "--$boundary"
            val nowIso = formatIsoUtcNow()

            connection.outputStream.use { stream ->
                fun writeField(name: String, value: String) {
                    stream.write("$boundaryPrefix$lineBreak".toByteArray())
                    stream.write("Content-Disposition: form-data; name=\"$name\"$lineBreak$lineBreak".toByteArray())
                    stream.write(value.toByteArray())
                    stream.write(lineBreak.toByteArray())
                }

                fun writeFile(name: String, filename: String, type: String, data: ByteArray) {
                    stream.write("$boundaryPrefix$lineBreak".toByteArray())
                    stream.write(
                        "Content-Disposition: form-data; name=\"$name\"; filename=\"$filename\"$lineBreak".toByteArray()
                    )
                    stream.write("Content-Type: $type$lineBreak$lineBreak".toByteArray())
                    stream.write(data)
                    stream.write(lineBreak.toByteArray())
                }

                writeField("deviceId", "kawaiiraweditor-android")
                writeField("deviceAssetId", UUID.randomUUID().toString())
                writeField("fileCreatedAt", nowIso)
                writeField("fileModifiedAt", nowIso)
                writeField("filename", fileName)
                writeField("metadata", "[]")
                writeFile("assetData", fileName, mimeType, bytes)
                stream.write("$boundaryPrefix--$lineBreak".toByteArray())
            }

            val status = connection.responseCode
            status in 200..299
        }.getOrDefault(false)
    }
}

internal suspend fun loginImmich(
    serverUrl: String,
    email: String,
    password: String
): ImmichLoginResult? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = ImmichConfig(serverUrl, ImmichAuthMode.Login).apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching null

            val payload = JSONObject()
                .put("email", email)
                .put("password", password)

            val url = "$baseUrl/auth/login"
            val connection = openJsonConnection(url, "POST")
            connection.doOutput = true
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray())
            }

            val status = connection.responseCode
            val body =
                if (status in 200..299) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use {
                        it.readText()
                    }
                }

            if (status !in 200..299) {
                val error = parseImmichError(body)
                return@runCatching ImmichLoginResult(
                    accessToken = null,
                    errorMessage = error ?: "Login failed (HTTP $status).",
                    statusCode = status
                )
            }

            val json = JSONObject(body)
            val token = json.optString("accessToken").trim()
            if (token.isBlank()) {
                return@runCatching ImmichLoginResult(
                    accessToken = null,
                    errorMessage = "Login failed: empty access token.",
                    statusCode = status
                )
            }
            ImmichLoginResult(
                accessToken = token,
                userEmail = json.optString("userEmail").ifBlank { null },
                name = json.optString("name").ifBlank { null },
                statusCode = status
            )
        }.getOrElse { error ->
            ImmichLoginResult(
                accessToken = null,
                errorMessage = "Login failed: ${error.message ?: "Network error"}"
            )
        }
    }
}

internal suspend fun startImmichOAuth(
    serverUrl: String,
    redirectUri: String
): ImmichOAuthStartResult? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = ImmichConfig(serverUrl, ImmichAuthMode.Login).apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching null

            val state = UUID.randomUUID().toString()
            val codeVerifier = generateCodeVerifier()
            val codeChallenge = generateCodeChallenge(codeVerifier)

            val payload = JSONObject()
                .put("redirectUri", redirectUri)
                .put("codeChallenge", codeChallenge)
                .put("state", state)

            val url = "$baseUrl/oauth/authorize"
            val connection = openJsonConnection(url, "POST")
            connection.doOutput = true
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray())
            }

            val status = connection.responseCode
            val body =
                if (status in 200..299) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use {
                        it.readText()
                    }
                }

            if (status !in 200..299) {
                val error = parseImmichError(body)
                return@runCatching ImmichOAuthStartResult(
                    authorizationUrl = null,
                    errorMessage = error ?: "OAuth start failed (HTTP $status).",
                    statusCode = status
                )
            }

            val json = JSONObject(body)
            val authUrl = json.optString("url").ifBlank { json.optString("authorizationUrl") }.trim()
            if (authUrl.isBlank()) {
                return@runCatching ImmichOAuthStartResult(
                    authorizationUrl = null,
                    errorMessage = "OAuth start failed: missing authorization URL.",
                    statusCode = status
                )
            }

            ImmichOAuthStartResult(
                authorizationUrl = authUrl,
                state = state,
                codeVerifier = codeVerifier,
                statusCode = status
            )
        }.getOrElse { error ->
            ImmichOAuthStartResult(
                authorizationUrl = null,
                errorMessage = "OAuth start failed: ${error.message ?: "Network error"}"
            )
        }
    }
}

internal suspend fun completeImmichOAuth(
    serverUrl: String,
    redirectUrl: String,
    state: String,
    codeVerifier: String
): ImmichLoginResult? {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = ImmichConfig(serverUrl, ImmichAuthMode.Login).apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching null

            val payload = JSONObject()
                .put("url", redirectUrl)
                .put("state", state)
                .put("codeVerifier", codeVerifier)

            val url = "$baseUrl/oauth/callback"
            val connection = openJsonConnection(url, "POST")
            connection.doOutput = true
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray())
            }

            val status = connection.responseCode
            val body =
                if (status in 200..299) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use {
                        it.readText()
                    }
                }

            if (status !in 200..299) {
                val error = parseImmichError(body)
                return@runCatching ImmichLoginResult(
                    accessToken = null,
                    errorMessage = error ?: "OAuth login failed (HTTP $status).",
                    statusCode = status
                )
            }

            val json = JSONObject(body)
            val token = json.optString("accessToken").trim()
            if (token.isBlank()) {
                return@runCatching ImmichLoginResult(
                    accessToken = null,
                    errorMessage = "OAuth login failed: empty access token.",
                    statusCode = status
                )
            }
            ImmichLoginResult(
                accessToken = token,
                userEmail = json.optString("userEmail").ifBlank { null },
                name = json.optString("name").ifBlank { null },
                statusCode = status
            )
        }.getOrElse { error ->
            ImmichLoginResult(
                accessToken = null,
                errorMessage = "OAuth login failed: ${error.message ?: "Network error"}"
            )
        }
    }
}

private fun openJsonConnection(
    url: String,
    method: String,
    config: ImmichConfig? = null
): HttpURLConnection {
    return (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 7_500
        readTimeout = 7_500
        requestMethod = method
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Content-Type", "application/json")
        if (config != null) applyAuthHeader(this, config)
    }
}

private fun openBinaryConnection(url: String, config: ImmichConfig): HttpURLConnection {
    return (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 10_000
        readTimeout = 20_000
        requestMethod = "GET"
        setRequestProperty("Accept", "application/octet-stream")
        applyAuthHeader(this, config)
    }
}

private fun parseImmichAsset(item: JSONObject): ImmichAsset? {
    val id = item.optString("id").trim()
    if (id.isBlank()) return null
    val fileName = item.optString("originalFileName").trim().ifBlank { id }
    val createdAt = item.optString("fileCreatedAt").ifBlank { null }
    val updatedAt = item.optString("updatedAt").ifBlank { null }
    val type = item.optString("type").ifBlank { "IMAGE" }
    return ImmichAsset(
        id = id,
        fileName = fileName,
        createdAt = createdAt,
        updatedAt = updatedAt,
        type = type
    )
}

private fun applyAuthHeader(connection: HttpURLConnection, config: ImmichConfig) {
    when (config.authMode) {
        ImmichAuthMode.ApiKey -> {
            if (config.apiKey.isNotBlank()) {
                connection.setRequestProperty("x-api-key", config.apiKey)
            }
        }
        ImmichAuthMode.Login -> {
            if (config.accessToken.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${config.accessToken}")
            }
        }
    }
}

private fun formatIsoUtcNow(): String {
    val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    format.timeZone = TimeZone.getTimeZone("UTC")
    return format.format(Date())
}

private fun parseImmichError(body: String): String? {
    if (body.isBlank()) return null
    return runCatching {
        val json = JSONObject(body)
        val message = json.optString("message").ifBlank { null }
        val error = json.optString("error").ifBlank { null }
        val status = json.optInt("statusCode", 0).takeIf { it > 0 }
        when {
            message != null && error != null && status != null -> "$status $error: $message"
            message != null && error != null -> "$error: $message"
            message != null -> message
            error != null -> error
            else -> body.trim().take(200)
        }
    }.getOrNull()
}

private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

private fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
}
