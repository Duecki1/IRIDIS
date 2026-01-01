package com.dueckis.kawaiiraweditor.ios.picker

import com.dueckis.kawaiiraweditor.ios.util.toByteArray
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.create
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
                val secured = url.startAccessingSecurityScopedResource()
                try {
                    // FIX: Use the constructor 'NSData(contentsOfURL = url)'
                    // instead of 'NSData.dataWithContentsOfURL(url)'
                    val data = NSData(contentsOfURL = url) ?: return@mapNotNull null

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
            // Ensure this uses the full enum path
            inMode = UIDocumentPickerMode.UIDocumentPickerModeImport
        )
        picker.allowsMultipleSelection = true
        picker.delegate = delegate

        // Find the root view controller dynamically to avoid passing context
        val window = UIApplication.sharedApplication.keyWindow
        val rootController = window?.rootViewController

        rootController?.presentViewController(picker, animated = true, completion = null)
    }
}