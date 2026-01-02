package com.dueckis.kawaiiraweditor.data.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID
class ProjectStorage(private val context: Context) {
    private val gson = Gson()

    private val projectsDir = File(context.filesDir, "projects")
    private val projectsIndexFile = File(context.filesDir, "projects.json")

    init {
        projectsDir.mkdirs()
    }

    data class ProjectMetadata(
        val id: String,
        val fileName: String,
        val createdAt: Long,
        val modifiedAt: Long,
        val editsUpdatedAtMs: Long? = null,
        val rating: Int = 0,
        val tags: List<String>? = null,
        val rawMetadata: Map<String, String>? = null,
        val immichAssetId: String? = null,
        val immichAlbumId: String? = null,
        val immichSidecarAssetId: String? = null,
        val immichSidecarUpdatedAtMs: Long? = null
    )

    data class ProjectData(
        val metadata: ProjectMetadata,
        val adjustmentsJson: String
    )

    fun importRawFile(
        fileName: String,
        rawBytes: ByteArray,
        immichAssetId: String? = null,
        immichAlbumId: String? = null,
    ): String {
        val projectId = UUID.randomUUID().toString()
        val projectDir = File(projectsDir, projectId)
        projectDir.mkdirs()

        val rawFile = File(projectDir, "image.raw")
        rawFile.writeBytes(rawBytes)

        val adjustmentsFile = File(projectDir, "adjustments.json")
        adjustmentsFile.writeText("{}")

        val metadata = ProjectMetadata(
            id = projectId,
            fileName = fileName,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            tags = emptyList(),
            rawMetadata = emptyMap(),
            immichAssetId = immichAssetId,
            immichAlbumId = immichAlbumId
        )
        addToProjectIndex(metadata)

        return projectId
    }

    fun setTags(projectId: String, tags: List<String>) {
        val normalized = tags.map { it.trim() }.filter { it.isNotBlank() }.distinct().take(25)
        val projects = getAllProjects().toMutableList()
        val index = projects.indexOfFirst { it.id == projectId }
        if (index >= 0) {
            projects[index] =
                projects[index].copy(tags = normalized, modifiedAt = System.currentTimeMillis())
            saveProjectIndex(projects)
        }
    }

    fun loadRawBytes(projectId: String): ByteArray? {
        val rawFile = File(projectsDir, "$projectId/image.raw")
        return if (rawFile.exists()) rawFile.readBytes() else null
    }

    fun loadAdjustments(projectId: String): String {
        val adjustmentsFile = File(projectsDir, "$projectId/adjustments.json")
        return if (adjustmentsFile.exists()) {
            adjustmentsFile.readText()
        } else {
            "{}"
        }
    }
    
    fun saveAdjustments(projectId: String, adjustmentsJson: String, updatedAtMs: Long = System.currentTimeMillis()) {
        val projectDir = File(projectsDir, projectId)
        if (!projectDir.exists()) return
        
        val adjustmentsFile = File(projectDir, "adjustments.json")
        val existing = runCatching { if (adjustmentsFile.exists()) adjustmentsFile.readText() else null }.getOrNull()
        if (existing == adjustmentsJson) return
        adjustmentsFile.writeText(adjustmentsJson)
        
        updateProjectEditsTime(projectId, updatedAtMs)
    }

    fun getEditsUpdatedAtMs(projectId: String): Long {
        val meta = getAllProjects().firstOrNull { it.id == projectId } ?: return 0L
        return meta.editsUpdatedAtMs ?: meta.modifiedAt
    }

    fun getImmichSidecarUpdatedAtMs(projectId: String): Long {
        val meta = getAllProjects().firstOrNull { it.id == projectId } ?: return 0L
        return meta.immichSidecarUpdatedAtMs ?: 0L
    }

    fun setImmichSidecarInfo(projectId: String, assetId: String?, updatedAtMs: Long?) {
        val projects = getAllProjects().toMutableList()
        val index = projects.indexOfFirst { it.id == projectId }
        if (index < 0) return
        val normalizedAssetId = assetId?.trim().takeUnless { it.isNullOrBlank() }
        val normalizedUpdatedAt = updatedAtMs?.takeIf { it > 0L }
        val now = System.currentTimeMillis()
        projects[index] =
            projects[index].copy(
                modifiedAt = now,
                immichSidecarAssetId = normalizedAssetId,
                immichSidecarUpdatedAtMs = normalizedUpdatedAt
            )
        saveProjectIndex(projects)
    }
    
