package com.dueckis.kawaiiraweditor.data.model

internal fun List<GalleryItem>.updateById(
    projectId: String,
    updater: (GalleryItem) -> GalleryItem
): List<GalleryItem> {
    return map { item -> if (item.projectId == projectId) updater(item) else item }
}

internal fun List<GalleryItem>.updateByIds(
    projectIds: Set<String>,
    updater: (GalleryItem) -> GalleryItem
): List<GalleryItem> {
    return map { item -> if (item.projectId in projectIds) updater(item) else item }
}
