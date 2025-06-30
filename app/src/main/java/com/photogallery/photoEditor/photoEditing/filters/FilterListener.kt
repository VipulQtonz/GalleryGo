package com.photogallery.photoEditor.photoEditing.filters

import com.photogallery.photoEditor.photoEditorHelper.PhotoFilter

interface FilterListener {
    fun onFilterSelected(photoFilter: PhotoFilter)
}