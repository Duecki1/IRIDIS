package com.dueckis.kawaiiraweditor.ios.picker

import com.dueckis.kawaiiraweditor.ios.util.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.UIKit.*
import platform.darwin.NSObject

data class PickedFile(val name: String, val bytes: ByteArray)

class IosRawPicker(
    private val onPicked: (List<PickedFile>) -> Unit
) {
    private val delegate = object : NSObject(), UIDocumentPickerDelegateProtocol, UINavigationControllerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
            val files = urls.mapNotNull { url ->
                // "startAccessing..." is required for picking files outside the sandbox
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
        val picker = UIDocumentPickerViewController(
            documentTypes = listOf("public.item"),
            // FIX: Added 'UIDocumentPickerMode.' prefix
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
        )
        picker.allowsMultipleSelection = true
        picker.delegate = delegate

        // FIX: Find the root controller dynamically instead of passing it in
        val window = UIApplication.sharedApplication.keyWindow
        val rootController = window?.rootViewController

        rootController?.presentViewController(picker, animated = true, completion = null)
    }
}