package com.photogallery.activity

import android.content.Intent
import android.graphics.Bitmap.CompressFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Animatable
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.transition.AutoTransition
import androidx.transition.Transition
import androidx.transition.TransitionManager
import com.photogallery.R
import com.photogallery.crop.Crop
import com.photogallery.crop.callback.BitmapCropCallback
import com.photogallery.crop.model.AspectRatio
import com.photogallery.crop.util.SelectedStateListDrawable
import com.photogallery.crop.view.CropImageView
import com.photogallery.crop.view.CropView
import com.photogallery.crop.view.GestureCropImageView
import com.photogallery.crop.view.OverlayView
import com.photogallery.crop.view.TransformImageView.TransformImageListener
import com.photogallery.crop.view.widget.AspectRatioTextView
import com.photogallery.crop.view.widget.HorizontalProgressWheelView
import com.photogallery.crop.view.widget.HorizontalProgressWheelView.ScrollingListener
import com.photogallery.databinding.CropActivityBinding
import java.lang.Float
import java.util.Locale
import kotlin.Boolean
import kotlin.Exception
import kotlin.IllegalStateException
import kotlin.Int
import kotlin.IntArray
import kotlin.Long
import kotlin.NullPointerException
import kotlin.String
import kotlin.Throwable
import kotlin.intArrayOf

open class CropActivity : BaseActivity<CropActivityBinding>() {
    annotation class GestureTypes

    private var mToolbarTitle: String? = null
    private var mActiveControlsWidgetColor = 0
    private var mToolbarWidgetColor = 0
    private var mRootViewBackgroundColor = 0
    private var mToolbarCropDrawable = 0
    private var mShowBottomControls = false
    private var mShowLoader = true
    private var mCropView: CropView? = null
    private var mGestureCropImageView: GestureCropImageView? = null
    private var mOverlayView: OverlayView? = null
    private var mWrapperStateAspectRatio: ViewGroup? = null
    private var mWrapperStateRotate: ViewGroup? = null
    private var mWrapperStateScale: ViewGroup? = null
    private var mLayoutAspectRatio: ViewGroup? = null
    private var mLayoutRotate: ViewGroup? = null
    private var mLayoutScale: ViewGroup? = null
    private val mCropAspectRatioViews: MutableList<ViewGroup> = ArrayList<ViewGroup>()
    private var mTextViewRotateAngle: TextView? = null
    private var mTextViewScalePercent: TextView? = null
    private var mBlockingView: View? = null
    private var mControlsTransition: Transition? = null
    private var mCompressFormat: CompressFormat = DEFAULT_COMPRESS_FORMAT
    private var mCompressQuality: Int = DEFAULT_COMPRESS_QUALITY
    private var mAllowedGestures: IntArray? = intArrayOf(SCALE, ROTATE, ALL)
    private var ivBack: AppCompatImageView? = null
    private var ivSave: AppCompatTextView? = null

    override fun getViewBinding(): CropActivityBinding {
        return CropActivityBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        val intent = getIntent()
        setupViews(intent)
        setImageData(intent)
        setInitialState()
        addBlockingView()

        ivBack = findViewById<AppCompatImageView>(R.id.ivBack)
        ivSave = findViewById<AppCompatTextView?>(R.id.tvSave)
        ivBack!!.setOnClickListener { v: View? -> onBackPressedDispatcher() }
        ivSave!!.setOnClickListener { v: View? ->
            if (!mShowLoader) {
                cropAndSaveImage()
            }
        }
        if (mShowLoader) {
            ivSave!!.visibility = View.GONE
        }
    }

