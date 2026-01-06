package com.dueckis.kawaiiraweditor.widget

import android.content.Intent
import android.widget.RemoteViewsService

class EditedGalleryWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory {
        return EditedGalleryWidgetFactory(applicationContext)
    }
}
