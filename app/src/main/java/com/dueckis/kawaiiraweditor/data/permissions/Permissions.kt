package com.dueckis.kawaiiraweditor.data.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal fun maybeRequestPostNotificationsPermission(context: Context, requestPermission: (String) -> Unit) {
    if (Build.VERSION.SDK_INT < 33) return
    val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    if (!granted) {
        requestPermission(Manifest.permission.POST_NOTIFICATIONS)
    }
}
