package com.photogallery.photoEditor.photoEditing

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.AnticipateOvershootInterpolator
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.ChangeBounds
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.activity.CropImageActivity
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityEditImageBinding
import com.photogallery.photoEditor.photoEditing.EmojiBSFragment.EmojiListener
import com.photogallery.photoEditor.photoEditing.StickerBSFragment.StickerListener
import com.photogallery.photoEditor.photoEditing.filters.FilterListener
import com.photogallery.photoEditor.photoEditing.filters.FilterViewAdapter
import com.photogallery.photoEditor.photoEditing.tools.EditingToolsAdapter
import com.photogallery.photoEditor.photoEditing.tools.EditingToolsAdapter.OnItemSelected
import com.photogallery.photoEditor.photoEditing.tools.ToolType
import com.photogallery.photoEditor.photoEditorHelper.OnPhotoEditorListener
import com.photogallery.photoEditor.photoEditorHelper.PhotoEditor
import com.photogallery.photoEditor.photoEditorHelper.PhotoEditorView
import com.photogallery.photoEditor.photoEditorHelper.PhotoFilter
import com.photogallery.photoEditor.photoEditorHelper.SaveFileResult
import com.photogallery.photoEditor.photoEditorHelper.SaveSettings
import com.photogallery.photoEditor.photoEditorHelper.TextStyleBuilder
import com.photogallery.photoEditor.photoEditorHelper.ViewType
import com.photogallery.photoEditor.photoEditorHelper.shape.ShapeBuilder
import com.photogallery.photoEditor.photoEditorHelper.shape.ShapeType
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class EditImageActivity : BaseActivity<ActivityEditImageBinding>(), OnPhotoEditorListener,
    PropertiesBSFragment.Properties, ShapeBSFragment.Properties, EmojiListener, StickerListener,
    OnItemSelected, FilterListener, BlurBSFragment.BlurProperties,
    RotateBSFragment.RotateProperties {

    lateinit var mPhotoEditor: PhotoEditor
    private lateinit var mPhotoEditorView: PhotoEditorView
    private lateinit var mPropertiesBSFragment: PropertiesBSFragment
    private lateinit var mShapeBSFragment: ShapeBSFragment
    private lateinit var mShapeBuilder: ShapeBuilder
    private lateinit var mEmojiBSFragment: EmojiBSFragment
    private lateinit var mStickerBSFragment: StickerBSFragment
    private lateinit var mRvTools: RecyclerView
    private lateinit var mRvFilters: RecyclerView
    private lateinit var mImgUndo: View
    private lateinit var mImgRedo: View
    private lateinit var mBlurBSFragment: BlurBSFragment
    private var mEditingToolsAdapter = EditingToolsAdapter(this, this)
    private val mFilterViewAdapter = FilterViewAdapter(this)
    private lateinit var mRotateBSFragment: RotateBSFragment
    private lateinit var mRootView: ConstraintLayout
    private val mConstraintSet = ConstraintSet()
    private lateinit var mSaveFileHelper: FileSaveHelper
    private var mIsFilterVisible = false
    private var imageUri: Uri? = null
    private var photoFilter: PhotoFilter = PhotoFilter.NONE
    private var currentBlurRadius: Int = 0
    private var rotationCount: Int = 0
    private var flipHorizontal: Boolean = false
    private var flipVertical: Boolean = false
    private var savedRotationCount: Int = 0
    private var savedFlipHorizontal: Boolean = false
    private var savedFlipVertical: Boolean = false

    @VisibleForTesting
    var mSaveImageUri: Uri? = null

    override fun getViewBinding(): ActivityEditImageBinding {
        return ActivityEditImageBinding.inflate(layoutInflater)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CROP_IMAGE && resultCode == RESULT_OK) {
            data?.let {
                val croppedUri = it.getParcelableExtra<Uri>("croppedUri")
                if (croppedUri != null) {
                    try {
                        imageUri = croppedUri // Update imageUri to the cropped image
                        val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                        mPhotoEditorView.source.setImageBitmap(bitmap)
                        mImgUndo.isEnabled = mPhotoEditor.isUndoAvailable
                        mImgRedo.isEnabled = mPhotoEditor.isRedoAvailable
                    } catch (e: IOException) {
                        e.printStackTrace()
                        Toast.makeText(this, R.string.invalid_image_path, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun init(savedInstanceState: Bundle?) {
        mPhotoEditorView = binding.photoEditorView
        mRvTools = binding.rvConstraintTools
        mRvFilters = binding.rvFilterView
        mRootView = binding.rootView
        mImgUndo = binding.imgUndo
        mImgRedo = binding.imgRedo
        mImgUndo.isEnabled = false
        mImgRedo.isEnabled = false

        handleIntentImage(mPhotoEditorView.source)
        mPropertiesBSFragment = PropertiesBSFragment()
        mEmojiBSFragment = EmojiBSFragment()
        mStickerBSFragment = StickerBSFragment()
        mShapeBSFragment = ShapeBSFragment()
        mStickerBSFragment.setStickerListener(this)
        mEmojiBSFragment.setEmojiListener(this)
        mPropertiesBSFragment.setPropertiesChangeListener(this)
        mShapeBSFragment.setPropertiesChangeListener(this)
        mBlurBSFragment = BlurBSFragment()
        mBlurBSFragment.setBlurPropertiesListener(this)
        mRotateBSFragment = RotateBSFragment()
        mRotateBSFragment.setRotatePropertiesListener(this)

        mEditingToolsAdapter = EditingToolsAdapter(this, this)
        mRvTools.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mRvTools.adapter = mEditingToolsAdapter
        mEditingToolsAdapter.initializeTools(this)

        mRvFilters.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        mRvFilters.adapter = mFilterViewAdapter

        val pinchTextScalable = intent.getBooleanExtra(PINCH_TEXT_SCALABLE_INTENT_KEY, true)
        val mTextRobotoTf = ResourcesCompat.getFont(this, R.font.kumbh_sans_regular)

        mPhotoEditor =
            PhotoEditor.Builder(this, mPhotoEditorView).setPinchTextScalable(pinchTextScalable)
                .setDefaultTextTypeface(mTextRobotoTf).build()

        mPhotoEditor.setOnPhotoEditorListener(this)
        mSaveFileHelper = FileSaveHelper(this)

        if (ePreferences.getBoolean("isFirstTimeEditImageUndo", true)) {
            setupTooltip(
                this,
                binding.imgUndo,
                getString(R.string.click_to_undo),
                ArrowOrientation.BOTTOM,
                ePreferences,
                "isFirstTimeEditImageUndo"
            ) {
                if (ePreferences.getBoolean("isFirstTimeEditImageRedo", true)) {
                    setupTooltip(
                        this,
                        binding.imgRedo,
                        getString(R.string.click_to_redo),
                        ArrowOrientation.BOTTOM,
                        ePreferences,
                        "isFirstTimeEditImageRedo"
                    )
                    {
                        if (ePreferences.getBoolean("isFirstTimeEditImageRecyclerView", true)) {
                            setupTooltip(
                                this,
                                binding.rvConstraintTools,
                                getString(R.string.scroll_horizontal_for_more_tools),
                                ArrowOrientation.BOTTOM,
                                ePreferences,
                                "isFirstTimeEditImageRecyclerView"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun addListener() {
        binding.imgUndo.setOnClickListener {
            mImgUndo.isEnabled = mPhotoEditor.undo()
            mImgRedo.isEnabled = mPhotoEditor.isRedoAvailable
        }
        binding.imgRedo.setOnClickListener {
            mImgUndo.isEnabled = mPhotoEditor.isUndoAvailable
            mImgRedo.isEnabled = mPhotoEditor.redo()
        }
        binding.tvSave.setOnClickListener {
            CoroutineScope(Dispatchers.Main).launch {
                saveImage()
            }
        }
        binding.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }
        binding.layoutControlOptions.tvCancel.setOnClickListener {
            if (mIsFilterVisible) {
                showFilter(false)
            }
            photoFilter = PhotoFilter.NONE
            mPhotoEditor.setFilterEffect(PhotoFilter.NONE)
        }
        binding.layoutControlOptions.tvDone.setOnClickListener {
            if (mIsFilterVisible) {
                showFilter(false)
            }
            mPhotoEditor.setFilterEffect(photoFilter)
        }
    }

    override fun onBackPressedDispatcher() {
        if (mIsFilterVisible) {
            showFilter(false)
        } else if (!mPhotoEditor.isCacheEmpty) {
            showSaveDialog()
        } else {
            finish()
            backScreenAnimation()
        }
    }

    private fun handleIntentImage(source: ImageView) {
        if (intent == null) return
        imageUri = intent.getParcelableExtra("uri")
        if (imageUri != null) {
            try {
                val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
                source.setImageBitmap(bitmap)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } else {
            Toast.makeText(this, R.string.invalid_image_path, Toast.LENGTH_SHORT).show()
            finish()
            backScreenAnimation()
        }
    }

    override fun onEditTextChangeListener(rootView: View, text: String, colorCode: Int) {
        val textEditorDialogFragment = TextEditorDialogFragment.show(this, text, colorCode)
        textEditorDialogFragment.setOnTextEditorListener(object :
            TextEditorDialogFragment.TextEditorListener {
            override fun onDone(inputText: String, colorCode: Int) {
                val styleBuilder = TextStyleBuilder()
                styleBuilder.withTextColor(colorCode)
                mPhotoEditor.editText(rootView, inputText, styleBuilder)
            }
        })
    }

    override fun onAddViewListener(viewType: ViewType, numberOfAddedViews: Int) {
        mImgUndo.isEnabled = mPhotoEditor.isUndoAvailable
        mImgRedo.isEnabled = mPhotoEditor.isRedoAvailable
    }

    override fun onRemoveViewListener(viewType: ViewType, numberOfAddedViews: Int) {
        mImgUndo.isEnabled = mPhotoEditor.isUndoAvailable
        mImgRedo.isEnabled = mPhotoEditor.isRedoAvailable
    }

    override fun onStartViewChangeListener(viewType: ViewType) {}
    override fun onStopViewChangeListener(viewType: ViewType) {}
    override fun onTouchSourceImage(event: MotionEvent) {}

    @RequiresPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    private suspend fun saveImage() {
        val fileName = "${System.currentTimeMillis()}.png"
        if (FileSaveHelper.isSdkHigherThan28()) {
            showLoading()
            mSaveFileHelper.createFile(fileName, object : FileSaveHelper.OnFileCreateResult {
                @RequiresPermission(allOf = [Manifest.permission.WRITE_EXTERNAL_STORAGE])
                override fun onFileCreateResult(
                    created: Boolean, filePath: String?, error: String?, uri: Uri?
                ) {
                    lifecycleScope.launch {
                        if (created && filePath != null) {
                            try {
                                val sourceBitmap = mPhotoEditorView.source.drawable.toBitmap()
                                val transformedBitmap = RotateHelper.applyTransformations(
                                    sourceBitmap,
                                    savedRotationCount,
                                    savedFlipHorizontal,
                                    savedFlipVertical
                                )
                                val finalBitmap = if (currentBlurRadius > 0) {
                                    BlurHelper.applyBlur(
                                        this@EditImageActivity, transformedBitmap, currentBlurRadius
                                    )
                                } else {
                                    transformedBitmap
                                }
                                val tempFile = File.createTempFile("temp", ".png", cacheDir)
                                finalBitmap.compress(
                                    Bitmap.CompressFormat.PNG, 100, FileOutputStream(tempFile)
                                )
                                val tempUri = Uri.fromFile(tempFile)
                                imageUri = tempUri // Update imageUri

                                val saveSettings = SaveSettings.Builder().setClearViewsEnabled(true)
                                    .setTransparencyEnabled(true).build()

                                val result = mPhotoEditor.saveAsFile(filePath, saveSettings)

                                if (result is SaveFileResult.Success) {
                                    mSaveFileHelper.notifyThatFileIsNowPubliclyAvailable(
                                        contentResolver
                                    )
                                    MyApplication.isPhotoFetchReload = true
                                    hideLoading()
                                    mSaveImageUri = uri
                                    mPhotoEditorView.source.setImageURI(mSaveImageUri)
                                    finish()
                                    backScreenAnimation()
                                } else {
                                    hideLoading()
                                }
                            } catch (_: IOException) {
                                hideLoading()
                            }
                        } else {
                            hideLoading()
                        }
                    }
                }
            })
        } else {
            showLoading()
            try {
                val file = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    fileName
                )

                val saveSettings =
                    SaveSettings.Builder().setClearViewsEnabled(true).setTransparencyEnabled(true)
                        .build()

                val result = mPhotoEditor.saveAsFile(file.absolutePath, saveSettings)
                if (result is SaveFileResult.Success) {
                    val uri = Uri.fromFile(file)
                    sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, uri))
                    MyApplication.isPhotoFetchReload = true
                    hideLoading()
                    mSaveImageUri = uri
                    mPhotoEditorView.source.setImageURI(mSaveImageUri)
                    finish()
                    backScreenAnimation()
                } else {
                    hideLoading()
                }
            } catch (_: IOException) {
                hideLoading()
            }
        }
    }

    override fun onColorChanged(colorCode: Int) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeColor(colorCode))
    }

    override fun onOpacityChanged(opacity: Int) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeOpacity(opacity))
    }

    override fun onShapeSizeChanged(shapeSize: Int) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeSize(shapeSize.toFloat()))
    }

    override fun onShapePicked(shapeType: ShapeType) {
        mPhotoEditor.setShape(mShapeBuilder.withShapeType(shapeType))
    }

    override fun onEmojiClick(emojiUnicode: String) {
        mPhotoEditor.addEmoji(emojiUnicode)
    }

    override fun onStickerClick(bitmap: Bitmap) {
        mPhotoEditor.addImage(bitmap)
    }

    private fun showSaveDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_all_photos, null)
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)

        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }

        val btnCancel = dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
        val btnYesDelete = dialogView.findViewById<AppCompatButton>(R.id.btnYesDelete)
        val tvTitle = dialogView.findViewById<AppCompatTextView>(R.id.tvTitle)
        val tvDescription = dialogView.findViewById<AppCompatTextView>(R.id.tvCreateNewAlbum)

        btnCancel.text = getString(R.string.no_keep)
        btnYesDelete.text = getString(R.string.yes_discard)
        tvTitle.text = getString(R.string.discard_changes)
        tvDescription.text = getString(R.string.are_you_sure_you_want_to_discard_the_changes)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnYesDelete.setOnClickListener {
            dialog.dismiss()
            finish()
            backScreenAnimation()
        }
        dialog.show()
    }

    override fun onFilterSelected(photoFilter: PhotoFilter) {
        mPhotoEditor.setFilterEffect(photoFilter)
        this.photoFilter = photoFilter
    }

    override fun onToolSelected(toolType: ToolType) {
        when (toolType) {
            ToolType.SHAPE -> {
                mPhotoEditor.setBrushDrawingMode(true)
                mShapeBuilder = ShapeBuilder()
                mPhotoEditor.setShape(mShapeBuilder)
                showBottomSheetDialogFragment(mShapeBSFragment)
                binding.layoutControlOptions.tvToolName.text = getString(R.string.shape)
            }

            ToolType.TEXT -> {
                val textEditorDialogFragment = TextEditorDialogFragment.show(this)
                textEditorDialogFragment.setOnTextEditorListener(object :
                    TextEditorDialogFragment.TextEditorListener {
                    override fun onDone(inputText: String, colorCode: Int) {
                        val styleBuilder = TextStyleBuilder()
                        styleBuilder.withTextColor(colorCode)
                        mPhotoEditor.addText(inputText, styleBuilder)
                    }
                })
                binding.layoutControlOptions.tvToolName.text = getString(R.string.text)
            }

            ToolType.ERASER -> {
                mPhotoEditor.brushEraser()
                binding.layoutControlOptions.tvToolName.text = getString(R.string.eraser)
            }

            ToolType.FILTER -> {
                showFilter(true)
                binding.layoutControlOptions.tvToolName.text = getString(R.string.filter)
            }

            ToolType.EMOJI -> showBottomSheetDialogFragment(mEmojiBSFragment)
            ToolType.STICKER -> showBottomSheetDialogFragment(mStickerBSFragment)
            ToolType.CROP -> {
                cropSelectedMedia()
            }

            ToolType.BLUR -> {
                showBottomSheetDialogFragment(mBlurBSFragment)
                binding.layoutControlOptions.tvToolName.text = getString(R.string.blur)
            }

            ToolType.ROTATE -> {
                showBottomSheetDialogFragment(mRotateBSFragment)
                binding.layoutControlOptions.tvToolName.text = getString(R.string.rotate)
            }
        }
    }

    private fun cropSelectedMedia() {
        showLoading() // Show progress dialog

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Get source bitmap
                val sourceBitmap = withContext(Dispatchers.Main) {
                    mPhotoEditorView.source.drawable.toBitmap()
                }

                // 2. Apply transformations in background
                val transformedBitmap = RotateHelper.applyTransformations(
                    sourceBitmap, savedRotationCount, savedFlipHorizontal, savedFlipVertical
                )

                // 3. Apply blur if needed
                val finalBitmap = if (currentBlurRadius > 0) {
                    BlurHelper.applyBlur(
                        this@EditImageActivity, transformedBitmap, currentBlurRadius
                    )
                } else {
                    transformedBitmap
                }

                // 4. Save to temp file
                val tempFile = File.createTempFile("temp_crop", ".png", cacheDir).apply {
                    finalBitmap.compress(Bitmap.CompressFormat.PNG, 90, FileOutputStream(this))
                }

                // 5. Start crop activity on UI thread
                withContext(Dispatchers.Main) {
                    hideLoading()
                    startActivityForResult(
                        Intent(this@EditImageActivity, CropImageActivity::class.java).apply {
                            putExtra("uri", Uri.fromFile(tempFile))
                        }, REQUEST_CROP_IMAGE
                    )
                    nextScreenAnimation()
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(
                        this@EditImageActivity,
                        R.string.failed_to_prepare_image_for_cropping,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showBottomSheetDialogFragment(fragment: BottomSheetDialogFragment?) {
        if (fragment == null || fragment.isAdded) {
            return
        }
        fragment.show(supportFragmentManager, fragment.tag)
    }

    private fun showFilter(isVisible: Boolean) {
        mIsFilterVisible = isVisible
        mConstraintSet.clone(mRootView)

        val rvFilterId: Int = binding.rlConstraintTools.id
        if (isVisible) {
            mConstraintSet.clear(rvFilterId, ConstraintSet.START)
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START
            )
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END
            )
        } else {
            mConstraintSet.connect(
                rvFilterId, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.END
            )
            mConstraintSet.clear(rvFilterId, ConstraintSet.END)
        }

        val changeBounds = ChangeBounds()
        changeBounds.duration = 350
        changeBounds.interpolator = AnticipateOvershootInterpolator(1.0f)
        TransitionManager.beginDelayedTransition(mRootView, changeBounds)
        mConstraintSet.applyTo(mRootView)
    }

    override fun onBlurChanged(blurRadius: Int) {
        currentBlurRadius = blurRadius
        try {
            val sourceBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            val blurredBitmap = BlurHelper.applyBlur(this, sourceBitmap, blurRadius)
            mPhotoEditorView.source.setImageBitmap(blurredBitmap)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.failed_to_apply_blur), Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onBlurSaved(blurRadius: Int) {
        currentBlurRadius = blurRadius
        try {
            val sourceBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            val blurredBitmap = BlurHelper.applyBlur(this, sourceBitmap, blurRadius)
            mPhotoEditorView.source.setImageBitmap(blurredBitmap)
            mImgUndo.isEnabled = mPhotoEditor.isUndoAvailable
            mImgRedo.isEnabled = mPhotoEditor.isRedoAvailable
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.failed_to_save_blur), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBlurCancelled() {
        try {
            val sourceBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            mPhotoEditorView.source.setImageBitmap(sourceBitmap)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.failed_to_revert_blur), Toast.LENGTH_SHORT)
                .show()
        }
    }

    override fun onRotate() {
        rotationCount = (rotationCount + 1) % 4
        applyTransformations()
    }

    override fun onFlipHorizontal() {
        flipHorizontal = !flipHorizontal
        applyTransformations()
    }

    override fun onFlipVertical() {
        flipVertical = !flipVertical
        applyTransformations()
    }

    override fun onRotateSaved() {
        savedRotationCount = rotationCount
        savedFlipHorizontal = flipHorizontal
        savedFlipVertical = flipVertical
        mImgUndo.isEnabled = mPhotoEditor.isUndoAvailable
        mImgRedo.isEnabled = mPhotoEditor.isRedoAvailable
    }

    override fun onRotateCancelled() {
        rotationCount = savedRotationCount
        flipHorizontal = savedFlipHorizontal
        flipVertical = savedFlipVertical
        applyTransformations()
    }

    private fun applyTransformations() {
        try {
            val sourceBitmap = MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            val transformedBitmap = RotateHelper.applyTransformations(
                sourceBitmap, rotationCount, flipHorizontal, flipVertical
            )
            mPhotoEditorView.source.setImageBitmap(transformedBitmap)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(
                this, getString(R.string.failed_to_apply_transformation), Toast.LENGTH_SHORT
            ).show()
        }
    }

    companion object {
        const val PINCH_TEXT_SCALABLE_INTENT_KEY = "PINCH_TEXT_SCALABLE"
        private const val REQUEST_CROP_IMAGE = 101
    }
}