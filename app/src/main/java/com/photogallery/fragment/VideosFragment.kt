package com.photogallery.fragment

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.processDuplicates
import com.photogallery.MyApplication.Companion.processFaceEmbeddings
import com.photogallery.MyApplication.Companion.processLocationPhotos
import com.photogallery.MyApplication.Companion.processMoments
import com.photogallery.MyApplication.Companion.processPhotoClassification
import com.photogallery.R
import com.photogallery.activity.HomeActivity
import com.photogallery.activity.VideoPlayerActivity
import com.photogallery.adapter.GalleryVideoAdapter
import com.photogallery.base.BaseFragment
import com.photogallery.databinding.DialogPersonaliseGridBinding
import com.photogallery.databinding.FragmentPhotosBinding
import com.photogallery.model.GalleryListItem
import com.photogallery.model.MediaData
import com.photogallery.utils.Const.SPAN_COUNT_DEFAULT
import com.photogallery.utils.LayoutMode
import com.photogallery.utils.ViewMode
import com.photogallery.utils.isInternet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideosFragment : BaseFragment<FragmentPhotosBinding>() {
    private lateinit var galleryVideosAdapter: GalleryVideoAdapter
    private val mediaOnlyList = mutableListOf<MediaData>()
    var personaliseLayoutDialog: AlertDialog? = null
    private var shortOrder = 0
    var getViewMode: String? = "DAY"
    private var spanCount = 3
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var hasScaled = false

    companion object {
        private var homeActivity: HomeActivity? = null
        fun newInstance(homeActivity: HomeActivity) = VideosFragment().apply {
            Companion.homeActivity = homeActivity
            arguments = Bundle().apply {}
        }
    }

    override fun getViewBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentPhotosBinding {
        return FragmentPhotosBinding.inflate(inflater, container, false)
    }

    override fun init() {
        binding.emptyViewLayout.tvTitle.text = getString(R.string.no_videos_yet)
        binding.emptyViewLayout.tvDescription.text =
            getString(R.string.no_videos_available_capture_and_share_your_moments)
        binding.emptyViewLayout.ivIllustrator.setImageResource(R.drawable.ic_videos_empty)
        scaleGestureDetector = ScaleGestureDetector(
            requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
                    ePreferences?.putInt("span_count", spanCount)
                }
            })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        spanCount = ePreferences?.getInt("span_count", SPAN_COUNT_DEFAULT) ?: SPAN_COUNT_DEFAULT
        initializeAdapter()
        binding.rvPhotos.setOnTouchListener { _, event ->
            if (event.pointerCount >= 2) {
                scaleGestureDetector.onTouchEvent(event)
                true
            } else {
                false
            }
        }
    }

    private fun initializeAdapter() {
        galleryVideosAdapter = GalleryVideoAdapter(
            context = requireContext(),
            galleryImgList = emptyList(),
            layoutMode = LayoutMode.GRID,
            viewMode = viewMode,
            spanCount = spanCount,
            onOptionClickListener = {
                showPersonaliseLayoutDialog(viewMode)
            },
            onSelectionModeChange = { isSelectionMode ->
                (activity as? HomeActivity)?.apply {
                    if (isSelectionMode) showSelectionModeToolbarAndOptions()
                    else showMainToolbarAndBottomNav()
                }
            },
            onSelectedCountChange = { count ->
                (activity as? HomeActivity)?.updateSelectedCount(count)
            },
            onImageClickListener = { mediaData, _ ->
                val position = mediaOnlyList.indexOfFirst { it.id == mediaData.id }
                if (position != -1) {
                    val intent = Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                        putExtra("selected_position", position)
                        putExtra("media_id", mediaData.id)
                    }
                    startActivity(intent)
                    (requireContext() as Activity).nextScreenAnimation()
                }
            })
    }

    override fun onResume() {
        super.onResume()
        getViewMode = ePreferences?.getString("view_mode", "DAY")
        shortOrder = ePreferences!!.getInt("SortOrder", 0)
        when (getViewMode) {
            "DAY" -> {
                viewMode = ViewMode.DAY
            }

            "MONTH" -> {
                viewMode = ViewMode.MONTH
            }

            "COMFORTABLE" -> {
                viewMode = ViewMode.COMFORTABLE
            }
        }
        if (::galleryVideosAdapter.isInitialized && !galleryVideosAdapter.isSelectionMode) {
            if (hasStoragePermission()) {
                if (MyApplication.isVideoFetchReload == true) {
                    loadVideos(viewMode)
                }
                processLocationPhotos(requireContext())
                if (requireContext().isInternet()) {
                    processPhotoClassification(requireContext())
                    processMoments(requireContext())
                    processDuplicates(requireContext())
                    processFaceEmbeddings(requireContext())
                }
            } else {
                showPermissionDialog()
            }
        }
    }

    override fun onStoragePermissionGranted() {
        loadVideos(viewMode)
    }

    override fun addListener() {
        binding.ivStickyMenuOption.setOnClickListener {
            showPersonaliseLayoutDialog(viewMode)
        }

        binding.emptyViewLayout.btnOpen.setOnClickListener {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            if (intent.resolveActivity(requireContext().packageManager) != null) {
                startActivity(intent)
                (requireContext() as Activity).nextScreenAnimation()
            } else {
                Toast.makeText(
                    requireContext(), getString(R.string.no_camera_app_found), Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun showPersonaliseLayoutDialog(viewModeNew: ViewMode) {
        val builder = AlertDialog.Builder(context)
        val layoutDialogBinding = DialogPersonaliseGridBinding.inflate(LayoutInflater.from(context))
        builder.setCancelable(false)

        when (viewModeNew) {
            ViewMode.COMFORTABLE -> {
                layoutDialogBinding.rbComfort.isChecked = true
                layoutDialogBinding.rbDay.isChecked = false
                layoutDialogBinding.rbMonth.isChecked = false
                viewMode = ViewMode.COMFORTABLE
            }

            ViewMode.DAY -> {
                layoutDialogBinding.rbDay.isChecked = true
                layoutDialogBinding.rbComfort.isChecked = false
                layoutDialogBinding.rbMonth.isChecked = false
                viewMode = ViewMode.DAY
            }

            ViewMode.MONTH -> {
                layoutDialogBinding.rbMonth.isChecked = true
                layoutDialogBinding.rbComfort.isChecked = false
                layoutDialogBinding.rbDay.isChecked = false
                viewMode = ViewMode.MONTH
            }
        }

        layoutDialogBinding.rbComfort.setOnClickListener {
            layoutDialogBinding.rbComfort.isChecked = true
            layoutDialogBinding.rbDay.isChecked = false
            layoutDialogBinding.rbMonth.isChecked = false
            viewMode = ViewMode.COMFORTABLE
            getViewMode = "COMFORTABLE"
        }
        layoutDialogBinding.rbDay.setOnClickListener {
            layoutDialogBinding.rbDay.isChecked = true
            layoutDialogBinding.rbComfort.isChecked = false
            layoutDialogBinding.rbMonth.isChecked = false
            viewMode = ViewMode.DAY
            getViewMode = "DAY"
        }
        layoutDialogBinding.rbMonth.setOnClickListener {
            layoutDialogBinding.rbMonth.isChecked = true
            layoutDialogBinding.rbComfort.isChecked = false
            layoutDialogBinding.rbDay.isChecked = false
            viewMode = ViewMode.MONTH
            getViewMode = "MONTH"
        }

        layoutDialogBinding.btnApply.setOnClickListener {
            loadVideos(viewMode)
            ePreferences?.setString("view_mode", getViewMode)
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

    private fun loadVideos(viewMode: ViewMode) {
        binding.ivStickyMenuOption.visibility = View.GONE
        lifecycleScope.launch {
            val itemList = withContext(Dispatchers.IO) {
                loadGroupedVideos(viewMode)
            }

            withContext(Dispatchers.Main) {
                MyApplication.isVideoFetchReload = false
                if (itemList.isEmpty()) {
                    binding.emptyViewLayout.llEmptyLayout.visibility = View.VISIBLE
                    return@withContext
                }

                binding.emptyViewLayout.llEmptyLayout.visibility = View.GONE

                galleryVideosAdapter = GalleryVideoAdapter(
                    context = requireContext(),
                    galleryImgList = itemList,
                    layoutMode = LayoutMode.GRID,
                    viewMode = viewMode,
                    spanCount = spanCount,
                    onOptionClickListener = {
                        showPersonaliseLayoutDialog(viewMode)
                    },
                    onSelectionModeChange = { isSelectionMode ->
                        (activity as? HomeActivity)?.apply {
                            if (isSelectionMode) showSelectionModeToolbarAndOptions()
                            else showMainToolbarAndBottomNav()
                        }
                    },
                    onSelectedCountChange = { count ->
                        (activity as? HomeActivity)?.updateSelectedCount(count)
                    },
                    onImageClickListener = { mediaData, _ ->
                        val position = mediaOnlyList.indexOfFirst { it.id == mediaData.id }
                        if (position != -1) {
                            val intent =
                                Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                                    putExtra("selected_position", position)
                                    putExtra("media_id", mediaData.id)
                                }
                            startActivity(intent)
                            (requireContext() as Activity).nextScreenAnimation()
                        }
                    })

                val layoutManager = GridLayoutManager(requireContext(), spanCount)
                layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (galleryVideosAdapter.getItemViewType(position)) {
                            0 -> spanCount // e.g., header or full-span items
                            else -> 1       // regular grid items
                        }
                    }
                }

                binding.rvPhotos.layoutManager = layoutManager
                binding.rvPhotos.adapter = galleryVideosAdapter
            }
        }
    }

    private fun updateGridLayout() {
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (galleryVideosAdapter.getItemViewType(position)) {
                    0 -> spanCount // Header spans full width
                    else -> 1      // Media items take one span
                }
            }
        }
        binding.rvPhotos.layoutManager = layoutManager
        binding.rvPhotos.adapter = galleryVideosAdapter
        galleryVideosAdapter.updateSpanCount(spanCount)
    }

    private fun loadGroupedVideos(viewMode: ViewMode): List<GalleryListItem> {
        val videoMap = mutableMapOf<String, MutableList<MediaData>>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.DURATION,
        )

        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        val sortOrder = when (shortOrder) {
            0 -> MediaStore.Video.Media.DATE_ADDED + " DESC" // Newest first (default)
            1 -> MediaStore.Video.Media.DATE_ADDED + " ASC"  // Oldest first
            else -> MediaStore.Video.Media.DATE_ADDED + " DESC"
        }

        val cursor = context?.contentResolver?.query(
            uri, projection, null, null, sortOrder
        )

        cursor?.use {
            val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dateColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val pathColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

            while (it.moveToNext()) {
                val id = it.getLong(idColumn)
                val name = it.getString(nameColumn)
                val dateAddedMillis = it.getLong(dateColumn) * 1000
                val path = it.getString(pathColumn)
                val duration = it.getLong(durationColumn)

                val dateTakenMillis = if (dateAddedMillis > 0) dateAddedMillis else {
                    val file = File(path)
                    if (file.exists()) file.lastModified() else System.currentTimeMillis()
                }

                val date = Date(dateTakenMillis)
                val dateKey = when (viewMode) {
                    ViewMode.DAY -> dateFormat.format(date)
                    ViewMode.MONTH -> monthFormat.format(date)
                    ViewMode.COMFORTABLE -> monthFormat.format(date)
                }

                val contentUri = ContentUris.withAppendedId(uri, id)
                val media = MediaData(
                    id, name, path, contentUri, dateTakenMillis, isVideo = true, duration = duration
                )
                videoMap.getOrPut(dateKey) { mutableListOf() }.add(media)
            }
        }

        if (videoMap.isEmpty()) {
            return emptyList()
        }

        val sortedDateKeys = if (shortOrder == 0) {
            videoMap.keys.sortedByDescending {
                when (viewMode) {
                    ViewMode.DAY -> dateFormat.parse(it)
                    ViewMode.MONTH, ViewMode.COMFORTABLE -> monthFormat.parse(it)
                }
            }
        } else {
            videoMap.keys.sortedBy {
                when (viewMode) {
                    ViewMode.DAY -> dateFormat.parse(it)
                    ViewMode.MONTH, ViewMode.COMFORTABLE -> monthFormat.parse(it)
                }
            }
        }

        val itemList = mutableListOf<GalleryListItem>()
        for (date in sortedDateKeys) {
            itemList.add(GalleryListItem.DateHeader(date))
            videoMap[date]?.forEach {
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
        galleryVideosAdapter.clearSelection()
    }

    fun getSelectedMedia(): List<MediaData> {
        return if (::galleryVideosAdapter.isInitialized) {
            galleryVideosAdapter.getSelectedMedia()
        } else {
            emptyList()
        }
    }

    fun removeMedia(media: MediaData) {
        if (!::galleryVideosAdapter.isInitialized) return

        val itemList = galleryVideosAdapter.galleryImgList.toMutableList()
        val index = itemList.indexOfFirst { item ->
            item is GalleryListItem.MediaItem && item.media.id == media.id
        }

        if (index != -1) {
            itemList.removeAt(index)
            galleryVideosAdapter.notifyItemRemoved(index)

            val dateHeaderIndex = itemList.indexOfLast { item ->
                item is GalleryListItem.DateHeader && itemList.indexOf(item) < index
            }
            if (dateHeaderIndex != -1) {
                val dateHeader = itemList[dateHeaderIndex] as GalleryListItem.DateHeader
                val hasItemsForDate = itemList.any { item ->
                    item is GalleryListItem.MediaItem && getDateKey(
                        item.media.dateTaken, viewMode
                    ) == dateHeader.date
                }
                if (!hasItemsForDate) {
                    itemList.removeAt(dateHeaderIndex)
                    galleryVideosAdapter.notifyItemRemoved(dateHeaderIndex)
                }
            }

            galleryVideosAdapter.galleryImgList = itemList
            updateMediaLists(itemList)

            if (itemList.isEmpty()) {
                binding.emptyViewLayout.llEmptyLayout.visibility = View.VISIBLE
            }

            if (galleryVideosAdapter.isSelectionMode) {
                (activity as? HomeActivity)?.updateSelectedCount(galleryVideosAdapter.getSelectedMedia().size)
            }
        }
    }
}

private fun getDateKey(dateTakenMillis: Long, viewMode: ViewMode): String {
    val dateFormat = SimpleDateFormat(
        if (viewMode == ViewMode.DAY) "EEE, dd MMM yyyy" else "MMMM yyyy", Locale.getDefault()
    )
    return dateFormat.format(Date(dateTakenMillis))
}

