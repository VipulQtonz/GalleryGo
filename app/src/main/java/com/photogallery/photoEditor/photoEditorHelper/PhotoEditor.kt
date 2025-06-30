package com.photogallery.photoEditor.photoEditorHelper

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.view.View
import android.widget.ImageView
import androidx.annotation.IntRange
import androidx.annotation.RequiresPermission
import androidx.annotation.UiThread
import com.photogallery.photoEditor.photoEditorHelper.shape.ShapeBuilder

interface PhotoEditor {
    fun addImage(desiredImage: Bitmap)

    @SuppressLint("ClickableViewAccessibility")
    fun addText(text: String, colorCodeTextView: Int)

    @SuppressLint("ClickableViewAccessibility")
    fun addText(textTypeface: Typeface?, text: String, colorCodeTextView: Int)

    @SuppressLint("ClickableViewAccessibility")
    fun addText(text: String, styleBuilder: TextStyleBuilder?)

    fun editText(view: View, inputText: String, colorCode: Int)

    fun editText(view: View, textTypeface: Typeface?, inputText: String, colorCode: Int)

    fun editText(view: View, inputText: String, styleBuilder: TextStyleBuilder?)

    fun addEmoji(emojiName: String)

    fun addEmoji(emojiTypeface: Typeface?, emojiName: String)

    fun setBrushDrawingMode(brushDrawingMode: Boolean)

    val brushDrawableMode: Boolean?

    @Deprecated(
        """use {@code setShape} of a ShapeBuilder
     
      """
    )
    fun setOpacity(@IntRange(from = 0, to = 100) opacity: Int)

    fun setBrushEraserSize(brushEraserSize: Float)

    val eraserSize: Float
    @set:Deprecated(
        """use {@code setShape} of a ShapeBuilder
     
      """
    )
    var brushSize: Float
    @set:Deprecated(
        """use {@code setShape} of a ShapeBuilder
     
      """
    )
    var brushColor: Int

    fun brushEraser()

    fun undo(): Boolean

    val isUndoAvailable: Boolean

    fun redo(): Boolean

    val isRedoAvailable: Boolean

    fun clearAllViews()

    @UiThread
    fun clearHelperBox()

    fun setFilterEffect(customEffect: CustomEffect?)

    fun setFilterEffect(filterType: PhotoFilter)

    @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
    suspend fun saveAsFile(
        imagePath: String,
        saveSettings: SaveSettings = SaveSettings.Builder().build()
    ): SaveFileResult

    suspend fun saveAsBitmap(saveSettings: SaveSettings = SaveSettings.Builder().build()): Bitmap

    fun saveAsFile(imagePath: String, saveSettings: SaveSettings, onSaveListener: OnSaveListener)

    fun saveAsFile(imagePath: String, onSaveListener: OnSaveListener)

    fun saveAsBitmap(saveSettings: SaveSettings, onSaveBitmap: OnSaveBitmap)

    fun saveAsBitmap(onSaveBitmap: OnSaveBitmap)

    fun setOnPhotoEditorListener(onPhotoEditorListener: OnPhotoEditorListener)

    val isCacheEmpty: Boolean

    class Builder(var context: Context, var photoEditorView: PhotoEditorView) {

        @JvmField
        var imageView: ImageView = photoEditorView.source

        @JvmField
        var deleteView: View? = null

        @JvmField
        var drawingView: DrawingView = photoEditorView.drawingView

        @JvmField
        var textTypeface: Typeface? = null

        @JvmField
        var emojiTypeface: Typeface? = null

        // By default, pinch-to-scale is enabled for text
        @JvmField
        var isTextPinchScalable = true

        @JvmField
        var clipSourceImage = false

        fun setDefaultTextTypeface(textTypeface: Typeface?): Builder {
            this.textTypeface = textTypeface
            return this
        }

        fun setPinchTextScalable(isTextPinchScalable: Boolean): Builder {
            this.isTextPinchScalable = isTextPinchScalable
            return this
        }

        fun build(): PhotoEditor {
            return PhotoEditorImpl(this)
        }

    }

    interface OnSaveListener {
        fun onSuccess(imagePath: String)

        fun onFailure(exception: Exception)
    }

    fun setShape(shapeBuilder: ShapeBuilder) // endregion
}