package com.photogallery.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.photogallery.R
import com.photogallery.model.GalleryListItem
import com.photogallery.model.MediaData
import com.photogallery.utils.LayoutMode
import com.photogallery.utils.ViewMode
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class GalleryVideoAdapter(
    private val context: Context,
    internal var galleryImgList: List<GalleryListItem>,
    private val layoutMode: LayoutMode,
    private val viewMode: ViewMode,
    private var spanCount: Int,
    private val onOptionClickListener: () -> Unit,
    private val onSelectionModeChange: (Boolean) -> Unit,
    private val onSelectedCountChange: (Int) -> Unit,
    private val onImageClickListener: (MediaData, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), RecyclerViewFastScroller.OnPopupViewUpdate {
    private val selectedMedia = mutableSetOf<MediaData>()
    internal var isSelectionMode = false
    private var attachedRecyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attachedRecyclerView = null
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_IMAGE = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (galleryImgList[position]) {
            is GalleryListItem.DateHeader -> TYPE_HEADER
            is GalleryListItem.MediaItem -> TYPE_IMAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_date_header, parent, false)
            DateHeaderViewHolder(
                view,
                onOptionClickListener = onOptionClickListener,
                onSelectClickListener = { date ->
                    if (viewMode == ViewMode.DAY) {
                        selectMediaByDate(date)
                    } else {
                        selectMediaByMonth(date)
                    }
                }
            )
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_media_list, parent, false)
            MediaViewHolder(
                view,
                onSelectClick = { position ->
                    val item = galleryImgList[position] as? GalleryListItem.MediaItem
                    item?.let { toggleSelection(it.media) }
                },
                onLongClick = { position ->
                    val item = galleryImgList[position] as? GalleryListItem.MediaItem
                    item?.let {
                        if (!isSelectionMode) {
                            isSelectionMode = true
                            onSelectionModeChange(true)
                            rebindVisibleMediaViewHolders()
                        }
                        toggleSelection(it.media)
                    }
                },

                onImageClick = { position ->
                    val item = galleryImgList[position] as? GalleryListItem.MediaItem
                    item?.let {
                        if (!isSelectionMode) {
                            onImageClickListener(it.media, position)
                        } else {
                            toggleSelection(it.media)
                        }
                    }
                }
            )
        }
    }

    override fun getItemCount() = galleryImgList.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = galleryImgList[position]) {
            is GalleryListItem.DateHeader -> {
                val label = if (isTodayDate(item.date) && viewMode == ViewMode.DAY) {
                    context.getString(R.string.label_today)
                } else {
                    item.date
                }
                val isFirstHeader = position == 0
                val allSelected = if (viewMode == ViewMode.DAY) {
                    areAllImagesSelectedForDate(item.date)
                } else {
                    areAllImagesSelectedForMonth(item.date)
                }
                (holder as DateHeaderViewHolder).bind(label, item.date, isFirstHeader, allSelected)
                holder.itemView.invalidate()
                holder.itemView.requestLayout()
            }

            is GalleryListItem.MediaItem -> {
                val isSelected = selectedMedia.contains(item.media)
                (holder as MediaViewHolder).bindImage(
                    item.media,
                    layoutMode,
                    isSelected,
                    isSelectionMode,
                    spanCount
                )
                holder.itemView.findViewById<ImageView>(R.id.ivSelectOption)?.let {
                    it.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
                    it.setImageResource(
                        if (isSelected) R.drawable.ic_select_active else R.drawable.ic_select_inactive
                    )
                }
                holder.itemView.invalidate()
                holder.itemView.requestLayout()
            }
        }
    }

    private fun areAllImagesSelectedForDate(date: String): Boolean {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val mediaForDate = galleryImgList.filterIsInstance<GalleryListItem.MediaItem>()
            .filter {
                val mediaDate = Date(it.media.dateTaken)
                dateFormat.format(mediaDate) == date
            }
            .map { it.media }
        return mediaForDate.isNotEmpty() && mediaForDate.all { selectedMedia.contains(it) }
    }

    private fun areAllImagesSelectedForMonth(date: String): Boolean {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val mediaForMonth = galleryImgList.filterIsInstance<GalleryListItem.MediaItem>()
            .filter {
                val mediaDate = Date(it.media.dateTaken)
                monthFormat.format(mediaDate) == date
            }
            .map { it.media }
        return mediaForMonth.isNotEmpty() && mediaForMonth.all { selectedMedia.contains(it) }
    }

    private fun toggleSelection(media: MediaData) {
        val itemPosition = galleryImgList.indexOfFirst {
            it is GalleryListItem.MediaItem && it.media.id == media.id
        }
        val wasInSelectionMode = isSelectionMode

        if (itemPosition == -1) return
        if (selectedMedia.contains(media)) {
            selectedMedia.remove(media)
            if (selectedMedia.isEmpty()) {
                isSelectionMode = false
                onSelectionModeChange(false)
            }
        } else {
            selectedMedia.add(media)
            if (!isSelectionMode) {
                isSelectionMode = true
                onSelectionModeChange(true)
            }
        }
        onSelectedCountChange(selectedMedia.size)

        if (wasInSelectionMode != isSelectionMode) {
            notifyDataSetChanged()
        } else {
            notifyItemChanged(itemPosition)
            val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
            val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val mediaDate = Date(media.dateTaken)
            val dateStr = if (viewMode == ViewMode.DAY) {
                dateFormat.format(mediaDate)
            } else {
                monthFormat.format(mediaDate)
            }
            val headerPosition = galleryImgList.indexOfFirst {
                it is GalleryListItem.DateHeader && it.date == dateStr
            }
            if (headerPosition != -1) {
                notifyItemChanged(headerPosition)
            }
        }
    }


    class DateHeaderViewHolder(
        itemView: View,
        private val onOptionClickListener: () -> Unit,
        private val onSelectClickListener: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvDate = itemView.findViewById<TextView>(R.id.tvDate)
        private val ivOption = itemView.findViewById<ImageView>(R.id.ivOptions)
        private val ivSelect = itemView.findViewById<ImageView>(R.id.ivSelect)

        fun bind(
            itemDateStr: String,
            originalDate: String,
            isFirstHeader: Boolean,
            allSelected: Boolean
        ) {
            tvDate.text = itemDateStr
            ivSelect.visibility = View.VISIBLE
            ivSelect.setImageResource(
                if (allSelected) R.drawable.ic_select_active else R.drawable.ic_select_inactive
            )
            ivOption.visibility = if (isFirstHeader) View.VISIBLE else View.GONE
            ivOption.setOnClickListener {
                if (isFirstHeader) {
                    onOptionClickListener()
                }
            }
            ivSelect.setOnClickListener {
                onSelectClickListener(originalDate)
            }
            val params = itemView.layoutParams
            if (params is StaggeredGridLayoutManager.LayoutParams) {
                params.isFullSpan = true
            }
        }
    }


    class MediaViewHolder(
        itemView: View,
        private val onSelectClick: (Int) -> Unit,
        private val onLongClick: (Int) -> Unit,
        private val onImageClick: (Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val ivItem = itemView.findViewById<ShapeableImageView>(R.id.ivGalleryImgItem)
        private val ivSelectOption = itemView.findViewById<ImageView>(R.id.ivSelectOption)
        private val ivPlayIcon = itemView.findViewById<ImageView>(R.id.ivPlayIcon)
        private val tvDuration = itemView.findViewById<TextView>(R.id.tvDuration)

        private var lastClickTime: Long = 0
        private var isSelectionMode = false

        init {
            ivPlayIcon.visibility = View.VISIBLE
            tvDuration.visibility = View.VISIBLE
            ivSelectOption.setOnClickListener {
                onSelectClick(adapterPosition)
            }

            ivItem.setOnLongClickListener {
                onLongClick(adapterPosition)
                true
            }

            ivItem.setOnClickListener {
                val now = System.currentTimeMillis()
                if (isSelectionMode) {
                    // Immediate action in selection mode
                    onImageClick(adapterPosition)
                } else {
                    // Debounce logic for normal mode
                    if (now - lastClickTime > 800) {
                        lastClickTime = now
                        onImageClick(adapterPosition)
                    }
                }
            }
        }

        fun formatedDuration(durationMs: Long): String {
            val totalSeconds = durationMs / 1000
            val seconds = totalSeconds % 60
            val minutes = (totalSeconds / 60) % 60
            val hours = totalSeconds / 3600

            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

        fun bindImage(
            media: MediaData,
            layoutMode: LayoutMode,
            isSelected: Boolean,
            isSelectionMode: Boolean,
            spanCount: Int // Add spanCount parameter
        ) {
            this.isSelectionMode = isSelectionMode
            itemView.findViewById<ImageView>(R.id.ivOptions)?.visibility =
                if (isSelectionMode && adapterPosition == 0) View.VISIBLE else View.GONE


            val context = ivItem.context
            val params = ivItem.layoutParams
            tvDuration.text = formatedDuration(media.duration)
//            if (spanCount == 5 || spanCount == 6) {
//                ivPlayIcon.visibility = View.GONE
//            } else {
//                ivPlayIcon.visibility = View.VISIBLE
//            }

            if (layoutMode == LayoutMode.GRID) {
                val displayMetrics = context.resources.displayMetrics
                val screenWidth = displayMetrics.widthPixels
                val spacing = context.resources.getDimensionPixelSize(R.dimen.grid_spacing)

                // Calculate square dimensions based on spanCount
                val itemSize = (screenWidth - (spacing * (spanCount + 1))) / spanCount

                params.width = itemSize
                params.height = itemSize // Make it square
                ivItem.layoutParams = params
                ivItem.scaleType = ImageView.ScaleType.CENTER_CROP
                ivItem.adjustViewBounds = false

                Glide.with(context)
                    .load(media.uri)
                    .error(R.drawable.ic_video_placeholder)
                    .placeholder(R.drawable.ic_video_placeholder)
                    .override(itemSize, itemSize) // Load square image
                    .into(ivItem)
            } else {
                // For non-grid layouts (like list view)
                params.width = ViewGroup.LayoutParams.MATCH_PARENT
                params.height = ViewGroup.LayoutParams.WRAP_CONTENT
                ivItem.layoutParams = params
                ivItem.scaleType = ImageView.ScaleType.CENTER_CROP
                ivItem.adjustViewBounds = true

                Glide.with(context)
                    .load(media.uri)
                    .error(R.drawable.ic_video_placeholder)
                    .placeholder(R.drawable.ic_video_placeholder)
                    .into(ivItem)
            }

            // Selection styling - only apply padding when selected
            if (isSelected) {
                val padding =
                    context.resources.getDimensionPixelSize(R.dimen.selected_image_padding)
                ivItem.setPadding(padding, padding, padding, padding)
                ivItem.shapeAppearanceModel = ivItem.shapeAppearanceModel
                    .toBuilder()
                    .setAllCorners(
                        CornerFamily.ROUNDED,
                        context.resources.getDimension(R.dimen.selected_image_corner_radius)
                    )
                    .build()
            } else {
                // No padding when not selected
                ivItem.setPadding(0, 0, 0, 0)
                ivItem.background = null
                ivItem.shapeAppearanceModel = ivItem.shapeAppearanceModel
                    .toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, 0f)
                    .build()
            }

            ivSelectOption.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            ivSelectOption.setImageResource(
                if (isSelected) R.drawable.ic_select_active else R.drawable.ic_select_inactive
            )
        }
    }

    private fun isTodayDate(dateStr: String): Boolean {
        return try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
            val date = format.parse(dateStr)
            val today = Calendar.getInstance()
            val cal = Calendar.getInstance()
            cal.time = date!!
            today.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
                    today.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
        } catch (_: Exception) {
            false
        }
    }

    fun getSelectedMedia(): List<MediaData> = selectedMedia.toList()

    fun clearSelection() {
        selectedMedia.clear()
        isSelectionMode = false
        onSelectionModeChange(false)
        onSelectedCountChange(0)
        notifyDataSetChanged()
    }

    fun selectMediaByDate(date: String) {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault())
        val mediaForDate = galleryImgList.filterIsInstance<GalleryListItem.MediaItem>()
            .filter {
                val mediaDate = Date(it.media.dateTaken)
                dateFormat.format(mediaDate) == date
            }
            .map { it.media }
        if (mediaForDate.isEmpty()) return
        val allSelected = mediaForDate.all { selectedMedia.contains(it) }
        if (allSelected) {
            selectedMedia.removeAll(mediaForDate.toSet())
            if (selectedMedia.isEmpty()) {
                isSelectionMode = false
                onSelectionModeChange(false)
            }
        } else {
            selectedMedia.addAll(mediaForDate)
            if (!isSelectionMode) {
                isSelectionMode = true
                onSelectionModeChange(true)
            }
        }
        onSelectedCountChange(selectedMedia.size)
        notifyDataSetChanged()
        val headerPosition = galleryImgList.indexOfFirst {
            it is GalleryListItem.DateHeader && it.date == date
        }
        if (headerPosition != -1) {
            notifyItemChanged(headerPosition)
        }
    }

    fun selectMediaByMonth(date: String) {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val mediaForMonth = galleryImgList.filterIsInstance<GalleryListItem.MediaItem>()
            .filter {
                val mediaDate = Date(it.media.dateTaken)
                monthFormat.format(mediaDate) == date
            }
            .map { it.media }
        if (mediaForMonth.isEmpty()) return
        val allSelected = mediaForMonth.all { selectedMedia.contains(it) }
        if (allSelected) {
            selectedMedia.removeAll(mediaForMonth.toSet())
            if (selectedMedia.isEmpty()) {
                isSelectionMode = false
                onSelectionModeChange(false)
            }
        } else {
            selectedMedia.addAll(mediaForMonth)
            if (!isSelectionMode) {
                isSelectionMode = true
                onSelectionModeChange(true)
            }
        }
        onSelectedCountChange(selectedMedia.size)
        notifyDataSetChanged()
        val headerPosition = galleryImgList.indexOfFirst {
            it is GalleryListItem.DateHeader && it.date == date
        }
        if (headerPosition != -1) {
            notifyItemChanged(headerPosition)
        }
    }


    private fun onChange(position: Int): CharSequence {
        return when (val item = galleryImgList.getOrNull(position)) {
            is GalleryListItem.DateHeader -> item.date
            is GalleryListItem.MediaItem -> {
                var headerPosition = position
                while (headerPosition >= 0) {
                    when (galleryImgList[headerPosition]) {
                        is GalleryListItem.DateHeader -> {
                            return (galleryImgList[headerPosition] as GalleryListItem.DateHeader).date
                        }

                        is GalleryListItem.MediaItem -> headerPosition--
                    }
                }
                ""
            }

            else -> ""
        }
    }

    override fun onUpdate(position: Int, popupTextView: TextView) {
        popupTextView.text = onChange(position)
    }

    fun updateSpanCount(newSpanCount: Int) {
        spanCount = newSpanCount
    }
    private fun rebindVisibleMediaViewHolders() {
        attachedRecyclerView?.let { recyclerView ->
            for (i in 0 until recyclerView.childCount) {
                val child = recyclerView.getChildAt(i)
                val holder = recyclerView.getChildViewHolder(child)
                if (holder is MediaViewHolder) {
                    val position = holder.adapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val item =
                            galleryImgList[position] as? GalleryListItem.MediaItem ?: continue
                        holder.bindImage(
                            media = item.media,
                            layoutMode = layoutMode,
                            isSelected = selectedMedia.contains(item.media),
                            isSelectionMode = isSelectionMode,
                            spanCount = spanCount
                        )
                    }
                }
            }
        }
    }
}