    override fun addListener() {
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    private fun setLoaderVisibility(visible: Boolean) {
        mShowLoader = visible
        if (ivSave != null) {
            ivSave!!.visibility = if (visible) View.GONE else View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.crop_menu, menu)

        val menuItemLoader = menu.findItem(R.id.menuLoader)
        val menuItemLoaderIcon = menuItemLoader.icon
        if (menuItemLoaderIcon != null) {
            try {
                menuItemLoaderIcon.mutate()
                menuItemLoaderIcon.colorFilter =
                    PorterDuffColorFilter(mToolbarWidgetColor, PorterDuff.Mode.SRC_ATOP)
                menuItemLoader.icon = menuItemLoaderIcon
            } catch (e: IllegalStateException) {
                Log.i(
                    TAG, String.format(
                        "%s - %s", e.message, getString(R.string.crop_mutate_exception_hint)
                    )
                )
            }
            (menuItemLoader.icon as Animatable).start()
        }

        val menuItemCrop = menu.findItem(R.id.menuCrop)
        val menuItemCropIcon = ContextCompat.getDrawable(this, mToolbarCropDrawable)
        if (menuItemCropIcon != null) {
            menuItemCropIcon.mutate()
            menuItemLoaderIcon?.colorFilter =
                PorterDuffColorFilter(mToolbarWidgetColor, PorterDuff.Mode.SRC_ATOP)
            menuItemCrop.icon = menuItemCropIcon
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menuCrop).isVisible = !mShowLoader
        menu.findItem(R.id.menuLoader).isVisible = mShowLoader
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menuCrop) {
            cropAndSaveImage()
            return true
        } else if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()
        if (mGestureCropImageView != null) {
            mGestureCropImageView!!.cancelAllAnimations()
        }
    }

    private fun setImageData(intent: Intent) {
        val inputUri = intent.getParcelableExtra<Uri?>(Crop.EXTRA_INPUT_URI)
        val outputUri = intent.getParcelableExtra<Uri?>(Crop.EXTRA_OUTPUT_URI)
        processOptions(intent)

        if (inputUri != null && outputUri != null) {
            try {
                mGestureCropImageView!!.setImageUri(inputUri, outputUri)
            } catch (e: Exception) {
                setResultError(e)
                onBackPressedDispatcher()
            }
        } else {
            setResultError(NullPointerException(getString(R.string.crop_error_input_data_is_absent)))
            onBackPressedDispatcher()
        }
    }

