package com.dueckis.kawaiiraweditor.widget

import android.content.Context

class EditedImagesRepository(private val context: Context) {
    fun getLatestRenderedEditedPaths(limit: Int): List<String> {
        // TODO: fetch from Room table like RenderedImage(path, createdAt, edited=true)
        return emptyList()
    }
}
