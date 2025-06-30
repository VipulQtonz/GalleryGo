package com.photogallery.photoEditor.photoEditorHelper

import android.view.MotionEvent
import android.view.View

interface OnPhotoEditorListener {
    fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int)

    fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int)

    fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int)

    fun onStartViewChangeListener(viewType: ViewType)

    fun onStopViewChangeListener(viewType: ViewType)

    fun onTouchSourceImage(event: MotionEvent)
}