    private fun processOptions(intent: Intent) {
        val typedValue = TypedValue()
        getTheme().resolveAttribute(R.attr.colorApp, typedValue, true)
        val defaultColor = ContextCompat.getColor(this, R.color.color_dark_text_60)
        val compressionFormatName =
            intent.getStringExtra(Crop.Options.EXTRA_COMPRESSION_FORMAT_NAME)
        var compressFormat: CompressFormat? = null
        if (!TextUtils.isEmpty(compressionFormatName)) {
            compressFormat = CompressFormat.valueOf(compressionFormatName!!)
        }
        mCompressFormat = compressFormat ?: DEFAULT_COMPRESS_FORMAT

        mCompressQuality =
            intent.getIntExtra(Crop.Options.EXTRA_COMPRESSION_QUALITY, DEFAULT_COMPRESS_QUALITY)

        // Gestures options
        val allowedGestures = intent.getIntArrayExtra(Crop.Options.EXTRA_ALLOWED_GESTURES)
        if (allowedGestures != null && allowedGestures.size == TABS_COUNT) {
            mAllowedGestures = allowedGestures
        }

        mGestureCropImageView!!.maxBitmapSize = intent.getIntExtra(
            Crop.Options.EXTRA_MAX_BITMAP_SIZE, CropImageView.DEFAULT_MAX_BITMAP_SIZE
        )
        mGestureCropImageView!!.setMaxScaleMultiplier(
            intent.getFloatExtra(
                Crop.Options.EXTRA_MAX_SCALE_MULTIPLIER, CropImageView.DEFAULT_MAX_SCALE_MULTIPLIER
            )
        )
        mGestureCropImageView!!.setImageToWrapCropBoundsAnimDuration(
            intent.getIntExtra(
                Crop.Options.EXTRA_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION,
                CropImageView.DEFAULT_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION
            ).toLong()
        )

        mOverlayView!!.isFreestyleCropEnabled = intent.getBooleanExtra(
            Crop.Options.EXTRA_FREE_STYLE_CROP,
            OverlayView.DEFAULT_FREESTYLE_CROP_MODE != OverlayView.FREESTYLE_CROP_MODE_DISABLE
        )

        mOverlayView!!.setDimmedColor(
            intent.getIntExtra(
                Crop.Options.EXTRA_DIMMED_LAYER_COLOR, getColor(R.color.crop_color_default_dimmed)
            )
        )
        mOverlayView!!.setCircleDimmedLayer(
            intent.getBooleanExtra(
                Crop.Options.EXTRA_CIRCLE_DIMMED_LAYER, false
            )
        )

        mOverlayView!!.setShowCropFrame(
            intent.getBooleanExtra(
                Crop.Options.EXTRA_SHOW_CROP_FRAME, true
            )
        )
        mOverlayView!!.setCropFrameColor(
            intent.getIntExtra(
                Crop.Options.EXTRA_CROP_FRAME_COLOR,
                if (typedValue.data != 0) typedValue.data else defaultColor
            )
        )
        mOverlayView!!.setCropFrameStrokeWidth(
            intent.getIntExtra(
                Crop.Options.EXTRA_CROP_FRAME_STROKE_WIDTH,
                getResources().getDimensionPixelSize(R.dimen.crop_default_crop_frame_stoke_width)
            )
        )

        mOverlayView!!.setShowCropGrid(
            intent.getBooleanExtra(
                Crop.Options.EXTRA_SHOW_CROP_GRID, true
            )
        )
        mOverlayView!!.setCropGridRowCount(
            intent.getIntExtra(
                Crop.Options.EXTRA_CROP_GRID_ROW_COUNT, OverlayView.DEFAULT_CROP_GRID_ROW_COUNT
            )
        )
        mOverlayView!!.setCropGridColumnCount(
            intent.getIntExtra(
                Crop.Options.EXTRA_CROP_GRID_COLUMN_COUNT,
                OverlayView.DEFAULT_CROP_GRID_COLUMN_COUNT
            )
        )
        mOverlayView!!.setCropGridColor(
            intent.getIntExtra(
                Crop.Options.EXTRA_CROP_GRID_COLOR, getColor(R.color.crop_color_default_crop_grid)
            )
        )
        mOverlayView!!.setCropGridCornerColor(
            intent.getIntExtra(
                Crop.Options.EXTRA_CROP_GRID_CORNER_COLOR,
                getColor(R.color.crop_color_default_crop_grid)
            )
        )
        mOverlayView!!.setCropGridStrokeWidth(
            intent.getIntExtra(
                Crop.Options.EXTRA_CROP_GRID_STROKE_WIDTH,
                getResources().getDimensionPixelSize(R.dimen.crop_default_crop_grid_stoke_width)
            )
        )

        val aspectRatioX = intent.getFloatExtra(Crop.EXTRA_ASPECT_RATIO_X, -1f)
        val aspectRatioY = intent.getFloatExtra(Crop.EXTRA_ASPECT_RATIO_Y, -1f)

        val aspectRationSelectedByDefault =
            intent.getIntExtra(Crop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0)
        val aspectRatioList =
            intent.getParcelableArrayListExtra<AspectRatio?>(Crop.Options.EXTRA_ASPECT_RATIO_OPTIONS)

        if (aspectRatioX >= 0 && aspectRatioY >= 0) {
            if (mWrapperStateAspectRatio != null) {
                mWrapperStateAspectRatio!!.visibility = View.GONE
            }
            val targetAspectRatio = aspectRatioX / aspectRatioY
            mGestureCropImageView!!.setTargetAspectRatio(if (Float.isNaN(targetAspectRatio)) CropImageView.SOURCE_IMAGE_ASPECT_RATIO else targetAspectRatio)
        } else if (aspectRatioList != null && aspectRationSelectedByDefault < aspectRatioList.size) {
            val targetAspectRatio =
                aspectRatioList[aspectRationSelectedByDefault]!!.aspectRatioX / aspectRatioList[aspectRationSelectedByDefault]!!.aspectRatioY
            mGestureCropImageView!!.setTargetAspectRatio(if (Float.isNaN(targetAspectRatio)) CropImageView.SOURCE_IMAGE_ASPECT_RATIO else targetAspectRatio)
        } else {
            mGestureCropImageView!!.setTargetAspectRatio(CropImageView.SOURCE_IMAGE_ASPECT_RATIO)
        }
        val maxSizeX = intent.getIntExtra(Crop.EXTRA_MAX_SIZE_X, 0)
        val maxSizeY = intent.getIntExtra(Crop.EXTRA_MAX_SIZE_Y, 0)
        if (maxSizeX > 0 && maxSizeY > 0) {
            mGestureCropImageView!!.setMaxResultImageSizeX(maxSizeX)
            mGestureCropImageView!!.setMaxResultImageSizeY(maxSizeY)
        }
    }

