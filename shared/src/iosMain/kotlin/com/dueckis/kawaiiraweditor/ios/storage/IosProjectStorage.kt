package com.dueckis.kawaiiraweditor.ios.storage

import com.dueckis.kawaiiraweditor.ios.model.IosProjectMetadata
import com.dueckis.kawaiiraweditor.ios.util.toNSData
import com.dueckis.kawaiiraweditor.ios.util.toByteArray
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import platform.Foundation.*
import platform.UIKit.UIDevice
import kotlin.random.Random

class IosProjectStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val fm = NSFileManager.defaultManager

    private fun documentsUrl(): NSURL {
        return fm.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null
        )!!
    }

    private fun projectsBaseUrl(): NSURL {
        val base = documentsUrl().URLByAppendingPathComponent("projects")!!
        fm.createDirectoryAtURL(base, withIntermediateDirectories = true, attributes = null, error = null)
        return base
    }

    private fun indexUrl(): NSURL {
        return documentsUrl().URLByAppendingPathComponent("projects.json")!!
    }

    fun getAllProjects(): List<IosProjectMetadata> {
        val data = NSData.dataWithContentsOfURL(indexUrl()) ?: return emptyList()
        val str = (NSString.create(data, NSUTF8StringEncoding) as? String) ?: return emptyList()
        return runCatching { json.decodeFromString<List<IosProjectMetadata>>(str) }.getOrDefault(emptyList())
    }

    private fun saveIndex(list: List<IosProjectMetadata>) {
        val str = json.encodeToString(list)
        val data = (str as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        data.writeToURL(indexUrl(), atomically = true)
    }

    fun importRawFile(fileName: String, rawBytes: ByteArray): String {
        val now = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
        val projectId = "${now}_${Random.nextInt(100000, 999999)}_${UIDevice.currentDevice.name}"

        val base = projectsBaseUrl()
        val dir = base.URLByAppendingPathComponent(projectId)!!
        fm.createDirectoryAtURL(dir, withIntermediateDirectories = true, attributes = null, error = null)

        // image.raw
        val rawUrl = dir.URLByAppendingPathComponent("image.raw")!!
        rawBytes.toNSData().writeToURL(rawUrl, atomically = true)

        // adjustments.json (placeholder)
        val adjUrl = dir.URLByAppendingPathComponent("adjustments.json")!!
        val adjData = ("{}" as NSString).dataUsingEncoding(NSUTF8StringEncoding)!!
        adjData.writeToURL(adjUrl, atomically = true)

        val meta = IosProjectMetadata(
            id = projectId,
            fileName = fileName,
            createdAt = now,
            modifiedAt = now,
            rating = 0,
            tags = emptyList(),
            rawMetadata = emptyMap()
        )

        val updated = getAllProjects().toMutableList().apply { add(meta) }
        saveIndex(updated)
        return projectId
    }

    fun saveThumbnail(projectId: String, thumbnailBytes: ByteArray) {
        val thumbUrl = projectsBaseUrl()
            .URLByAppendingPathComponent(projectId)!!
            .URLByAppendingPathComponent("thumbnail.jpg")!!
        thumbnailBytes.toNSData().writeToURL(thumbUrl, atomically = true)
    }

    fun loadThumbnail(projectId: String): ByteArray? {
        val thumbUrl = projectsBaseUrl()
            .URLByAppendingPathComponent(projectId)!!
            .URLByAppendingPathComponent("thumbnail.jpg")!!
        return NSData.dataWithContentsOfURL(thumbUrl)?.toByteArray()
    }

    fun deleteProject(projectId: String) {
        val dir = projectsBaseUrl().URLByAppendingPathComponent(projectId)!!
        fm.removeItemAtURL(dir, error = null)

        val remaining = getAllProjects().filterNot { it.id == projectId }
        saveIndex(remaining)
    }
}
