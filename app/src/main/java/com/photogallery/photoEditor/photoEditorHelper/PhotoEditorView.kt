package com.photogallery.photoEditor.photoEditorHelper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.widget.ImageView
import android.widget.RelativeLayout
import com.photogallery.R
import com.photogallery.photoEditor.photoEditorHelper.FilterImageView.OnImageChangedListener
import com.photogallery.utils.Const.GL_FILTER_ID
import com.photogallery.utils.Const.IMG_SRC_ID
import com.photogallery.utils.Const.SHAPE_SRC_ID

class PhotoEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RelativeLayout(context, attrs, defStyle) {

    private var mImgSource: FilterImageView = FilterImageView(context)

    internal var drawingView: DrawingView
        private set

    private var mImageFilterView: ImageFilterView
    private var clipSourceImage = false

    init {
        val sourceParam = setupImageSource(attrs)
        mImageFilterView = ImageFilterView(context)
        val filterParam = setupFilterView()

        mImgSource.setOnImageChangedListener(object : OnImageChangedListener {
            override fun onBitmapLoaded(sourceBitmap: Bitmap?) {
                mImageFilterView.setFilterEffect(PhotoFilter.NONE)
                mImageFilterView.setSourceBitmap(sourceBitmap)
            }
        })


        drawingView = DrawingView(context)
        val brushParam = setupDrawingView()

        addView(mImgSource, sourceParam)

        addView(mImageFilterView, filterParam)

        addView(drawingView, brushParam)
    }

    @SuppressLint("Recycle")
    private fun setupImageSource(attrs: AttributeSet?): LayoutParams {
        mImgSource.id = IMG_SRC_ID
        mImgSource.adjustViewBounds = true
        mImgSource.scaleType = ImageView.ScaleType.CENTER_INSIDE

        attrs?.let {
            val a = context.obtainStyledAttributes(it, R.styleable.PhotoEditorView)
            val imgSrcDrawable = a.getDrawable(R.styleable.PhotoEditorView_photo_src)
            if (imgSrcDrawable != null) {
                mImgSource.setImageDrawable(imgSrcDrawable)
            }
        }

        var widthParam = LayoutParams.MATCH_PARENT
        if (clipSourceImage) {
            widthParam = LayoutParams.WRAP_CONTENT
        }
        val params = LayoutParams(
            widthParam, LayoutParams.WRAP_CONTENT
        )
        params.addRule(CENTER_IN_PARENT, TRUE)
        return params
    }

    private fun setupDrawingView(): LayoutParams {
        drawingView.visibility = GONE
        drawingView.id = SHAPE_SRC_ID

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
        params.addRule(CENTER_IN_PARENT, TRUE)
        params.addRule(ALIGN_TOP, IMG_SRC_ID)
        params.addRule(ALIGN_BOTTOM, IMG_SRC_ID)
        params.addRule(ALIGN_LEFT, IMG_SRC_ID)
        params.addRule(ALIGN_RIGHT, IMG_SRC_ID)
        return params
    }

    private fun setupFilterView(): LayoutParams {
        mImageFilterView.visibility = GONE
        mImageFilterView.id = GL_FILTER_ID

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
        params.addRule(CENTER_IN_PARENT, TRUE)
        params.addRule(ALIGN_TOP, IMG_SRC_ID)
        params.addRule(ALIGN_BOTTOM, IMG_SRC_ID)
        return params
    }

    val source: ImageView
        get() = mImgSource

    internal suspend fun saveFilter(): Bitmap {
        return if (mImageFilterView.visibility == VISIBLE) {
            val saveBitmap = try {
                mImageFilterView.saveBitmap()
            } catch (t: Throwable) {
                throw RuntimeException("Couldn't save bitmap with filter", t)
            }
            mImgSource.setImageBitmap(saveBitmap)
            mImageFilterView.visibility = GONE
            saveBitmap
        } else {
            mImgSource.bitmap!!
        }
    }

    internal fun setFilterEffect(filterType: PhotoFilter) {
        mImageFilterView.visibility = VISIBLE
        mImageFilterView.setFilterEffect(filterType)
    }

    internal fun setFilterEffect(customEffect: CustomEffect?) {
        mImageFilterView.visibility = VISIBLE
        mImageFilterView.setFilterEffect(customEffect)
    }

    internal fun setClipSourceImage(clip: Boolean) {
        clipSourceImage = clip
        val param = setupImageSource(null)
        mImgSource.layoutParams = param
    } // endregion
}