    private fun setupViews(intent: Intent) {
        val typedValue = TypedValue()
        getTheme().resolveAttribute(R.attr.colorApp, typedValue, true)
        val defaultColor = ContextCompat.getColor(this, R.color.color_dark_text_60)
        mActiveControlsWidgetColor = intent.getIntExtra(
            Crop.Options.EXTRA_UCROP_COLOR_CONTROLS_WIDGET_ACTIVE,
            if (typedValue.data != 0) typedValue.data else defaultColor
        )

        mToolbarWidgetColor = intent.getIntExtra(
            Crop.Options.EXTRA_UCROP_WIDGET_COLOR_TOOLBAR,
            ContextCompat.getColor(this, R.color.black)
        )
        mToolbarCropDrawable =
            intent.getIntExtra(Crop.Options.EXTRA_UCROP_WIDGET_CROP_DRAWABLE, R.drawable.ic_done)
        mToolbarTitle = intent.getStringExtra(Crop.Options.EXTRA_UCROP_TITLE_TEXT_TOOLBAR)
        mToolbarTitle =
            if (mToolbarTitle != null) mToolbarTitle else getResources().getString(R.string.edit_photo)
        mShowBottomControls =
            !intent.getBooleanExtra(Crop.Options.EXTRA_HIDE_BOTTOM_CONTROLS, false)
        mRootViewBackgroundColor = intent.getIntExtra(
            Crop.Options.EXTRA_UCROP_ROOT_VIEW_BACKGROUND_COLOR,
            ContextCompat.getColor(this, R.color.white)
        )
        initiateRootViews()

        if (mShowBottomControls) {
            val viewGroup = findViewById<ViewGroup>(R.id.rlCrop)
            val wrapper = viewGroup.findViewById<ViewGroup>(R.id.flControlsWrapper)
            wrapper.visibility = View.VISIBLE
            LayoutInflater.from(this).inflate(R.layout.crop_controls, wrapper, true)

            mControlsTransition = AutoTransition()
            mControlsTransition!!.setDuration(CONTROLS_ANIMATION_DURATION)
            mWrapperStateAspectRatio = findViewById(R.id.llStateAspectRatio)
            mWrapperStateAspectRatio!!.setOnClickListener(mStateClickListener)
            mWrapperStateRotate = findViewById(R.id.llStateRotate)
            mWrapperStateRotate!!.setOnClickListener(mStateClickListener)
            mWrapperStateScale = findViewById(R.id.llStateScale)
            mWrapperStateScale!!.setOnClickListener(mStateClickListener)

            mLayoutAspectRatio = findViewById(R.id.llLayoutAspectRatio)
            mLayoutRotate = findViewById(R.id.layoutRotateWheel)
            mLayoutScale = findViewById(R.id.layoutScaleWheel)

            setupAspectRatioWidget(intent)
            setupRotateWidget()
            setupScaleWidget()
            setupStatesWrapper()
        }
    }

    /**
     * Configures and styles both status bar and toolbar.
     */
    private fun initiateRootViews() {
        mCropView = findViewById(R.id.crop)
        mGestureCropImageView = mCropView!!.cropImageView
        mOverlayView = mCropView!!.overlayView

        mGestureCropImageView!!.setTransformImageListener(mImageListener)

        binding.flCropFrame.setBackgroundColor(mRootViewBackgroundColor)
        if (!mShowBottomControls) {
            val params = binding.flCropFrame.layoutParams as RelativeLayout.LayoutParams
            params.bottomMargin = 0
            binding.flCropFrame.requestLayout()
        }
    }

    private val mImageListener: TransformImageListener = object : TransformImageListener {
        override fun onRotate(currentAngle: kotlin.Float) {
            setAngleText(currentAngle)
        }

        override fun onScale(currentScale: kotlin.Float) {
            setScaleText(currentScale)
        }

        override fun onLoadComplete() {
            mCropView!!.animate().alpha(1f).setDuration(300)
                .setInterpolator(AccelerateInterpolator())
            mBlockingView!!.isClickable = false
            setLoaderVisibility(false)
        }

        override fun onLoadFailure(e: Exception) {
            setResultError(e)
            onBackPressedDispatcher()
        }
    }

