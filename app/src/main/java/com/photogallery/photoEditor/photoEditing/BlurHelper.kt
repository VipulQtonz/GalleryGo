package com.photogallery.photoEditor.photoEditing

import android.content.Context
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur

object BlurHelper {
    fun applyBlur(context: Context, inputBitmap: Bitmap, blurRadius: Int): Bitmap {
        val outputBitmap = Bitmap.createBitmap(inputBitmap)
        val renderScript = RenderScript.create(context)
        val input = Allocation.createFromBitmap(renderScript, inputBitmap)
        val output = Allocation.createFromBitmap(renderScript, outputBitmap)
        val script = ScriptIntrinsicBlur.create(renderScript, Element.U8_4(renderScript))
        val safeRadius = if (blurRadius == 0) 0.1f else blurRadius.toFloat().coerceIn(0.1f, 25f)
        try {
            script.setRadius(safeRadius)
            script.setInput(input)
            script.forEach(output)
            output.copyTo(outputBitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            return inputBitmap // Return original bitmap on error
        } finally {
            renderScript.destroy()
        }

        return outputBitmap
    }
}