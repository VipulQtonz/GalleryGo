package com.photogallery.photoEditor.photoEditing

import android.graphics.Bitmap
import android.graphics.Matrix

object RotateHelper {
    fun applyTransformations(
        inputBitmap: Bitmap,
        rotationCount: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(rotationCount * 90f)
        if (flipHorizontal) {
            matrix.postScale(-1f, 1f, inputBitmap.width / 2f, inputBitmap.height / 2f)
        }
        if (flipVertical) {
            matrix.postScale(1f, -1f, inputBitmap.width / 2f, inputBitmap.height / 2f)
        }
        return Bitmap.createBitmap(
            inputBitmap,
            0,
            0,
            inputBitmap.width,
            inputBitmap.height,
            matrix,
            true
        )
    }
}