package com.dueckis.kawaiiraweditor

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

internal data class StartupUpdateInfo(
    val currentVersionName: String,
    val latestVersionName: String,
    val releasePageUrl: String
)

internal fun getInstalledVersionName(context: Context): String {
    return runCatching {
        val pm = context.packageManager
        val pkg = context.packageName
        @Suppress("DEPRECATION")
        val info =
            if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            else pm.getPackageInfo(pkg, 0)
        info.versionName ?: ""
    }.getOrDefault("")
}

internal suspend fun fetchStartupUpdateInfo(
    currentVersionName: String,
    githubOwner: String,
    githubRepo: String
): StartupUpdateInfo? {
    return withContext(Dispatchers.IO) {
        runCatching {
            if (currentVersionName.isBlank()) return@runCatching null

            val apiUrl = "https://api.github.com/repos/$githubOwner/$githubRepo/tags?per_page=1"
            val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 7_500
                readTimeout = 7_500
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "$githubOwner-$githubRepo-android")
            }

            val statusCode = connection.responseCode
            if (statusCode !in 200..299) return@runCatching null

            val body =
                BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
            val tags = JSONArray(body)
            val latestTagRaw = tags.optJSONObject(0)?.optString("name").orEmpty()
            val latestTag = latestTagRaw.trim()
            val latestVersionName = latestTag.removePrefix("v").removePrefix("V")
            if (latestVersionName.isBlank()) return@runCatching null

            val releasePageUrl =
                "https://github.com/$githubOwner/$githubRepo/releases/tag/${Uri.encode(latestTag)}"

            val shouldUpdate = isNewerVersion(latestVersionName, currentVersionName)
            if (!shouldUpdate) return@runCatching null

            StartupUpdateInfo(
                currentVersionName = currentVersionName,
                latestVersionName = latestVersionName,
                releasePageUrl = releasePageUrl
            )
        }.getOrNull()
    }
}

internal fun isNewerVersion(latest: String, current: String): Boolean {
    fun parse(v: String): List<Int>? {
        val cleaned = v.trim().removePrefix("v").removePrefix("V")
        val match = Regex("""\d+(?:\.\d+)*""").find(cleaned) ?: return null
        return match.value.split('.').mapNotNull { it.toIntOrNull() }
    }

    val a = parse(latest)
    val b = parse(current)
    if (a == null || b == null) return false

    val max = maxOf(a.size, b.size)
    for (i in 0 until max) {
        val ai = a.getOrElse(i) { 0 }
        val bi = b.getOrElse(i) { 0 }
        if (ai != bi) return ai > bi
    }
    return false
}

