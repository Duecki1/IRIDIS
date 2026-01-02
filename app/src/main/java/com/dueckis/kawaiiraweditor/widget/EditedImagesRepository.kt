package com.dueckis.kawaiiraweditor.widget

import android.content.Context
import com.dueckis.kawaiiraweditor.data.storage.ProjectStorage
import java.io.File
import java.util.Comparator

class EditedImagesRepository(private val context: Context) {
    fun getLatestRenderedEditedPaths(limit: Int): List<String> {
        val storage = ProjectStorage(context)
        val projects = storage.getAllProjects()

        val candidates = ArrayList<Pair<Long, String>>()
        val baseDir = File(context.filesDir, "projects")

        for (p in projects) {
            val thumb = File(baseDir, "${p.id}/thumbnail.jpg")
            if (thumb.exists()) {
                candidates.add(Pair(p.modifiedAt, thumb.absolutePath))
            }
        }

        return candidates.sortedWith(Comparator { a, b -> b.first.compareTo(a.first) })
            .map { it.second }
            .take(limit)
    }
}
