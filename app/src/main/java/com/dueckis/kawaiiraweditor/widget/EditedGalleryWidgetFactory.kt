package com.dueckis.kawaiiraweditor.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dueckis.kawaiiraweditor.R
import java.io.File

class EditedGalleryWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private var imagePaths: List<String> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Load your most recent edited renders (example: newest 20)
        imagePaths = EditedImagesRepository(context).getLatestRenderedEditedPaths(limit = 20)
            .filter { File(it).exists() }
    }

    override fun onDestroy() {
        imagePaths = emptyList()
    }

    override fun getCount(): Int = imagePaths.size

    override fun getViewAt(position: Int): RemoteViews {
        val rv = RemoteViews(context.packageName, R.layout.widget_item_image)
        return try {
            val path = imagePaths[position]

            val thumb = loadThumb(path, 320, 320) // smaller to avoid binder limits
            if (thumb != null) rv.setImageViewBitmap(R.id.itemImage, thumb)
            else rv.setImageViewResource(R.id.itemImage, android.R.drawable.ic_menu_report_image)

            // Optional: pass something for click handling (if your GalleryActivity reads it)
            val fillIn = android.content.Intent().apply { putExtra("image_path", path) }
            rv.setOnClickFillInIntent(R.id.itemImage, fillIn)

            rv
        } catch (t: Throwable) {
            rv.setImageViewResource(R.id.itemImage, android.R.drawable.ic_menu_report_image)
            rv
        }
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = imagePaths[position].hashCode().toLong()
    override fun hasStableIds(): Boolean = true

    private fun loadThumb(path: String, reqW: Int, reqH: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)

        opts.inSampleSize = calculateInSampleSize(opts, reqW, reqH)
        opts.inJustDecodeBounds = false
        opts.inPreferredConfig = Bitmap.Config.RGB_565

        val decoded = BitmapFactory.decodeFile(path, opts) ?: return null
        return Bitmap.createScaledBitmap(decoded, reqW, reqH, true)
    }

    private fun calculateInSampleSize(opts: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        val (h, w) = opts.outHeight to opts.outWidth
        var sample = 1
        if (h > reqH || w > reqW) {
            var halfH = h / 2
            var halfW = w / 2
            while ((halfH / sample) >= reqH && (halfW / sample) >= reqW) sample *= 2
        }
        return sample
    }
}
