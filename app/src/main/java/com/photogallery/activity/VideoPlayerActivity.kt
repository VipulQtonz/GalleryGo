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
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.adapter.EditOptionsAdapter
import com.photogallery.adapter.ImagePagerAdapter
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityVideoPlayerBinding
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.db.model.MediaDataEntity
import com.photogallery.db.model.MediaFavoriteData
import com.photogallery.model.EditOptionItems
import com.photogallery.model.MediaData
import com.photogallery.utils.Const.SWIPE_THRESHOLD
import com.photogallery.utils.Const.SWIPE_VELOCITY_THRESHOLD
import com.photogallery.utils.formatImageFileSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class VideoPlayerActivity : BaseActivity<ActivityVideoPlayerBinding>() {
    private val rotationMap = mutableMapOf<Int, Float>()
    private lateinit var mediaList: MutableList<MediaData>
    private var isLoopingEnabled = false
    private lateinit var imagePagerAdapter: ImagePagerAdapter
    private lateinit var editOptionsAdapter: EditOptionsAdapter
    private lateinit var gestureDetector: GestureDetectorCompat
    private var isDragging = false
    private var startY = 0f
    private var lastY = 0f
    private val dismissThreshold = 0.4f
    private var screenHeight = 0f
    private val minScale = 0.8f
    private var isSwipeDismiss = false
    private lateinit var editOptions: MutableList<EditOptionItems>

    private val addToAlbumLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshMediaList()
        }
    }

    override fun getViewBinding(): ActivityVideoPlayerBinding {
        return ActivityVideoPlayerBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        isLoopingEnabled = ePreferences.getBoolean("IsLoop", false)

        editOptions = mutableListOf(
            EditOptionItems(R.drawable.ic_share_new, getString(R.string.share)),
            EditOptionItems(R.drawable.ic_delete, getString(R.string.delete)),
            EditOptionItems(
                if (isLoopingEnabled) R.drawable.ic_loop_video_on else R.drawable.ic_loop_video_off,
                getString(if (isLoopingEnabled) R.string.loop_video_off else R.string.loop_video_on)
            ),
            EditOptionItems(R.drawable.ic_lock, getString(R.string.lock)),
            EditOptionItems(R.drawable.ic_add_to_albim, getString(R.string.add_to_album)),
            EditOptionItems(R.drawable.ic_move, getString(R.string.move_to_album))
        )

        editOptions[2] = if (isLoopingEnabled) {
            EditOptionItems(R.drawable.ic_loop_video_on, getString(R.string.loop_video_off))
        } else {
            EditOptionItems(R.drawable.ic_loop_video_off, getString(R.string.loop_video_on))
        }

        mediaList = MyApplication.mediaList.toMutableList()
        val selectedPosition = intent.getIntExtra("selected_position", 0)
        binding.viewPager.adapter = ImagePagerAdapter(
            this@VideoPlayerActivity,
            mediaList,
            rotationMap,
            isLoopingEnabled,
            ePreferences
        )
        binding.viewPager.setCurrentItem(selectedPosition, false)
        imagePagerAdapter =
            ImagePagerAdapter(
                this@VideoPlayerActivity,
                mediaList,
                rotationMap,
                isLoopingEnabled,
                ePreferences
            )

        binding.rwEditOptions.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        editOptionsAdapter = EditOptionsAdapter(editOptions).apply {
            setOnItemClickListener { position ->
                when (position) {
                    0 -> shareCurrentMedia()
                    1 -> deleteSelectedMedia()
                    2 -> changeVideoLoopState()
                    3 -> showLockPhotoDialog()
                    4 -> addToAlbumSelected()
                    5 -> addToAlbumSelected()
                }
            }
        }
        binding.rwEditOptions.adapter = editOptionsAdapter
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
                    e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float
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

        val startTranslationY = binding.root.translationY
        val startScaleX = binding.root.scaleX
        val startAlpha = binding.root.alpha

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            interpolator = DecelerateInterpolator()
            duration = 300
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                binding.root.translationY =
                    startTranslationY + (screenHeight - startTranslationY) * progress
                val targetScale = 0.7f
                val scale = startScaleX - (startScaleX - targetScale) * progress
                binding.root.scaleX = scale
                binding.root.scaleY = scale
                binding.root.scaleY = scale
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
                    val database = PhotoGalleryDatabase.getDatabase(this@VideoPlayerActivity)
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
                        this@VideoPlayerActivity,
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
                val database = PhotoGalleryDatabase.getDatabase(this@VideoPlayerActivity)
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
                        isFavorite = media.isFavorite,
                        isVideo = media.isVideo,
                        uri = media.uri
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
                        this@VideoPlayerActivity,
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

    private fun changeVideoLoopState() {
        val newState = !isLoopingEnabled // Flip current global state
        isLoopingEnabled = newState

        if (newState) {
            editOptions[2] =
                EditOptionItems(R.drawable.ic_loop_video_on, getString(R.string.loop_video_off))
        } else {
            editOptions[2] =
                EditOptionItems(R.drawable.ic_loop_video_off, getString(R.string.loop_video_on))
        }
        ePreferences.putBoolean("IsLoop", isLoopingEnabled)
        (binding.viewPager.adapter as? ImagePagerAdapter)?.updateLoopingForAllItems(newState)
        editOptionsAdapter.notifyItemChanged(2)
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
        tvTitle.text = getString(R.string.lock_video)
        tvDescription.text = getString(R.string.are_you_sure_want_to_lock_this_video)
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
    }

    private fun getSelectedMedia(): List<MediaData> {
        val list = mutableListOf<MediaData>()
        val currentPosition = binding.viewPager.currentItem
        val media = mediaList[currentPosition]
        list.add(media)
        return list
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
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

        val name = file.name
        val lastModified = SimpleDateFormat(
            "dd/MM/yyyy HH:mm:ss", Locale.getDefault()
        ).format(Date(file.lastModified()))
        val sizeFormatted = formatImageFileSize(file.length())
        val fullPath = file.absolutePath

        val currentPosition = binding.viewPager.currentItem
        val isVideo = if (currentPosition in mediaList.indices) {
            mediaList[currentPosition].isVideo
        } else {
            false
        }

        val dimensions = if (isVideo) {
            val duration = if (currentPosition in mediaList.indices) {
                formatDuration(mediaList[currentPosition].duration)
            } else {
                "N/A"
            }
            "$duration"
        } else {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                "${bitmap.width} x ${bitmap.height}"
            } else {
                "N/A"
            }
        }
        dialogView.findViewById<TextView>(R.id.tvImageFileDimensionsText).text =
            getString(R.string.duration)
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

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)

        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun shareCurrentMedia() {
        val currentPosition = binding.viewPager.currentItem
        if (currentPosition !in mediaList.indices) {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedMedia = mediaList[currentPosition]
        val uri = selectedMedia.uri
        if (!isUriReadable(uri)) {
            Toast.makeText(this, getString(R.string.invalid_video_uri), Toast.LENGTH_SHORT).show()
            return
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(shareIntent, getString(R.string.share_video_via))
        startActivity(chooser)
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
                val database = PhotoGalleryDatabase.getDatabase(this@VideoPlayerActivity)
                val file = File(selectedMedia.path)
                val newFile = moveToDeletedFolder(file)

                if (newFile != null) {
                    try {
                        database.photoGalleryDao().getFavoriteById(selectedMedia.id)
                            ?.let { favorite ->
                                database.photoGalleryDao().deleteFavorite(favorite)
                            }
                        MyApplication.isVideoFetchReload = true
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


                        val contentResolver = this@VideoPlayerActivity.contentResolver
                        try {
                            val uri = selectedMedia.uri
                            contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            MediaScannerConnection.scanFile(
                                this@VideoPlayerActivity,
                                arrayOf(newFile.absolutePath),
                                arrayOf(if (selectedMedia.isVideo) "video/*" else "image/*")
                            ) { _, _ ->
                            }
                        }
                        withContext(Dispatchers.Main) {
                            mediaList.removeAt(currentPosition)
                            hideLoading()
                            (binding.viewPager.adapter as ImagePagerAdapter).notifyDataSetChanged()
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
                                this@VideoPlayerActivity,
                                getString(R.string.error_deleting_photo),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } else {
                    hideLoading()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@VideoPlayerActivity,
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

    private fun lockSelectedMedia() {
        val currentPosition = binding.viewPager.currentItem
        val media = mediaList[currentPosition]
        if (currentPosition !in mediaList.indices) {
            Toast.makeText(this, getString(R.string.no_image_selected), Toast.LENGTH_SHORT).show()
            return
        }

        if (media.path.isEmpty()) return
        CoroutineScope(Dispatchers.IO).launch {
            val lockedDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "LockedMedia"
            )
            if (!lockedDir.exists()) lockedDir.mkdirs()
            val originalFile = File(media.path)
            val newFile = File(lockedDir, originalFile.name + ".lockimg")

            try {
                originalFile.renameTo(newFile)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val contentResolver = this@VideoPlayerActivity.contentResolver
            try {
                val uri = media.uri
                contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
                MediaScannerConnection.scanFile(
                    this@VideoPlayerActivity,
                    arrayOf(lockedDir.absolutePath),
                    arrayOf(if (media.isVideo) "video/*" else "image/*")
                ) { _, _ ->
                }
            }
            withContext(Dispatchers.Main) {
                hideLoading()
                MyApplication.isVideoFetchReload = true
                mediaList.removeAt(currentPosition)

                (binding.viewPager.adapter as ImagePagerAdapter).notifyDataSetChanged()
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

    private fun refreshMediaList() {
        val updatedMediaList = MyApplication.mediaList.toMutableList()

        if (updatedMediaList.isEmpty()) {
            backScreenAnimation()
            finish()
            return
        }

        val currentPosition = binding.viewPager.currentItem
        val currentMediaId =
            if (currentPosition in mediaList.indices) mediaList[currentPosition].id else -1

        mediaList.clear()
        mediaList.addAll(updatedMediaList)

        (binding.viewPager.adapter as ImagePagerAdapter).updateMediaList(updatedMediaList)

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