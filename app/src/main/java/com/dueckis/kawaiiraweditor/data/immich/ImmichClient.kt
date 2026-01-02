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

internal data class ImmichAssetInfo(
    val id: String,
    val originalFileName: String,
    val description: String? = null,
    val updatedAt: String? = null
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

internal data class ImmichUploadResult(
    val assetId: String?,
    val errorMessage: String? = null,
    val statusCode: Int? = null,
    val responseBody: String? = null
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

internal suspend fun fetchImmichAssetInfo(
    config: ImmichConfig,
    assetId: String
): ImmichAssetInfo? {
    if (assetId.isBlank()) return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching null
            val url = "$baseUrl/assets/$assetId"
            val connection = openJsonConnection(url, "GET", config)
            val status = connection.responseCode
            if (status !in 200..299) return@runCatching null
            val body = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val json = JSONObject(body)
            val id = json.optString("id").trim().ifBlank { assetId }
            val fileName = json.optString("originalFileName").trim().ifBlank { id }
            val updatedAt = json.optString("updatedAt").ifBlank { null }
            val description =
                json.optString("description").takeIf { it.isNotBlank() }
                    ?: json.optJSONObject("exifInfo")?.optString("description")?.takeIf { it.isNotBlank() }
            ImmichAssetInfo(id = id, originalFileName = fileName, description = description, updatedAt = updatedAt)
        }.getOrNull()
    }
}

internal suspend fun updateImmichAssetDescription(
    config: ImmichConfig,
    assetId: String,
    description: String
): ImmichUploadResult {
    if (assetId.isBlank()) {
        return ImmichUploadResult(assetId = null, errorMessage = "Missing asset id.")
    }
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) {
                return@runCatching ImmichUploadResult(assetId = null, errorMessage = "Immich server URL is missing.")
            }
            val payload = JSONObject().put("description", description)
            val url = "$baseUrl/assets/$assetId"
            val methodsToTry = listOf("PUT", "PATCH")
            var lastStatus: Int? = null
            var lastBody: String? = null
            for (method in methodsToTry) {
                val connection = openJsonConnection(url, method, config)
                connection.doOutput = true
                connection.outputStream.use { stream ->
                    stream.write(payload.toString().toByteArray())
                }
                val status = connection.responseCode
                lastStatus = status
                val body =
                    BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use {
                        it.readText()
                    }
                lastBody = body
                if (status in 200..299) {
                    return@runCatching ImmichUploadResult(assetId = assetId, statusCode = status, responseBody = body)
                }
            }
            val parsed = parseImmichError(lastBody.orEmpty())
            ImmichUploadResult(
                assetId = null,
                errorMessage = parsed ?: "Failed to update description (HTTP ${lastStatus ?: "?"}).",
                statusCode = lastStatus,
                responseBody = lastBody?.takeIf { it.isNotBlank() }
            )
        }.getOrElse { error ->
            ImmichUploadResult(assetId = null, errorMessage = "Failed to update description: ${error.message ?: "Network error"}")
        }
    }
}

