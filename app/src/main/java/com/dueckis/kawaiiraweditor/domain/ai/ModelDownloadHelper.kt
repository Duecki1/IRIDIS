package com.dueckis.kawaiiraweditor.domain.ai

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.dueckis.kawaiiraweditor.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.math.roundToInt

internal data class ModelInfo(val displayName: String, val filename: String)

internal fun missingModels(context: Context, models: List<ModelInfo>): List<ModelInfo> {
    val dir = File(context.applicationContext.filesDir, "models")
    return models.filter { model -> !File(dir, model.filename).exists() }
}

internal object ModelDownloadHelper {
    private const val CHANNEL_ID = "model_downloads"

    suspend fun downloadFileWithProgress(
        context: Context,
        url: String,
        finalDest: File,
        expectedSha256: String?,
        notificationTitle: String,
        toastStart: String,
        toastDone: String?,
        toastFailure: String?,
        notificationId: Int
    ) {
        val appContext = context.applicationContext
        val tmp = File(finalDest.parentFile, "${finalDest.name}.part")
        tmp.delete()
        finalDest.parentFile?.mkdirs()

        var lastUiUpdate = 0L
        var lastProgress = -1

        try {
            showToast(appContext, toastStart)
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
                requestMethod = "GET"
            }
            connection.connect()

            val total = connection.contentLengthLong
            val indeterminate = total <= 0L
            val digest = expectedSha256?.let { MessageDigest.getInstance("SHA-256") }

            connection.inputStream.use { input ->
                FileOutputStream(tmp).use { output ->
                    val buffer = ByteArray(1024 * 256)
                    var readTotal = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        digest?.update(buffer, 0, read)

                        readTotal += read.toLong()
                        val now = SystemClock.elapsedRealtime()
                        val progress =
                            if (indeterminate) -1 else ((readTotal * 100f) / total.toFloat()).roundToInt().coerceIn(0, 100)
                        val shouldUpdate =
                            indeterminate ||
                                progress != lastProgress ||
                                now - lastUiUpdate >= 350L
                        if (shouldUpdate) {
                            lastUiUpdate = now
                            lastProgress = progress
                            notifyDownloadProgress(
                                context = appContext,
                                notificationId = notificationId,
                                title = notificationTitle,
                                bytesRead = readTotal,
                                totalBytes = total,
                                indeterminate = indeterminate
                            )
                        }
                    }
                }
            }

            if (expectedSha256 != null) {
                val actual = digest?.digest()?.joinToString("") { "%02x".format(it) }
                check(actual != null && actual.equals(expectedSha256, ignoreCase = true)) {
                    "Downloaded file hash mismatch"
                }
            }

            if (finalDest.exists()) finalDest.delete()
            check(tmp.renameTo(finalDest)) { "Failed to move downloaded file into place" }
            if (toastDone != null) showToast(appContext, toastDone)
        } catch (e: Exception) {
            if (toastFailure != null) showToast(appContext, toastFailure)
            throw e
        } finally {
            tmp.delete()
            cancelDownloadNotification(appContext, notificationId)
        }
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun notificationsAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return true
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        return granted
    }

    private fun ensureDownloadChannel(context: Context): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return CHANNEL_ID
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return CHANNEL_ID
        val existing = nm.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return CHANNEL_ID
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Model downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Download progress for AI models"
            }
        )
        return CHANNEL_ID
    }

    private fun notifyDownloadProgress(
        context: Context,
        notificationId: Int,
        title: String,
        bytesRead: Long,
        totalBytes: Long,
        indeterminate: Boolean
    ) {
        if (!notificationsAllowed(context)) return
        val channelId = ensureDownloadChannel(context)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (indeterminate || totalBytes <= 0L) {
            builder.setProgress(0, 0, true)
            builder.setContentText("Downloading...")
        } else {
            val progress = ((bytesRead * 100f) / totalBytes.toFloat()).roundToInt().coerceIn(0, 100)
            builder.setProgress(100, progress, false)
            builder.setContentText("$progress%")
        }

        nm.notify(notificationId, builder.build())
    }

    private fun cancelDownloadNotification(context: Context, notificationId: Int) {
        if (!notificationsAllowed(context)) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.cancel(notificationId)
    }
}
