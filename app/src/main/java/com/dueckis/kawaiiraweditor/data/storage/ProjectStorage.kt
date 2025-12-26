package com.dueckis.kawaiiraweditor.data.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/**
 * Manages persistent storage of RAW files and their adjustments
 * Similar to RapidRAW's .rrdata files
 */
class ProjectStorage(private val context: Context) {
    private val gson = Gson()
    
    // Directory structure:
    // app_files/
    //   projects/
    //     project-uuid-1/
    //       image.raw (original RAW file)
    //       adjustments.json (adjustment data)
    //     project-uuid-2/
    //       image.raw
    //       adjustments.json
    //   projects.json (list of all projects with metadata)
    
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
        val rating: Int = 0,
        val tags: List<String>? = null,
        val rawMetadata: Map<String, String>? = null
    )
    
    data class ProjectData(
        val metadata: ProjectMetadata,
        val adjustmentsJson: String
    )
    
    /**
     * Import a RAW file and create a new project
     */
    fun importRawFile(fileName: String, rawBytes: ByteArray): String {
        val projectId = UUID.randomUUID().toString()
        val projectDir = File(projectsDir, projectId)
        projectDir.mkdirs()
        
        // Save the RAW file
        val rawFile = File(projectDir, "image.raw")
        rawFile.writeBytes(rawBytes)
        
        // Create initial adjustments file with defaults
        val adjustmentsFile = File(projectDir, "adjustments.json")
        adjustmentsFile.writeText("{}")
        
        // Add to project index
        val metadata = ProjectMetadata(
            id = projectId,
            fileName = fileName,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            tags = emptyList(),
            rawMetadata = emptyMap()
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
    
    /**
     * Load RAW bytes for a project
     */
    fun loadRawBytes(projectId: String): ByteArray? {
        val rawFile = File(projectsDir, "$projectId/image.raw")
        return if (rawFile.exists()) rawFile.readBytes() else null
    }
    
    /**
     * Load adjustments JSON for a project
     */
    fun loadAdjustments(projectId: String): String {
        val adjustmentsFile = File(projectsDir, "$projectId/adjustments.json")
        return if (adjustmentsFile.exists()) {
            adjustmentsFile.readText()
        } else {
            "{}"
        }
    }
    
    /**
     * Save adjustments for a project
     */
    fun saveAdjustments(projectId: String, adjustmentsJson: String) {
        val projectDir = File(projectsDir, projectId)
        if (!projectDir.exists()) return
        
        val adjustmentsFile = File(projectDir, "adjustments.json")
        adjustmentsFile.writeText(adjustmentsJson)
        
        // Update modified time
        updateProjectModifiedTime(projectId)
    }
    
    /**
     * Get all projects
     */
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
    
    /**
     * Load complete project data
     */
    fun loadProject(projectId: String): ProjectData? {
        val projects = getAllProjects()
        val metadata = projects.find { it.id == projectId } ?: return null
        val adjustmentsJson = loadAdjustments(projectId)
        
        return ProjectData(metadata, adjustmentsJson)
    }
    
    /**
     * Delete a project
     */
    fun deleteProject(projectId: String) {
        // Delete project directory
        val projectDir = File(projectsDir, projectId)
        projectDir.deleteRecursively()
        
        // Remove from index
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
    
    /**
     * Save thumbnail for a project
     */
    fun saveThumbnail(projectId: String, thumbnailBytes: ByteArray) {
        val projectDir = File(projectsDir, projectId)
        if (!projectDir.exists()) return
        
        val thumbnailFile = File(projectDir, "thumbnail.jpg")
        thumbnailFile.writeBytes(thumbnailBytes)
        
        updateProjectModifiedTime(projectId)
    }
    
    /**
     * Load thumbnail for a project
     */
    fun loadThumbnail(projectId: String): ByteArray? {
        val thumbnailFile = File(projectsDir, "$projectId/thumbnail.jpg")
        return if (thumbnailFile.exists()) thumbnailFile.readBytes() else null
    }
    
    /**
     * Get storage statistics
     */
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
