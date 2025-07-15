package com.photogallery.activity

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.adapter.GalleryPhotosAdapter
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivitySelectImageBinding
import com.photogallery.databinding.DialogPersonaliseGridBinding
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.model.GalleryListItem
import com.photogallery.model.MediaData
import com.photogallery.utils.Const.PERMISSION_REQUEST_CODE
import com.photogallery.utils.Const.SPAN_COUNT_DEFAULT
import com.photogallery.utils.LayoutMode
import com.photogallery.utils.MediaDataSerializer
import com.photogallery.utils.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SelectImageActivity : BaseActivity<ActivitySelectImageBinding>() {
    private lateinit var galleryPhotosAdapter: GalleryPhotosAdapter
    private var personaliseLayoutDialog: AlertDialog? = null
    private val mediaOnlyList = mutableListOf<MediaData>()
    private var shortOrder = 0
    private var viewMode: ViewMode = ViewMode.DAY
    private var spanCount = 3 // Default span count
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var hasScaled = false
    private var isGIF: Boolean = false // Set based on intent or default
    private var isSelectionMode = false

    override fun getViewBinding(): ActivitySelectImageBinding {
        return ActivitySelectImageBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        binding.ivMore.visibility = View.VISIBLE
        isGIF = intent.getBooleanExtra("isGIF", false)
        isSelectionMode = intent.getBooleanExtra("isSelectionMode", false)
        binding.tvToolbarTitle.text = getString(R.string.select_image)
        binding.ivMore.visibility = View.GONE
        if (isGIF) {
            binding.emptyViewLayout.tvTitle.text = getString(R.string.no_gifs_yet)
            binding.emptyViewLayout.tvDescription.text =
                getString(R.string.no_gifs_here_add_some_fun_by_downloading_your_favourite_gifs)
            binding.emptyViewLayout.ivIllustrator.setImageResource(R.drawable.ic_gif_empty)
            binding.emptyViewLayout.btnOpen.visibility = View.GONE
        } else {
            binding.emptyViewLayout.tvTitle.text = getString(R.string.no_photos_yet)
            binding.emptyViewLayout.tvDescription.text =
                getString(R.string.no_photos_available_capture_and_share_your_moments)
            binding.emptyViewLayout.ivIllustrator.setImageResource(R.drawable.ic_photos_empty)
        }

        scaleGestureDetector = ScaleGestureDetector(
            this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    if (!hasScaled) {
                        if (detector.scaleFactor > 1.0f && spanCount > 1) {
                            spanCount--
                            updateGridLayout()
                            hasScaled = true
                        } else if (detector.scaleFactor < 1.0f && spanCount < 5) {
                            spanCount++
                            updateGridLayout()
                            hasScaled = true
                        }
                    }
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    hasScaled = false
                    ePreferences.putInt("span_count", spanCount)
                }
            })

        spanCount = ePreferences.getInt("span_count", SPAN_COUNT_DEFAULT)
        shortOrder = ePreferences.getInt("SortOrder", 0)
        val savedViewMode = ePreferences.getString("view_mode", "DAY")
        viewMode = when (savedViewMode) {
            "DAY" -> ViewMode.DAY
            "MONTH" -> ViewMode.MONTH
            "COMFORTABLE" -> ViewMode.COMFORTABLE
            else -> ViewMode.DAY
        }

        initializeAdapter()
        if (MyApplication.instance.hasStoragePermission()) {
            loadImages(viewMode)
        } else {
            showPermissionDialog(this)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun addListener() {
        binding.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }

        binding.rvPhotos.setOnTouchListener { _, event ->
            if (event.pointerCount >= 2) {
                scaleGestureDetector.onTouchEvent(event)
                true
            } else {
                false
            }
        }

        binding.ivStickyMenuOption.setOnClickListener {
            showPersonaliseLayoutDialog(viewMode)
        }

        binding.emptyViewLayout.btnOpen.setOnClickListener {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                nextScreenAnimation()
            } else {
                Toast.makeText(this, getString(R.string.no_camera_app_found), Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.ivMore.setOnClickListener {
            if (isSelectionMode) {
                val selectedMedia = galleryPhotosAdapter.getSelectedMedia()
                if (selectedMedia.isNotEmpty()) {
                    val resultIntent = Intent().apply {
                        putExtra(
                            "selected_media_json", MediaDataSerializer.serialize(selectedMedia)
                        )
                    }
                    setResult(RESULT_OK, resultIntent)
                    finish()
                }
            }
        }
    }

    private fun initializeAdapter() {
        val dao = PhotoGalleryDatabase.getDatabase(this).photoGalleryDao()
        galleryPhotosAdapter = GalleryPhotosAdapter(
            ePreferences = ePreferences,
            context = this,
            galleryImgList = emptyList(),
            layoutMode = LayoutMode.GRID,
            viewMode = viewMode,
            isGIF = isGIF,
            spanCount = spanCount,
            onOptionClickListener = { showPersonaliseLayoutDialog(viewMode) },
            onSelectionModeChange = { isSelectionMode ->
                if (!isSelectionMode) {
                    binding.tvToolbarTitle.text = getString(R.string.select_image)
                }
            },
            onSelectedCountChange = { count ->
                updateSelectedCount(count)
                if (count <= 0) {
                    binding.ivMore.visibility = View.GONE
                } else {
                    binding.ivMore.visibility = View.VISIBLE
                }
            },
            onImageClickListener = { mediaData, _ ->
                val position = mediaOnlyList.indexOfFirst { it.id == mediaData.id }
                if (position != -1) {
                    val intent = Intent(this, PhotoViewActivity::class.java).apply {
                        putExtra("selected_position", position)
                        putExtra("media_id", mediaData.id)
                    }
                    startActivity(intent)
                    nextScreenAnimation()
                }
            }
        )

        val layoutManager = GridLayoutManager(this, spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (galleryPhotosAdapter.getItemViewType(position)) {
                        0 -> spanCount // Header spans full width
                        else -> 1 // Media items take one span
                    }
                }
            }
        }
        binding.rvPhotos.layoutManager = layoutManager
        binding.rvPhotos.adapter = galleryPhotosAdapter
    }

    private fun updateGridLayout() {
        val layoutManager = GridLayoutManager(this, spanCount).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when (galleryPhotosAdapter.getItemViewType(position)) {
                        0 -> spanCount
                        else -> 1
                    }
                }
            }
        }
        binding.rvPhotos.layoutManager = layoutManager
        galleryPhotosAdapter.updateSpanCount(spanCount)
    }

    private fun showPersonaliseLayoutDialog(viewMode: ViewMode) {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        val layoutDialogBinding = DialogPersonaliseGridBinding.inflate(layoutInflater)

        when (viewMode) {
            ViewMode.COMFORTABLE -> {
                layoutDialogBinding.rbComfort.isChecked = true
                layoutDialogBinding.rbDay.isChecked = false
                layoutDialogBinding.rbMonth.isChecked = false
            }

            ViewMode.DAY -> {
                layoutDialogBinding.rbDay.isChecked = true
                layoutDialogBinding.rbComfort.isChecked = false
                layoutDialogBinding.rbMonth.isChecked = false
            }

            ViewMode.MONTH -> {
                layoutDialogBinding.rbMonth.isChecked = true
                layoutDialogBinding.rbComfort.isChecked = false
                layoutDialogBinding.rbDay.isChecked = false
            }
        }

        layoutDialogBinding.rbComfort.setOnClickListener {
            layoutDialogBinding.rbComfort.isChecked = true
            layoutDialogBinding.rbDay.isChecked = false
            layoutDialogBinding.rbMonth.isChecked = false
            this.viewMode = ViewMode.COMFORTABLE
            ePreferences.setString("view_mode", "COMFORTABLE")
        }
        layoutDialogBinding.rbDay.setOnClickListener {
            layoutDialogBinding.rbDay.isChecked = true
            layoutDialogBinding.rbComfort.isChecked = false
            layoutDialogBinding.rbMonth.isChecked = false
            this.viewMode = ViewMode.DAY
            ePreferences.setString("view_mode", "DAY")
        }
        layoutDialogBinding.rbMonth.setOnClickListener {
            layoutDialogBinding.rbMonth.isChecked = true
            layoutDialogBinding.rbComfort.isChecked = false
            layoutDialogBinding.rbDay.isChecked = false
            this.viewMode = ViewMode.MONTH
            ePreferences.setString("view_mode", "MONTH")
        }

        layoutDialogBinding.btnApply.setOnClickListener {
            loadImages(viewMode)
            personaliseLayoutDialog?.dismiss()
        }
        layoutDialogBinding.ivClose.setOnClickListener {
            personaliseLayoutDialog?.dismiss()
        }

        builder.setView(layoutDialogBinding.root)
        personaliseLayoutDialog = builder.create()
        personaliseLayoutDialog?.setCanceledOnTouchOutside(true)
        personaliseLayoutDialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        personaliseLayoutDialog?.show()
    }

    private fun loadImages(viewMode: ViewMode) {
        binding.ivStickyMenuOption.visibility = View.GONE
        lifecycleScope.launch {
            val itemList = withContext(Dispatchers.IO) {
                loadGroupedImages(viewMode)
            }

            withContext(Dispatchers.Main) {
                if (itemList.isEmpty()) {
                    binding.emptyViewLayout.llEmptyLayout.visibility = View.VISIBLE
                } else {
                    binding.emptyViewLayout.llEmptyLayout.visibility = View.GONE
                    galleryPhotosAdapter.galleryImgList = itemList
                    galleryPhotosAdapter.notifyDataSetChanged()
                }
                MyApplication.isPhotoFetchReload = false
            }
        }
    }

    private fun loadGroupedImages(viewMode: ViewMode): List<GalleryListItem> {
        val imageMap = mutableMapOf<String, MutableList<MediaData>>()
        val uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.MIME_TYPE
        )

        val selection = if (isGIF) {
            "${MediaStore.Images.Media.MIME_TYPE} = ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
        } else {
            "${MediaStore.Images.Media.MIME_TYPE} != ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
        }
        val selectionArgs = arrayOf("image/gif", "%/recycle_bin/%")
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        val sortOrder = when (shortOrder) {
            0 -> "${MediaStore.Images.Media.DATE_ADDED} DESC"
            1 -> "${MediaStore.Images.Media.DATE_ADDED} ASC"
            else -> "${MediaStore.Images.Media.DATE_ADDED} DESC"
        }

        val allUris = mutableListOf<Uri>()
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                var dateTakenMillis = it.getLong(dateColumn) * 1000
                val path = it.getString(pathColumn)

                if (dateTakenMillis <= 0) {
                    val file = File(path)
                    if (file.exists()) {
                        dateTakenMillis = file.lastModified()
                    }
                }

                val date = Date(dateTakenMillis)
                val dateKey = when (viewMode) {
                    ViewMode.DAY -> dateFormat.format(date)
                    ViewMode.MONTH -> monthFormat.format(date)
                    ViewMode.COMFORTABLE -> monthFormat.format(date)
                }
                val contentUri = ContentUris.withAppendedId(uri, id)
                val media = MediaData(id, name, path, contentUri, dateTakenMillis, isVideo = false)
                imageMap.getOrPut(dateKey) { mutableListOf() }.add(media)
                allUris.add(contentUri)
            }
        }

        MyApplication.updateAllImageUris(allUris)

        if (imageMap.isEmpty()) {
            return emptyList()
        }

        val sortedDateKeys = if (shortOrder == 0) {
            imageMap.keys.sortedByDescending {
                when (viewMode) {
                    ViewMode.DAY -> dateFormat.parse(it)
                    ViewMode.MONTH -> monthFormat.parse(it)
                    ViewMode.COMFORTABLE -> monthFormat.parse(it)
                }
            }
        } else {
            imageMap.keys.sortedBy {
                when (viewMode) {
                    ViewMode.DAY -> dateFormat.parse(it)
                    ViewMode.MONTH -> monthFormat.parse(it)
                    ViewMode.COMFORTABLE -> monthFormat.parse(it)
                }
            }
        }

        val itemList = mutableListOf<GalleryListItem>()
        for (date in sortedDateKeys) {
            itemList.add(GalleryListItem.DateHeader(date))
            imageMap[date]?.forEach {
                itemList.add(GalleryListItem.MediaItem(it))
            }
        }

        updateMediaLists(itemList)
        return itemList
    }

    private fun updateMediaLists(itemList: List<GalleryListItem>) {
        mediaOnlyList.clear()
        mediaOnlyList.addAll(
            itemList.filterIsInstance<GalleryListItem.MediaItem>().map { it.media })
        MyApplication.mediaList = mediaOnlyList.toList()
    }

    fun clearSelection() {
        galleryPhotosAdapter.clearSelection()
    }

    fun getSelectedMedia(): List<MediaData> {
        return if (::galleryPhotosAdapter.isInitialized) {
            galleryPhotosAdapter.getSelectedMedia()
        } else {
            emptyList()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    loadImages(viewMode)
                } else {
                    showPermissionDialog(this)
                }
            }
        }
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    fun updateSelectedCount(count: Int) {
        binding.tvToolbarTitle.text = getString(R.string.selected, count)
    }
}