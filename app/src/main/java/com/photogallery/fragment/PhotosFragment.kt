package com.photogallery.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
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
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.carousel.CarouselLayoutManager
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.processDuplicates
import com.photogallery.MyApplication.Companion.processFaceEmbeddings
import com.photogallery.MyApplication.Companion.processLocationPhotos
import com.photogallery.MyApplication.Companion.processMoments
import com.photogallery.MyApplication.Companion.processPhotoClassification
import com.photogallery.R
import com.photogallery.activity.HomeActivity
import com.photogallery.activity.MomentDetailsActivity
import com.photogallery.activity.PhotoViewActivity
import com.photogallery.adapter.GalleryPhotosAdapter
import com.photogallery.adapter.MomentsImgAdapter
import com.photogallery.base.BaseFragment
import com.photogallery.databinding.DialogPersonaliseGridBinding
import com.photogallery.databinding.FragmentPhotosBinding
import com.photogallery.model.GalleryListItem
import com.photogallery.model.MediaData
import com.photogallery.model.Moment
import com.photogallery.utils.Const.PERMISSION_REQUEST_CODE
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

class PhotosFragment : BaseFragment<FragmentPhotosBinding>(),
    MomentsImgAdapter.OnItemClickListener {
    private val imgArrayList = ArrayList<Moment>()
    private lateinit var galleryPhotosAdapter: GalleryPhotosAdapter
    var personaliseLayoutDialog: AlertDialog? = null
    private val mediaOnlyList = mutableListOf<MediaData>()
    private var shortOrder = 0
    var getViewMode: String? = "DAY"
    private var spanCount = 3
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var hasScaled = false

    companion object {
        private var homeActivity: HomeActivity? = null
        private var isGIF: Boolean = false
        fun newInstance(homeActivity: HomeActivity, isGIF: Boolean) = PhotosFragment().apply {
            Companion.homeActivity = homeActivity
            Companion.isGIF = isGIF
            arguments = Bundle().apply {}
        }
    }

    override fun getViewBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentPhotosBinding {
        return FragmentPhotosBinding.inflate(inflater, container, false)
    }

    override fun init() {
        setMoments()
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
            requireContext(),
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
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
            }
        )
    }

    @SuppressLint("ClickableViewAccessibility")
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
        galleryPhotosAdapter = GalleryPhotosAdapter(
            ePreferences,
            context = requireContext(),
            galleryImgList = emptyList(),
            layoutMode = LayoutMode.GRID,
            viewMode = viewMode,
            isGIF = isGIF,
            spanCount = spanCount,
            onOptionClickListener = { showPersonaliseLayoutDialog(viewMode) },
            onSelectionModeChange = { isSelectionMode ->
                (activity as? HomeActivity)?.let { activity ->
                    if (isSelectionMode) {
                        activity.showSelectionModeToolbarAndOptions()
                    } else {
                        activity.showMainToolbarAndBottomNav()
                    }
                }
            },
            onSelectedCountChange = { count ->
                (activity as? HomeActivity)?.updateSelectedCount(count)
            },
            onImageClickListener = { mediaData, _ ->
                val position = mediaOnlyList.indexOfFirst { it.id == mediaData.id }
                if (position != -1) {
                    val intent = Intent(requireContext(), PhotoViewActivity::class.java).apply {
                        putExtra("selected_position", position)
                        putExtra("media_id", mediaData.id)
                    }
                    startActivity(intent)
                    (requireContext() as Activity).nextScreenAnimation()
                }
            })
        binding.rvMoments.layoutManager = CarouselLayoutManager()
        binding.rvMoments.adapter = homeActivity?.let {
            MomentsImgAdapter(imgArrayList, this)
        }
    }

    override fun onResume() {
        super.onResume()
        shortOrder = ePreferences!!.getInt("SortOrder", 0)
        getViewMode = ePreferences?.getString("view_mode", "DAY")
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

        if (::galleryPhotosAdapter.isInitialized && !galleryPhotosAdapter.isSelectionMode) {
            if (hasStoragePermission()) {
                if (MyApplication.isPhotoFetchReload == true) {
                    loadImages(viewMode)
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
        loadImages(viewMode)
    }

    private fun setMoments() {
        MyApplication.momentsLiveData.observe(viewLifecycleOwner) { momentGroups ->
            imgArrayList.clear()
            if (!momentGroups.isNullOrEmpty()) {
                binding.rvMoments.visibility = View.VISIBLE
                for (group in momentGroups) {
                    for (moment in group.moments) {
                        imgArrayList.add(moment)
                    }
                }
                if (binding.rvMoments.adapter == null) {
                    binding.rvMoments.layoutManager =
                        LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                    binding.rvMoments.adapter = MomentsImgAdapter(
                        imgArrayList, this
                    )
                }
                binding.rvMoments.adapter?.notifyDataSetChanged()
            } else {
                binding.rvMoments.visibility = View.GONE
            }
        }
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
        builder.setCancelable(false)
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
            loadImages(viewMode)
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

    private fun loadImages(viewMode: ViewMode) {
        binding.ivStickyMenuOption.visibility = View.GONE
        lifecycleScope.launch {
            val itemList = withContext(Dispatchers.IO) {
                loadGroupedImages(viewMode)
            }

            if (itemList.isEmpty()) {
                withContext(Dispatchers.Main) {
                    binding.emptyViewLayout.llEmptyLayout.visibility = View.VISIBLE
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                binding.emptyViewLayout.llEmptyLayout.visibility = View.GONE

                MyApplication.isPhotoFetchReload = false
                galleryPhotosAdapter = GalleryPhotosAdapter(
                    ePreferences = ePreferences,
                    context = requireContext(),
                    galleryImgList = itemList,
                    layoutMode = LayoutMode.GRID,
                    viewMode = viewMode,
                    isGIF = isGIF,
                    spanCount = spanCount,
                    onOptionClickListener = {
                        showPersonaliseLayoutDialog(viewMode)
                    },
                    onSelectionModeChange = { isSelectionMode ->
                        (activity as? HomeActivity)?.let { activity ->
                            if (isSelectionMode) {
                                activity.showSelectionModeToolbarAndOptions()
                            } else {
                                activity.showMainToolbarAndBottomNav()
                            }
                        }
                    },
                    onSelectedCountChange = { count ->
                        (activity as? HomeActivity)?.updateSelectedCount(count)
                    }
                ) { mediaData, _ ->
                    val position = mediaOnlyList.indexOfFirst { it.id == mediaData.id }
                    if (position != -1) {
                        val intent =
                            Intent(requireContext(), PhotoViewActivity::class.java).apply {
                                putExtra("selected_position", position)
                                putExtra("media_id", mediaData.id)
                            }
                        startActivity(intent)
                        (requireContext() as Activity).nextScreenAnimation()
                    }
                }

                val layoutManager = GridLayoutManager(
                    requireContext(), spanCount
                )
                layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        return when (galleryPhotosAdapter.getItemViewType(position)) {
                            0 -> spanCount
                            else -> 1
                        }
                    }
                }

                binding.rvPhotos.layoutManager = layoutManager
                binding.rvPhotos.adapter = galleryPhotosAdapter
            }
        }
    }

    private fun updateGridLayout() {
        val layoutManager = GridLayoutManager(requireContext(), spanCount)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (galleryPhotosAdapter.getItemViewType(position)) {
                    0 -> spanCount
                    else -> 1
                }
            }
        }
        binding.rvPhotos.layoutManager = layoutManager
        binding.rvPhotos.adapter = galleryPhotosAdapter
        galleryPhotosAdapter.updateSpanCount(spanCount)
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
            MediaStore.Images.Media.MIME_TYPE,
        )

        val selection = if (isGIF) {
            "${MediaStore.Images.Media.MIME_TYPE} = ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
        } else {
            "${MediaStore.Images.Media.MIME_TYPE} != ? AND ${MediaStore.Images.Media.DATA} NOT LIKE ?"
        }
        val selectionArgs = arrayOf(
            "image/gif", "%/recycle_bin/%"
        )
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

        val sortOrder = when (shortOrder) {
            0 -> MediaStore.Images.Media.DATE_ADDED + " DESC"
            1 -> MediaStore.Images.Media.DATE_ADDED + " ASC"
            else -> MediaStore.Images.Media.DATE_ADDED + " DESC"
        }

        val allUris = mutableListOf<Uri>()

        val cursor = context?.contentResolver?.query(
            uri, projection, selection, selectionArgs, sortOrder
        )

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

    private fun updateMediaLists(itemList: List<GalleryListItem>) {
        mediaOnlyList.clear()
        mediaOnlyList.addAll(
            itemList.filterIsInstance<GalleryListItem.MediaItem>().map { it.media })
        MyApplication.mediaList = mediaOnlyList.toList()
    }

    fun removeMedia(media: MediaData) {
        if (!::galleryPhotosAdapter.isInitialized) return

        val itemList = galleryPhotosAdapter.galleryImgList.toMutableList()
        val index = itemList.indexOfFirst { item ->
            item is GalleryListItem.MediaItem && item.media.id == media.id
        }

        if (index != -1) {
            itemList.removeAt(index)
            galleryPhotosAdapter.notifyItemRemoved(index)

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
                    galleryPhotosAdapter.notifyItemRemoved(dateHeaderIndex)
                }
            }

            galleryPhotosAdapter.galleryImgList = itemList
            updateMediaLists(itemList)

            if (itemList.isEmpty()) {
                binding.emptyViewLayout.llEmptyLayout.visibility = View.VISIBLE
            }

            if (galleryPhotosAdapter.isSelectionMode) {
                (activity as? HomeActivity)?.updateSelectedCount(galleryPhotosAdapter.getSelectedMedia().size)
            }
        }
    }

    private fun getDateKey(dateTakenMillis: Long, viewMode: ViewMode): String {
        val dateFormat = SimpleDateFormat(
            if (viewMode == ViewMode.DAY) "EEE, dd MMM yyyy" else "MMMM yyyy", Locale.getDefault()
        )
        return dateFormat.format(Date(dateTakenMillis))
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
                    showPermissionDialog()
                }
            }
        }
    }

    override fun onItemClick(position: Int, moment: Moment) {
        try {
            MyApplication.setSelectedMoment(moment)
            val intent = Intent(requireContext(), MomentDetailsActivity::class.java)
            if (!requireActivity().isFinishing) {
                startActivity(intent)
                (requireContext() as Activity).nextScreenAnimation()
            }
        } catch (_: Exception) {
        }
    }
}