    private fun setupStatesWrapper() {
        val stateScaleImageView = findViewById<ImageView>(R.id.ivImageViewStateScale)
        val stateRotateImageView = findViewById<ImageView>(R.id.ivImageViewStateRotate)
        val stateAspectRatioImageView = findViewById<ImageView>(R.id.ivImageViewStateAspectRatio)

        stateScaleImageView.setImageDrawable(
            SelectedStateListDrawable(
                stateScaleImageView.getDrawable(), mActiveControlsWidgetColor
            )
        )
        stateRotateImageView.setImageDrawable(
            SelectedStateListDrawable(
                stateRotateImageView.getDrawable(), mActiveControlsWidgetColor
            )
        )
        stateAspectRatioImageView.setImageDrawable(
            SelectedStateListDrawable(
                stateAspectRatioImageView.getDrawable(), mActiveControlsWidgetColor
            )
        )
    }

    private fun setupAspectRatioWidget(intent: Intent) {
        var aspectRationSelectedByDefault =
            intent.getIntExtra(Crop.Options.EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT, 0)
        var aspectRatioList =
            intent.getParcelableArrayListExtra<AspectRatio?>(Crop.Options.EXTRA_ASPECT_RATIO_OPTIONS)

        if (aspectRatioList == null || aspectRatioList.isEmpty()) {
            aspectRationSelectedByDefault = 2

            aspectRatioList = ArrayList<AspectRatio>()
            aspectRatioList.add(AspectRatio(null, 1f, 1f))
            aspectRatioList.add(AspectRatio(null, 3f, 4f))
            aspectRatioList.add(
                AspectRatio(
                    getString(R.string.original).uppercase(Locale.getDefault()),
                    CropImageView.SOURCE_IMAGE_ASPECT_RATIO,
                    CropImageView.SOURCE_IMAGE_ASPECT_RATIO
                )
            )
            aspectRatioList.add(AspectRatio(null, 3f, 2f))
            aspectRatioList.add(AspectRatio(null, 16f, 9f))
        }

        val wrapperAspectRatioList = findViewById<LinearLayout>(R.id.llLayoutAspectRatio)

        var wrapperAspectRatio: FrameLayout
        var aspectRatioTextView: AspectRatioTextView
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT)
        lp.weight = 1f
        for (aspectRatio in aspectRatioList) {
            wrapperAspectRatio =
                layoutInflater.inflate(R.layout.crop_aspect_ratio, null) as FrameLayout
            wrapperAspectRatio.setLayoutParams(lp)
            aspectRatioTextView = (wrapperAspectRatio.getChildAt(0) as AspectRatioTextView)
            aspectRatioTextView.setActiveColor(mActiveControlsWidgetColor)
            if (aspectRatio != null) {
                aspectRatioTextView.setAspectRatio(aspectRatio)
            }

            wrapperAspectRatioList.addView(wrapperAspectRatio)
            mCropAspectRatioViews.add(wrapperAspectRatio)
        }

        mCropAspectRatioViews[aspectRationSelectedByDefault].isSelected = true

