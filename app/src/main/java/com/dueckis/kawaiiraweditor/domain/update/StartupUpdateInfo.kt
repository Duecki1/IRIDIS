package com.dueckis.kawaiiraweditor.domain.update

internal data class StartupUpdateInfo(
    val currentVersionName: String,
    val latestVersionName: String,
    val releasePageUrl: String
)
