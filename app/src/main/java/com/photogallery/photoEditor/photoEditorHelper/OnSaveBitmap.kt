package com.photogallery.photoEditor.photoEditorHelper

import android.graphics.Bitmap

interface OnSaveBitmap {
    fun onBitmapReady(saveBitmap: Bitmap)
}