        for (cropAspectRatioView in mCropAspectRatioViews) {
            cropAspectRatioView.setOnClickListener { v: View? ->
                mGestureCropImageView!!.setTargetAspectRatio(
                    ((v as ViewGroup).getChildAt(0) as AspectRatioTextView).getAspectRatio(v.isSelected)
                )
                mGestureCropImageView!!.setImageToWrapCropBounds()
                if (!v.isSelected) {
                    for (cropAspectRatioView1 in mCropAspectRatioViews) {
                        cropAspectRatioView1.isSelected = cropAspectRatioView1 === v
                    }
                }
            }
        }
    }

    private fun setupRotateWidget() {
        mTextViewRotateAngle = findViewById(R.id.tvRotateText)
        (findViewById<View?>(R.id.rotateScrollWheel) as HorizontalProgressWheelView).setScrollingListener(
            object : ScrollingListener {
                override fun onScroll(delta: kotlin.Float, totalDistance: kotlin.Float) {
                    mGestureCropImageView!!.postRotate(delta / ROTATE_WIDGET_SENSITIVITY_COEFFICIENT)
                }

                override fun onScrollEnd() {
                    mGestureCropImageView!!.setImageToWrapCropBounds()
                }

                override fun onScrollStart() {
                    mGestureCropImageView!!.cancelAllAnimations()
                }
            })

        (findViewById<View?>(R.id.rotateScrollWheel) as HorizontalProgressWheelView).setMiddleLineColor(
            mActiveControlsWidgetColor
        )


        findViewById<View>(R.id.flWrapperResetRotate)?.let { view ->
            view.setOnClickListener { resetRotation() }
        }

        findViewById<View>(R.id.flWrapperRotateByAngle)?.let { view ->
            view.setOnClickListener { rotateByAngle(90) }
        }
        setAngleTextColor(mActiveControlsWidgetColor)
    }

    private fun setupScaleWidget() {
        mTextViewScalePercent = findViewById(R.id.tvScale)
        (findViewById<View?>(R.id.scaleScrollWheel) as HorizontalProgressWheelView).setScrollingListener(
            object : ScrollingListener {
                override fun onScroll(delta: kotlin.Float, totalDistance: kotlin.Float) {
                    if (delta > 0) {
                        mGestureCropImageView!!.zoomInImage(
                            mGestureCropImageView!!.currentScale + delta * ((mGestureCropImageView!!.maxScale - mGestureCropImageView!!.minScale) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT)
                        )
                    } else {
                        mGestureCropImageView!!.zoomOutImage(
                            mGestureCropImageView!!.currentScale + delta * ((mGestureCropImageView!!.maxScale - mGestureCropImageView!!.minScale) / SCALE_WIDGET_SENSITIVITY_COEFFICIENT)
                        )
                    }
                }

                override fun onScrollEnd() {
                    mGestureCropImageView!!.setImageToWrapCropBounds()
                }

                override fun onScrollStart() {
                    mGestureCropImageView!!.cancelAllAnimations()
                }
            })
        (findViewById<View?>(R.id.scaleScrollWheel) as HorizontalProgressWheelView).setMiddleLineColor(
            mActiveControlsWidgetColor
        )

        setScaleTextColor(mActiveControlsWidgetColor)
    }

    private fun setAngleText(angle: kotlin.Float) {
        if (mTextViewRotateAngle != null) {
            mTextViewRotateAngle!!.text = String.format(Locale.getDefault(), "%.1f°", angle)
        }
    }

    private fun setAngleTextColor(textColor: Int) {
        if (mTextViewRotateAngle != null) {
            mTextViewRotateAngle!!.setTextColor(textColor)
        }
    }

    private fun setScaleText(scale: kotlin.Float) {
        if (mTextViewScalePercent != null) {
            mTextViewScalePercent!!.text = String.format(
                Locale.getDefault(), "%d%%", (scale * 100).toInt()
            )
        }
    }

    private fun setScaleTextColor(textColor: Int) {
        if (mTextViewScalePercent != null) {
            mTextViewScalePercent!!.setTextColor(textColor)
        }
    }

    private fun resetRotation() {
        mGestureCropImageView!!.postRotate(-mGestureCropImageView!!.currentAngle)
        mGestureCropImageView!!.setImageToWrapCropBounds()
    }

    private fun rotateByAngle(angle: Int) {
        mGestureCropImageView!!.postRotate(angle.toFloat())
        mGestureCropImageView!!.setImageToWrapCropBounds()
    }

    private val mStateClickListener = View.OnClickListener { v: View? ->
        if (!v!!.isSelected) {
            setWidgetState(v.id)
        }
    }

    private fun setInitialState() {
        if (mShowBottomControls) {
            if (mWrapperStateAspectRatio!!.isVisible) {
                setWidgetState(R.id.llStateAspectRatio)
            } else {
                setWidgetState(R.id.llStateScale)
            }
        } else {
            setAllowedGestures(0)
        }
    }

    private fun setWidgetState(@IdRes stateViewId: Int) {
        if (!mShowBottomControls) return

        mWrapperStateAspectRatio!!.isSelected = stateViewId == R.id.llStateAspectRatio
        mWrapperStateRotate!!.isSelected = stateViewId == R.id.llStateRotate
        mWrapperStateScale!!.isSelected = stateViewId == R.id.llStateScale
        mLayoutAspectRatio!!.visibility =
            if (stateViewId == R.id.llStateAspectRatio) View.VISIBLE else View.GONE
        mLayoutRotate!!.visibility =
            if (stateViewId == R.id.llStateRotate) View.VISIBLE else View.GONE
        mLayoutScale!!.visibility =
            if (stateViewId == R.id.llStateScale) View.VISIBLE else View.GONE

        changeSelectedTab(stateViewId)

        when (stateViewId) {
            R.id.llStateScale -> {
                setAllowedGestures(0)
            }

            R.id.llStateRotate -> {
                setAllowedGestures(1)
            }

            else -> {
                setAllowedGestures(2)
            }
        }
    }

    private fun changeSelectedTab(stateViewId: Int) {
        TransitionManager.beginDelayedTransition(
            findViewById(R.id.rlCrop), mControlsTransition
        )

        val typedValue = TypedValue()
        getTheme().resolveAttribute(R.attr.colorApp, typedValue, true)
        val selectedColor = typedValue.data
        val unselectedColor = ContextCompat.getColor(this, R.color.white)

        mWrapperStateScale?.findViewById<TextView>(R.id.tvScale)
            ?.setTextColor(if (stateViewId == R.id.llStateScale) selectedColor else unselectedColor)

        mWrapperStateAspectRatio?.findViewById<TextView>(R.id.tvCrop)
            ?.setTextColor(if (stateViewId == R.id.llStateAspectRatio) selectedColor else unselectedColor)

        mWrapperStateRotate?.findViewById<TextView>(R.id.tvRotateText)
            ?.setTextColor(if (stateViewId == R.id.llStateRotate) selectedColor else unselectedColor)
    }

    private fun setAllowedGestures(tab: Int) {
        mGestureCropImageView!!.isScaleEnabled =
            mAllowedGestures!![tab] == ALL || mAllowedGestures!![tab] == SCALE
        mGestureCropImageView!!.isRotateEnabled =
            mAllowedGestures!![tab] == ALL || mAllowedGestures!![tab] == ROTATE
    }

    private fun addBlockingView() {
        if (mBlockingView == null) {
            mBlockingView = View(this)
            val lp = RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            lp.addRule(RelativeLayout.BELOW, R.id.toolbar)
            mBlockingView!!.setLayoutParams(lp)
            mBlockingView!!.isClickable = true
        }

        (findViewById<View?>(R.id.rlCrop) as RelativeLayout).addView(mBlockingView)
    }

    protected fun cropAndSaveImage() {
        mBlockingView!!.isClickable = true
        mShowLoader = true
        supportInvalidateOptionsMenu()

        mGestureCropImageView!!.cropAndSaveImage(
            mCompressFormat, mCompressQuality, object : BitmapCropCallback {
                override fun onBitmapCropped(
                    resultUri: Uri, offsetX: Int, offsetY: Int, imageWidth: Int, imageHeight: Int
                ) {
                    setResultUri(
                        resultUri,
                        mGestureCropImageView!!.targetAspectRatio,
                        offsetX,
                        offsetY,
                        imageWidth,
                        imageHeight
                    )
                    onBackPressedDispatcher()
                }

                override fun onCropFailure(t: Throwable) {
                    setResultError(t)
                    onBackPressedDispatcher()
                }
            })
    }

    protected fun setResultUri(
        uri: Uri?,
        resultAspectRatio: kotlin.Float,
        offsetX: Int,
        offsetY: Int,
        imageWidth: Int,
        imageHeight: Int
    ) {
        setResult(
            RESULT_OK,
            Intent().putExtra(Crop.EXTRA_OUTPUT_URI, uri)
                .putExtra(Crop.EXTRA_OUTPUT_CROP_ASPECT_RATIO, resultAspectRatio)
                .putExtra(Crop.EXTRA_OUTPUT_IMAGE_WIDTH, imageWidth)
                .putExtra(Crop.EXTRA_OUTPUT_IMAGE_HEIGHT, imageHeight)
                .putExtra(Crop.EXTRA_OUTPUT_OFFSET_X, offsetX)
                .putExtra(Crop.EXTRA_OUTPUT_OFFSET_Y, offsetY)
        )
    }

    protected fun setResultError(throwable: Throwable?) {
        setResult(Crop.RESULT_ERROR, Intent().putExtra(Crop.EXTRA_ERROR, throwable))
    }

    companion object {
        const val DEFAULT_COMPRESS_QUALITY: Int = 90
        val DEFAULT_COMPRESS_FORMAT: CompressFormat = CompressFormat.JPEG
        const val SCALE: Int = 1
        const val ROTATE: Int = 2
        const val ALL: Int = 3

        private const val TAG = "UCropActivity"
        private const val CONTROLS_ANIMATION_DURATION: Long = 50
        private const val TABS_COUNT = 3
        private const val SCALE_WIDGET_SENSITIVITY_COEFFICIENT = 15000
        private const val ROTATE_WIDGET_SENSITIVITY_COEFFICIENT = 42

        init {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        }
    }
}
