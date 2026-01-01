package com.dueckis.kawaiiraweditor.ios.picker

import com.dueckis.kawaiiraweditor.ios.util.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.UIKit.*
import platform.darwin.NSObject

data class PickedFile(val name: String, val bytes: ByteArray)

class IosRawPicker(
    private val presenter: UIViewController,
    private val onPicked: (List<PickedFile>) -> Unit
) {
    // Keep delegate strongly referenced, or iOS will drop callbacks :3
    private val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol, UINavigationControllerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
            val files = urls.mapNotNull { url ->
                val secured = url.startAccessingSecurityScopedResource()
                try {
                    val data = NSData.dataWithContentsOfURL(url) ?: return@mapNotNull null
                    val name = url.lastPathComponent ?: "image.raw"
                    PickedFile(name = name, bytes = data.toByteArray())
                } finally {
                    if (secured) url.stopAccessingSecurityScopedResource()
                }
            }
            onPicked(files)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            onPicked(emptyList())
        }
    }

    fun present() {
        // Old-but-solid API: accept any “public.item” from Files/iCloud/etc.
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.item"),
            inMode = UIDocumentPickerModeImport
        )
        picker.allowsMultipleSelection = true
        picker.delegate = delegate
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}
