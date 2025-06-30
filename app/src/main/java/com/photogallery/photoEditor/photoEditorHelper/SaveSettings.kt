package com.photogallery.photoEditor.photoEditorHelper

import android.graphics.Bitmap.CompressFormat

class SaveSettings private constructor(builder: Builder) {
    val isTransparencyEnabled: Boolean
    val isClearViewsEnabled: Boolean
    val compressFormat: CompressFormat
    val compressQuality: Int

    class Builder {
        @JvmField
        var isTransparencyEnabled = true
        @JvmField
        var isClearViewsEnabled = true
        @JvmField
        var compressFormat = CompressFormat.PNG
        @JvmField
        var compressQuality = 100

        fun setTransparencyEnabled(transparencyEnabled: Boolean): Builder {
            isTransparencyEnabled = transparencyEnabled
            return this
        }

        fun setClearViewsEnabled(clearViewsEnabled: Boolean): Builder {
            isClearViewsEnabled = clearViewsEnabled
            return this
        }

        fun build(): SaveSettings {
            return SaveSettings(this)
        }
    }

    init {
        isClearViewsEnabled = builder.isClearViewsEnabled
        isTransparencyEnabled = builder.isTransparencyEnabled
        compressFormat = builder.compressFormat
        compressQuality = builder.compressQuality
    }
}