    fun getAllProjects(): List<ProjectMetadata> {
        if (!projectsIndexFile.exists()) return emptyList()
        
        return try {
            val json = projectsIndexFile.readText()
            val type = object : TypeToken<List<ProjectMetadata>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun findProjectByImmichAssetId(assetId: String): ProjectMetadata? {
        if (assetId.isBlank()) return null
        return getAllProjects().firstOrNull { it.immichAssetId == assetId }
    }
    
    fun loadProject(projectId: String): ProjectData? {
        val projects = getAllProjects()
        val metadata = projects.find { it.id == projectId } ?: return null
        val adjustmentsJson = loadAdjustments(projectId)
        
        return ProjectData(metadata, adjustmentsJson)
    }
    
    fun deleteProject(projectId: String) {
        val projectDir = File(projectsDir, projectId)
        projectDir.deleteRecursively()

        val projects = getAllProjects().filter { it.id != projectId }
        saveProjectIndex(projects)
    }
    
    private fun addToProjectIndex(metadata: ProjectMetadata) {
        val projects = getAllProjects().toMutableList()
        projects.add(metadata)
        saveProjectIndex(projects)
    }
    
    private fun updateProjectModifiedTime(projectId: String) {
        val projects = getAllProjects().toMutableList()
        val index = projects.indexOfFirst { it.id == projectId }
        if (index >= 0) {
            projects[index] = projects[index].copy(modifiedAt = System.currentTimeMillis())
            saveProjectIndex(projects)
        }
    }

    private fun updateProjectEditsTime(projectId: String, editsUpdatedAtMs: Long) {
        val projects = getAllProjects().toMutableList()
        val index = projects.indexOfFirst { it.id == projectId }
        if (index < 0) return
        val now = System.currentTimeMillis()
        projects[index] =
            projects[index].copy(
                modifiedAt = maxOf(now, editsUpdatedAtMs),
                editsUpdatedAtMs = editsUpdatedAtMs
            )
        saveProjectIndex(projects)
    }

    fun setRawMetadata(projectId: String, rawMetadata: Map<String, String>) {
        val normalized =
            rawMetadata
                .mapValues { it.value.trim() }
                .filterValues { it.isNotBlank() }
                .toSortedMap()
        val projects = getAllProjects().toMutableList()
        val index = projects.indexOfFirst { it.id == projectId }
        if (index >= 0) {
            projects[index] =
                projects[index].copy(rawMetadata = normalized, modifiedAt = System.currentTimeMillis())
            saveProjectIndex(projects)
        }
    }

    fun setRating(projectId: String, rating: Int) {
        val clamped = rating.coerceIn(0, 5)
        val projects = getAllProjects().toMutableList()
        val index = projects.indexOfFirst { it.id == projectId }
        if (index >= 0) {
            projects[index] = projects[index].copy(rating = clamped, modifiedAt = System.currentTimeMillis())
            saveProjectIndex(projects)
        }
    }
    
    private fun saveProjectIndex(projects: List<ProjectMetadata>) {
        val json = gson.toJson(projects)
        projectsIndexFile.writeText(json)
    }
    
    fun saveThumbnail(projectId: String, thumbnailBytes: ByteArray) {
        val projectDir = File(projectsDir, projectId)
        if (!projectDir.exists()) return
        
        val thumbnailFile = File(projectDir, "thumbnail.jpg")
        thumbnailFile.writeBytes(thumbnailBytes)
        
        updateProjectModifiedTime(projectId)
    }
    
    fun loadThumbnail(projectId: String): ByteArray? {
        val thumbnailFile = File(projectsDir, "$projectId/thumbnail.jpg")
        return if (thumbnailFile.exists()) thumbnailFile.readBytes() else null
    }
    
    fun getStorageInfo(): StorageInfo {
        val projectCount = getAllProjects().size
        var totalSize = 0L
        
        projectsDir.listFiles()?.forEach { projectDir ->
            projectDir.listFiles()?.forEach { file ->
                totalSize += file.length()
            }
        }
        
        return StorageInfo(projectCount, totalSize)
    }
    
    data class StorageInfo(
        val projectCount: Int,
        val totalSizeBytes: Long
    )
}
