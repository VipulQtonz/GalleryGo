package com.photogallery.activity

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.adapter.EditOptionsAdapter
import com.photogallery.adapter.ImagePagerAdapter
import com.photogallery.databinding.ActivityPhotoViewBinding
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.db.model.MediaDataEntity
import com.photogallery.db.model.MediaFavoriteData
import com.photogallery.model.EditOptionItems
import com.photogallery.model.MediaData
import com.photogallery.photoEditor.photoEditing.EditImageActivity
import com.photogallery.utils.Const.SWIPE_THRESHOLD
import com.photogallery.utils.Const.SWIPE_VELOCITY_THRESHOLD
import com.photogallery.utils.formatImageFileSize
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class PhotoViewActivity : BaseActivity<ActivityPhotoViewBinding>() {
    private val rotationMap = mutableMapOf<Int, Float>()
    private var isSlideshowRunning = false
    private var slideshowHandler: android.os.Handler? = null
    private var slideshowRunnable: Runnable? = null
    private lateinit var mediaList: MutableList<MediaData>
    private var fromAlbum: Boolean = false
    private var fromSearch: Boolean = false
    private var clickCount = 0
    private lateinit var gestureDetector: GestureDetectorCompat
    private var isDragging = false
    private var startY = 0f
    private var lastY = 0f
    private val dismissThreshold = 0.4f // 40% of screen height to dismiss
    private var screenHeight = 0f
    private val minScale = 0.8f
    private var isSwipeDismiss = false

    private val addToAlbumLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshMediaList()
        }
    }

    override fun getViewBinding(): ActivityPhotoViewBinding {
        return ActivityPhotoViewBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        mediaList = MyApplication.mediaList.toMutableList()
        fromAlbum = intent.getBooleanExtra("fromAlbum", false)
        fromSearch = intent.getBooleanExtra("FromSearch", false)
        val selectedPosition = intent.getIntExtra("selected_position", 0)
        binding.viewPager.apply {
            offscreenPageLimit = 1
            adapter = ImagePagerAdapter(
                this@PhotoViewActivity,
                mediaList,
                rotationMap,
                false,
                ePreferences
            ).apply {
                setHasStableIds(true)
            }
            setCurrentItem(selectedPosition, false)
        }
        val ifViewFirst = ePreferences.getBoolean("ifViewFirst", true)
        if (ifViewFirst) {
            binding.llSuggestionLayout.visibility = View.VISIBLE
        } else {
            binding.llSuggestionLayout.visibility = View.GONE
        }

        binding.rwEditOptions.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        val editOptions = listOf(
            EditOptionItems(R.drawable.ic_edit, getString(R.string.edit)),
            EditOptionItems(R.drawable.ic_delete, getString(R.string.delete)),
            EditOptionItems(R.drawable.ic_lock, getString(R.string.lock)),
            EditOptionItems(R.drawable.ic_add_to_albim, getString(R.string.add_to_album)),
            EditOptionItems(R.drawable.ic_move, getString(R.string.move_to_album)),
            EditOptionItems(R.drawable.ic_gallery, getString(R.string.set_as_wallpaper)),
            EditOptionItems(R.drawable.ic_rename, getString(R.string.rename)),
            EditOptionItems(R.drawable.ic_slidshow, getString(R.string.slideshow))
        )
        binding.rwEditOptions.adapter = EditOptionsAdapter(editOptions).apply {
            setOnItemClickListener { position ->
                when (position) {
                    0 -> editSelectedMedia()
                    1 -> deleteSelectedMedia()
                    2 -> showLockPhotoDialog()
                    3 -> addToAlbumSelected()
                    4 -> addToAlbumSelected()
                    5 -> setAsWallPaper()
                    6 -> renameImage()
                    7 -> slideshowSelected()
                }
            }
        }
        setFavoriteIcon(selectedPosition)
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                setFavoriteIcon(position)
            }
        })
        setupGestureDetector()
        screenHeight = resources.displayMetrics.heightPixels.toFloat()
    }

    private fun setupGestureDetector() {
        gestureDetector =
            GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    if (e1 == null) return false

                    val diffY = e2.y - e1.y
                    val diffX = e2.x - e1.x

                    if (diffY > SWIPE_THRESHOLD && abs(diffY) > abs(diffX) && velocityY > SWIPE_VELOCITY_THRESHOLD) {
                        dismissWithAnimation()
                        return true
                    }
                    return false
                }
            })
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null) return super.dispatchTouchEvent(ev)

        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.y
                lastY = startY
                isDragging = false
            }

            MotionEvent.ACTION_MOVE -> {
                val diffY = ev.y - startY
                val diffX = ev.x - ev.rawX + ev.x
                if (abs(diffY) > abs(diffX) && diffY > 0 && !isDragging) {
                    isDragging = true
                }
                if (isDragging) {
                    val progress =
                        (ev.y - startY).coerceAtLeast(0f) / (screenHeight * dismissThreshold)
                    updateDragState(progress.coerceIn(0f, 1f))
                    lastY = ev.y
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    val progress =
                        (lastY - startY).coerceAtLeast(0f) / (screenHeight * dismissThreshold)
                    if (progress >= 1f) {
                        dismissWithAnimation()
                    } else {
                        snapBack()
                    }
                    return true
                }
            }
        }

        if (gestureDetector.onTouchEvent(ev)) {
            return true
        }

        return super.dispatchTouchEvent(ev)
    }

    private fun updateDragState(progress: Float) {
        val scale = 1f - (1f - minScale) * progress
        binding.root.scaleX = scale
        binding.root.scaleY = scale

        binding.root.translationY = progress * screenHeight * 0.5f
    }

    private fun snapBack() {
        val animator = ValueAnimator.ofFloat(binding.root.translationY, 0f).apply {
            duration = 300
            addUpdateListener { animation ->
                val value = animation.animatedValue as Float
                binding.root.translationY = value
                val progress = value / (screenHeight * 0.5f)
                val scale = 1f - (1f - minScale) * progress
                binding.root.scaleX = scale
                binding.root.scaleY = scale
            }
        }
        animator.start()
    }

    private fun dismissWithAnimation() {
        isSwipeDismiss = true

        // Start from current values
        val startTranslationY = binding.root.translationY
        val startScaleX = binding.root.scaleX
        val startAlpha = binding.root.alpha

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            interpolator = DecelerateInterpolator()
            duration = 300
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                // Animate translationY to screenHeight (or slightly beyond to ensure it disappears)
                binding.root.translationY =
                    startTranslationY + (screenHeight - startTranslationY) * progress
                // Animate scale down to 0.7f (or any small value to ensure it shrinks)
                val targetScale = 0.7f
                val scale = startScaleX - (startScaleX - targetScale) * progress
                binding.root.scaleX = scale
                binding.root.scaleY = scale
                binding.root.scaleY = scale
                // Fade out
                binding.root.alpha = startAlpha - (startAlpha - 0f) * progress
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    binding.root.post {
                        finish()
                        overridePendingTransition(0, 0)
                    }
                }
            })
        }
        animator.start()
    }

    private fun setFavoriteIcon(position: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (position in mediaList.indices) {
                    val mediaData = mediaList[position]
                    val database = PhotoGalleryDatabase.getDatabase(this@PhotoViewActivity)
                    val existingFavorite = database.photoGalleryDao().getFavoriteById(mediaData.id)

                    runOnUiThread {
                        binding.ivFavorite.setImageResource(
                            if (existingFavorite != null) R.drawable.ic_favourites_pink
                            else R.drawable.ic_favourites_inactive
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@PhotoViewActivity,
                        getString(R.string.error_checking_favorite_status),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun toggleFavorite(media: MediaData, position: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = PhotoGalleryDatabase.getDatabase(this@PhotoViewActivity)
                val existingFavorite = database.photoGalleryDao().getFavoriteById(media.id)

                if (existingFavorite != null) {
                    runOnUiThread {
                        showRemoveFavoriteDialog(database, existingFavorite, position)
                    }
                } else {
                    val favoriteData = MediaFavoriteData(
                        id = media.id,
                        originalPath = media.path,
                        name = media.name,
                        dateTaken = media.dateTaken,
                        duration = media.duration,
                        isFavorite = true,
                        isVideo = false,
                        uri = media.uri,
                    )
                    database.photoGalleryDao().insertFavorite(favoriteData)
                    runOnUiThread {
                        binding.ivFavorite.setImageResource(R.drawable.ic_favourites_pink)
                    }
                    setFavoriteIcon(position)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    Toast.makeText(
                        this@PhotoViewActivity,
                        getString(R.string.error_updating_favorite),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showRemoveFavoriteDialog(
        database: PhotoGalleryDatabase, favorite: MediaFavoriteData, position: Int
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_remove_favorites, null)

        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogView)
            setCancelable(true)
            window?.apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
        }

        val btnRemoveFavorite = dialogView.findViewById<AppCompatButton>(R.id.btnRemoveFromFavorite)
        val btnCancel = dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
        val ivClose = dialogView.findViewById<AppCompatImageView>(R.id.ivClose)

        btnRemoveFavorite.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                database.photoGalleryDao().deleteFavorite(favorite)
                runOnUiThread {
                    binding.ivFavorite.setImageResource(R.drawable.ic_favourites_inactive)
                    setFavoriteIcon(position)
                }
            }
            dialog.dismiss()
        }

        ivClose.setOnClickListener {
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showLockPhotoDialog(
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_remove_favorites, null)
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogView)
            setCancelable(true)
            window?.apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
        }

        val btnLock = dialogView.findViewById<AppCompatButton>(R.id.btnRemoveFromFavorite)
        val btnCancel = dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
        val ivClose = dialogView.findViewById<AppCompatImageView>(R.id.ivClose)
        val tvTitle = dialogView.findViewById<AppCompatTextView>(R.id.tvTitle)
        val tvDescription = dialogView.findViewById<AppCompatTextView>(R.id.tvDescription)

        btnLock.text = getString(R.string.lock)
        tvTitle.text = getString(R.string.lock_photo)
        tvDescription.text = getString(R.string.are_you_sure_want_to_lock_this_photo)
        btnLock.setOnClickListener {
            showLoading()
            lockSelectedMedia()
            dialog.dismiss()
        }

        ivClose.setOnClickListener {
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    override fun addListener() {
        binding.ivSettings.setOnClickListener {
            onBackPressedDispatcher()
        }
        binding.ivRotate.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            val currentRotation = rotationMap.getOrDefault(currentPosition, 0f)
            val newRotation = (currentRotation + 90f) % 360f
            rotationMap[currentPosition] = newRotation
            (binding.viewPager.adapter as ImagePagerAdapter).notifyItemChanged(currentPosition)
        }

        binding.ivInfo.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition in mediaList.indices) {
                val mediaData = mediaList[currentPosition]
                showImageInfoBottomSheet(mediaData.path)
            } else {
                Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.ivFavorite.setOnClickListener {
            val currentPosition = binding.viewPager.currentItem
            if (currentPosition in mediaList.indices) {
                val mediaData = mediaList[currentPosition]
                toggleFavorite(mediaData, currentPosition)
            }
        }

        binding.btnGotIt.setOnClickListener {
            clickCount += 1
            runFunction(clickCount)
        }
    }

    private fun showImageInfoBottomSheet(imagePath: String) {
        val file = File(imagePath)
        if (!file.exists()) {
            Toast.makeText(this, getString(R.string.file_not_found), Toast.LENGTH_LONG).show()
            return
        }

        val bottomSheetDialog = BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.show_image_info_dialog, null)
        bottomSheetDialog.setContentView(dialogView)

        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        val name = file.name
        val lastModified = SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss",
            Locale.getDefault()
        ).format(Date(file.lastModified()))
        val sizeFormatted = formatImageFileSize(file.length())
        val dimensions = "${bitmap.width} x ${bitmap.height}"
        val fullPath = file.absolutePath

        dialogView.findViewById<TextView>(R.id.tvImageFileName).text = name
        dialogView.findViewById<TextView>(R.id.tvImageFileTime).text = lastModified
        dialogView.findViewById<TextView>(R.id.tvImageFileDimensions).text = dimensions
        dialogView.findViewById<TextView>(R.id.tvImageFileSize).text = sizeFormatted
        dialogView.findViewById<TextView>(R.id.tvImagePath).text = fullPath

        dialogView.findViewById<ImageView>(R.id.ivCancel).setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    private fun runFunction(i: Int) {
        if (i == 1) {
            binding.ivIllustrator.setImageResource(R.drawable.ic_double_tap_to_zoom)
            binding.tvTitle.text = getString(R.string.double_tap_to_zoom)
            binding.tvDescription.text =
                getString(R.string.quickly_zoom_in_with_a_double_tap_on_the_screen)
        } else {
            binding.llSuggestionLayout.visibility = View.GONE
            ePreferences.putBoolean("ifViewFirst", false)
            if (ePreferences.getBoolean("isFirstTimePhotoViewRotate", true)) {
                setupTooltip(
                    this,
                    binding.ivRotate, getString(R.string.rotate_image),
                    ArrowOrientation.BOTTOM,
                    ePreferences,
                    "isFirstTimePhotoViewRotate"
                ) {
                    if (ePreferences.getBoolean("isFirstTimePhotoViewFavorite", true)) {
                        setupTooltip(
                            this,
                            binding.ivFavorite, getString(R.string.click_to_add_to_favorites),
                            ArrowOrientation.BOTTOM,
                            ePreferences,
                            "isFirstTimePhotoViewFavorite"
                        )
                        {
                            if (ePreferences.getBoolean("isFirstTimePhotoViewInfo", true)) {
                                setupTooltip(
                                    this,
                                    binding.ivInfo,
                                    getString(R.string.click_to_view_image_information),
                                    ArrowOrientation.BOTTOM,
                                    ePreferences,
                                    "isFirstTimePhotoViewInfo"
                                )
                                {
                                    if (ePreferences.getBoolean(
                                            "isFirstTimePhotoRecyclerView",
                                            true
                                        )
                                    ) {
                                        setupTooltip(
                                            this,
                                            binding.rwEditOptions,
                                            getString(R.string.click_to_view_image_information),
                                            ArrowOrientation.BOTTOM,
                                            ePreferences,
                                            "isFirstTimePhotoRecyclerView"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getSelectedMedia(): List<MediaData> {
        val list = mutableListOf<MediaData>()
        val currentPosition = binding.viewPager.currentItem
        val media = mediaList[currentPosition]
        list.add(media)
        return list
    }

    private fun editSelectedMedia() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition !in mediaList.indices) {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedMedia = mediaList[currentPosition]
        if (!isUriReadable(selectedMedia.uri)) {
            Toast.makeText(this, getString(R.string.invalid_image_uri), Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(this, EditImageActivity::class.java).apply {
            data = selectedMedia.uri
            putExtra("uri", selectedMedia.uri)
            putExtra("path", selectedMedia.path)
        }

        startActivity(intent)
        nextScreenAnimation()
    }

    private fun isUriReadable(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { it.close() }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteSelectedMedia() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition !in mediaList.indices) {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedMedia = mediaList[currentPosition]
        val dialogView = layoutInflater.inflate(R.layout.dialog_move_to_recyclebin, null)
        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDescription)
        val btnMoveToBeen = dialogView.findViewById<Button>(R.id.btnMoveToBeen)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnApply)
        val ivClose = dialogView.findViewById<ImageView>(R.id.ivClose)

        tvTitle.text = getString(R.string.delete_photo)
        tvDescription.text = getString(R.string.are_you_sure_you_want_to_delete_photo)

        btnMoveToBeen.setOnClickListener {
            showLoading()
            dialog.dismiss()
            CoroutineScope(Dispatchers.IO).launch {
                val database = PhotoGalleryDatabase.getDatabase(this@PhotoViewActivity)
                val file = File(selectedMedia.path)
                val newFile = moveToDeletedFolder(file)

                if (newFile != null) {
                    try {
                        // Remove from favorites if it exists
                        database.photoGalleryDao().getFavoriteById(selectedMedia.id)
                            ?.let { favorite ->
                                database.photoGalleryDao().deleteFavorite(favorite)
                            }

                        // Insert into deleted items
                        val mediaDataEntity = MediaDataEntity(
                            id = selectedMedia.id,
                            name = selectedMedia.name,
                            originalPath = newFile.path,
                            recyclePath = newFile.absolutePath,
                            uri = selectedMedia.uri,
                            dateTaken = selectedMedia.dateTaken,
                            isVideo = selectedMedia.isVideo,
                            duration = selectedMedia.duration,
                            deletedAt = System.currentTimeMillis()
                        )
                        database.photoGalleryDao().insertDeletedMedia(mediaDataEntity)

                        // Notify MediaStore of the deletion
                        val contentResolver = this@PhotoViewActivity.contentResolver
                        try {
                            val uri = selectedMedia.uri
                            contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            // Optionally, use MediaScannerConnection to scan the new file location
                            MediaScannerConnection.scanFile(
                                this@PhotoViewActivity,
                                arrayOf(newFile.absolutePath),
                                arrayOf(if (selectedMedia.isVideo) "video/*" else "image/*")
                            ) { _, _ ->
                                // Scan complete, no action needed
                            }
                        }

                        // Update UI
                        withContext(Dispatchers.Main) {
                            hideLoading()
                            // Remove from current list
                            mediaList.removeAt(currentPosition)
                            if (fromAlbum) {
                                if (MyApplication.selectedAlbumImages.isNotEmpty()) {
                                    (MyApplication.selectedAlbumImages as MutableList).removeAt(
                                        currentPosition
                                    )
                                }
                            }
                            // Replace notifyFileDeleted with MediaStore notification
                            MyApplication.instance.notifyFileDeleted(selectedMedia.uri)
                            MyApplication.isPhotoFetchReload = true
                            // Update adapter
                            (binding.viewPager.adapter as ImagePagerAdapter).notifyDataSetChanged()

                            // Close activity if no more items
                            if (mediaList.isEmpty()) {
                                backScreenAnimation()
                                finish()
                            } else {
                                val newPosition = if (currentPosition >= mediaList.size) {
                                    mediaList.size - 1
                                } else {
                                    currentPosition
                                }
                                binding.viewPager.setCurrentItem(newPosition, false)
                            }
                        }
                    } catch (e: Exception) {
                        hideLoading()
                        e.printStackTrace()
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@PhotoViewActivity,
                                getString(R.string.error_deleting_photo),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        Toast.makeText(
                            this@PhotoViewActivity,
                            getString(R.string.error_moving_to_recycle_bin),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                dialog.dismiss()
            }
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        ivClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

//    private fun lockSelectedMedia() {
//        val currentPosition = binding.viewPager.currentItem
//        if (currentPosition !in mediaList.indices) {
//            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
//            return
//        }
//
//        val media = mediaList[currentPosition]
//        if (media.path.isEmpty()) return
//
//        CoroutineScope(Dispatchers.IO).launch {
//            val lockedDir = File(
//                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
//                "LockedMedia"
//            ).apply {
//                if (!exists()) mkdirs()
//            }
//
//            val database = PhotoGalleryDatabase.getDatabase(this@PhotoViewActivity)
//            val originalFile = File(media.path)
//
//            if (!originalFile.exists()) {
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(this@PhotoViewActivity, "File not found", Toast.LENGTH_SHORT).show()
//                }
//                return@launch
//            }
//
//            try {
//                // 1. First encrypt the file
//                val encryptedFile = File(lockedDir, originalFile.name + ".lockimg")
//                FileEncryptor.encryptFile(originalFile, encryptedFile)
//
//                // 2. Then remove from favorites if exists
//                database.photoGalleryDao().getFavoriteById(media.id)?.let { favorite ->
//                    database.photoGalleryDao().deleteFavorite(favorite)
//                }
//
//                // 3. Delete original file only after successful encryption
//                if (originalFile.exists()) {
//                    if (!originalFile.delete()) {
//                        Log.e("PhotoViewActivity", "Failed to delete original file")
//                    }
//                }
//
//                // 4. Update MediaStore
//                try {
//                    contentResolver.delete(media.uri, null, null)
//                } catch (e: Exception) {
//                    MediaScannerConnection.scanFile(
//                        this@PhotoViewActivity,
//                        arrayOf(lockedDir.absolutePath),
//                        arrayOf(if (media.isVideo) "video/*" else "image/*")
//                    ) { _, _ -> }
//                }
//
//                // 5. Update UI and data
//                withContext(Dispatchers.Main) {
//                    // Remove from current list
//                    mediaList.removeAt(currentPosition)
//
//                    if (fromAlbum) {
//                        (MyApplication.selectedAlbumImages as? MutableList)?.removeAt(currentPosition)
//                    }
//
//                    MyApplication.instance.notifyFileDeleted(media.uri)
//                    MyApplication.isPhotoFetchReload = true
//
//                    // Update adapter
//                    (binding.viewPager.adapter as? ImagePagerAdapter)?.notifyDataSetChanged()
//
//                    if (mediaList.isEmpty()) {
//                        finish()
//                    } else {
//                        val newPosition = currentPosition.coerceAtMost(mediaList.size - 1)
//                        binding.viewPager.setCurrentItem(newPosition, false)
//                    }
//                }
//            } catch (e: Exception) {
//                withContext(Dispatchers.Main) {
//                    Toast.makeText(
//                        this@PhotoViewActivity,
//                        "Failed to lock media: ${e.message}",
//                        Toast.LENGTH_SHORT
//                    ).show()
//                }
//            }
//        }
//    }

    private fun lockSelectedMedia() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition !in mediaList.indices) {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val media = mediaList[currentPosition]
        if (media.path.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            val lockedDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "LockedMedia"
            ).apply {
                if (!exists()) mkdirs()
            }

            val database = PhotoGalleryDatabase.getDatabase(this@PhotoViewActivity)
            val originalFile = File(media.path)

            if (!originalFile.exists()) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@PhotoViewActivity,
                        getString(R.string.file_not_found),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return@launch
            }

            try {
                // 1. Move and rename the file
                val newFile = File(lockedDir, originalFile.name + ".lockimg")
                originalFile.renameTo(newFile)

                // 2. Remove from favorites if exists
                database.photoGalleryDao().getFavoriteById(media.id)?.let { favorite ->
                    database.photoGalleryDao().deleteFavorite(favorite)
                }

                // 3. Update MediaStore
                try {
                    contentResolver.delete(media.uri, null, null)
                } catch (_: Exception) {
                    MediaScannerConnection.scanFile(
                        this@PhotoViewActivity,
                        arrayOf(lockedDir.absolutePath),
                        arrayOf(if (media.isVideo) "video/*" else "image/*")
                    ) { _, _ -> }
                }

                // 4. Update UI and data
                withContext(Dispatchers.Main) {
                    hideLoading()
                    // Remove from current list
                    mediaList.removeAt(currentPosition)

                    if (fromAlbum) {
                        (MyApplication.selectedAlbumImages as? MutableList)?.removeAt(
                            currentPosition
                        )
                    }

                    MyApplication.instance.notifyFileDeleted(media.uri)
                    MyApplication.isPhotoFetchReload = true

                    // Update adapter
                    (binding.viewPager.adapter as? ImagePagerAdapter)?.notifyDataSetChanged()

                    if (mediaList.isEmpty()) {
                        backScreenAnimation()
                        finish()
                    } else {
                        val newPosition = currentPosition.coerceAtMost(mediaList.size - 1)
                        binding.viewPager.setCurrentItem(newPosition, false)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoading()
                    Toast.makeText(
                        this@PhotoViewActivity,
                        getString(R.string.failed_to_lock_media, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun addToAlbumSelected() {
        val currentPosition = binding.viewPager.currentItem

        if (currentPosition !in mediaList.indices) {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val media = mediaList[currentPosition]

        if (media.path.isEmpty() || !File(media.path).exists()) {
            Toast.makeText(this, getString(R.string.invalid_image_path), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            MyApplication.instance.setSelectedMediaAndAction(getSelectedMedia(), "MOVE")
            val intent = Intent(this, AddToAlbumActivity::class.java)
            addToAlbumLauncher.launch(intent)

        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }
    }

    private fun renameImage() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition !in mediaList.indices) {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedMedia = mediaList[currentPosition]
        val originalFile = File(selectedMedia.path)
        val originalName = originalFile.nameWithoutExtension
        val extension = originalFile.extension

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_new_album, null)
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogView)
            setCancelable(true)
            window?.apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
        }

        val tvTitle = dialogView.findViewById<AppCompatTextView>(R.id.tvTitle)
        val etName = dialogView.findViewById<AppCompatEditText>(R.id.edtCreateNewAlbum)
        val btnCancel = dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
        val btnCreate = dialogView.findViewById<AppCompatButton>(R.id.btnCreateNewAlbum)
        val ivClear = dialogView.findViewById<ImageView>(R.id.ivClear)

        // Update UI elements for rename operation
        ivClear.visibility = View.VISIBLE
        tvTitle.text = getString(R.string.rename)
        etName.setText(originalName)
        btnCreate.text = getString(R.string.save)
        etName.setSelection(etName.text?.trim()?.length ?: 0)

        ivClear.setOnClickListener {
            etName.text?.clear()
        }

        btnCreate.setOnClickListener {
            val newName = etName.text.toString().trim()
            if (newName.isEmpty()) {
                Toast.makeText(this, getString(R.string.please_enter_name), Toast.LENGTH_SHORT)
                    .show()
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val database = PhotoGalleryDatabase.getDatabase(this@PhotoViewActivity)
                    val newFile = File(originalFile.parent, "$newName.$extension")

                    if (originalFile.renameTo(newFile)) {
                        // Check if this media is in favorites
                        val existingFavorite =
                            database.photoGalleryDao().getFavoriteById(selectedMedia.id)

                        // Update the media data
                        val updatedMedia = selectedMedia.copy(
                            path = newFile.absolutePath, name = newFile.name
                        )

                        // Update in database if it's a favorite
                        existingFavorite?.let { favorite ->
                            val updatedFavorite = favorite.copy(
                                originalPath = newFile.absolutePath, name = newFile.name
                            )
                            database.photoGalleryDao().updateFavorite(updatedFavorite)
                        }

                        // Update in the current list
                        withContext(Dispatchers.Main) {
                            MyApplication.isPhotoFetchReload = true
                            mediaList[currentPosition] = updatedMedia
                            (binding.viewPager.adapter as ImagePagerAdapter).notifyItemChanged(
                                currentPosition
                            )
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@PhotoViewActivity,
                                getString(R.string.error_renaming_file),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@PhotoViewActivity,
                            getString(R.string.error_renaming_file),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                dialog.dismiss()
            }
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun setAsWallPaper() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition !in mediaList.indices) {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val media = mediaList[currentPosition]
        if (media.path.isEmpty() || !File(media.path).exists()) {
            Toast.makeText(this, getString(R.string.invalid_image_path), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val intent = Intent(this, SetAsActivity::class.java)
            intent.putExtra("SetAsImage", media.uri)
            startActivity(intent)
            nextScreenAnimation()

        } catch (e: ActivityNotFoundException) {
            e.printStackTrace()
        }

    }

    private fun slideshowSelected() {
        if (isSlideshowRunning) {
            stopSlideshow()
        } else {
            startSlideshow()
        }
    }

    private fun startSlideshow() {
        isSlideshowRunning = true
        slideshowHandler = android.os.Handler(mainLooper)
        slideshowRunnable = object : Runnable {
            override fun run() {
                val nextPosition = (binding.viewPager.currentItem + 1) % mediaList.size
                binding.viewPager.setCurrentItem(nextPosition, true)
                slideshowHandler?.postDelayed(this, 2000) // 2 seconds delay
            }
        }
        slideshowHandler?.post(slideshowRunnable!!)
    }

    private fun stopSlideshow() {
        isSlideshowRunning = false
        slideshowHandler?.removeCallbacks(slideshowRunnable!!)
        slideshowHandler = null
        slideshowRunnable = null
    }

    private fun refreshMediaList() {
        val updatedMediaList = MyApplication.mediaList.toMutableList()

        if (updatedMediaList.isEmpty()) {
            backScreenAnimation()
            finish()
            return
        }

        // Store current position before updating
        val currentPosition = binding.viewPager.currentItem
        val currentMediaId =
            if (currentPosition in mediaList.indices) mediaList[currentPosition].id else -1

        // Update the local list
        mediaList.clear()
        mediaList.addAll(updatedMediaList)

        // Update the adapter
        (binding.viewPager.adapter as ImagePagerAdapter).updateMediaList(updatedMediaList)

        // Find the position of the previously viewed media (if it still exists)
        val newPosition = if (currentMediaId.toInt() != -1) {
            updatedMediaList.indexOfFirst { it.id == currentMediaId }.takeIf { it != -1 }
                ?: if (currentPosition < updatedMediaList.size) currentPosition else updatedMediaList.size - 1
        } else {
            0
        }

        binding.viewPager.setCurrentItem(newPosition, false)
        setFavoriteIcon(newPosition)
    }
}