internal suspend fun uploadImmichAsset(
    config: ImmichConfig,
    bytes: ByteArray,
    fileName: String,
    mimeType: String
): ImmichUploadResult {
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching ImmichUploadResult(
                assetId = null,
                errorMessage = "Immich server URL is missing.",
                statusCode = null
            )

            val boundary = "----KawaiiRawEditor${System.currentTimeMillis()}"
            val url = "$baseUrl/assets"
            val safeFileName =
                fileName
                    .replace("\"", "_")
                    .replace("\r", "")
                    .replace("\n", "")
                    .trim()
                    .ifBlank { "upload" }
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "POST"
                doOutput = true
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "KawaiiRawEditor")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                applyAuthHeader(this, config)
            }

            val lineBreak = "\r\n"
            val boundaryPrefix = "--$boundary"
            val nowIso = formatIsoUtcNow()

            fun fieldPart(name: String, value: String): ByteArray =
                buildString {
                    append(boundaryPrefix).append(lineBreak)
                    append("Content-Disposition: form-data; name=\"").append(name).append('"')
                    append(lineBreak).append(lineBreak)
                    append(value)
                    append(lineBreak)
                }.toByteArray(Charsets.UTF_8)

            fun fileHeaderPart(name: String, filename: String, type: String): ByteArray =
                buildString {
                    append(boundaryPrefix).append(lineBreak)
                    append("Content-Disposition: form-data; name=\"").append(name).append("\"; filename=\"")
                    append(filename).append('"').append(lineBreak)
                    append("Content-Type: ").append(type).append(lineBreak).append(lineBreak)
                }.toByteArray(Charsets.UTF_8)

            fun fileFooterPart(): ByteArray = lineBreak.toByteArray(Charsets.UTF_8)

            val closing = "$boundaryPrefix--$lineBreak".toByteArray(Charsets.UTF_8)

            val parts =
                listOf(
                    fieldPart("deviceId", "kawaiiraweditor-android"),
                    fieldPart("deviceAssetId", UUID.randomUUID().toString()),
                    fieldPart("fileCreatedAt", nowIso),
                    fieldPart("fileModifiedAt", nowIso),
                    fieldPart("filename", safeFileName),
                    fieldPart("metadata", "[]"),
                    fileHeaderPart("assetData", safeFileName, mimeType)
                )

            val totalLength =
                parts.sumOf { it.size.toLong() } +
                    bytes.size.toLong() +
                    fileFooterPart().size.toLong() +
                    closing.size.toLong()

            // Avoid chunked transfer encoding; some servers/proxies can mishandle it and corrupt uploads.
            connection.setFixedLengthStreamingMode(totalLength)
            connection.setRequestProperty("Content-Length", totalLength.toString())

            connection.outputStream.use { stream ->
                parts.forEach { stream.write(it) }
                stream.write(bytes)
                stream.write(fileFooterPart())
                stream.write(closing)
            }

            val status = connection.responseCode
            val body =
                if (status in 200..299) {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                } else {
                    BufferedReader(InputStreamReader(connection.errorStream ?: connection.inputStream)).use {
                        it.readText()
                    }
                }.trim()

            if (status !in 200..299) {
                val error = parseImmichError(body)
                return@runCatching ImmichUploadResult(
                    assetId = null,
                    errorMessage = error ?: "Immich upload failed (HTTP $status).",
                    statusCode = status,
                    responseBody = body.takeIf { it.isNotBlank() }
                )
            }

            val assetId =
                runCatching {
                    val obj = JSONObject(body)
                    obj.optString("id").ifBlank { obj.optString("assetId") }.trim()
                }.getOrElse {
                    runCatching {
                        val arr = JSONArray(body)
                        val first = arr.optJSONObject(0)
                        first?.optString("id")?.trim().orEmpty()
                    }.getOrDefault("")
                }.ifBlank { null }

            ImmichUploadResult(
                assetId = assetId,
                errorMessage = if (assetId == null) "Immich upload succeeded but returned no asset id." else null,
                statusCode = status,
                responseBody = body.takeIf { it.isNotBlank() }
            )
        }.getOrElse { error ->
            ImmichUploadResult(
                assetId = null,
                errorMessage = "Immich upload failed: ${error.message ?: "Network error"}",
                statusCode = null
            )
        }
    }
}

internal suspend fun addImmichAssetsToAlbum(
    config: ImmichConfig,
    albumId: String,
    assetIds: List<String>
): Boolean {
    if (albumId.isBlank()) return false
    val ids = assetIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    if (ids.isEmpty()) return false
    return withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = config.apiBaseUrl()
            if (baseUrl.isBlank()) return@runCatching false

            val payload = JSONObject().put("ids", JSONArray(ids))
            val url = "$baseUrl/albums/$albumId/assets"
            val connection = openJsonConnection(url, "PUT", config)
            connection.doOutput = true
            connection.outputStream.use { stream ->
                stream.write(payload.toString().toByteArray())
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
