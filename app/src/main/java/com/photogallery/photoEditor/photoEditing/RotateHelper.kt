package com.photogallery.photoEditor.photoEditing

import android.graphics.Bitmap
import android.graphics.Matrix

object RotateHelper {
    fun applyTransformations(
        inputBitmap: Bitmap,
        rotationCount: Int, // Number of 90-degree rotations
        flipHorizontal: Boolean,
        flipVertical: Boolean
    ): Bitmap {
        val matrix = Matrix()
        // Apply rotation (90 degrees per count)
        matrix.postRotate(rotationCount * 90f)
        // Apply flip horizontal
        if (flipHorizontal) {
            matrix.postScale(-1f, 1f, inputBitmap.width / 2f, inputBitmap.height / 2f)
        }
        // Apply flip vertical
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