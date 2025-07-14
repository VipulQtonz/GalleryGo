package com.photogallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.CornerFamily
import com.photogallery.R
import com.photogallery.model.MediaData

class RecyclerBinAdapter(
    private val onItemClick: (MediaData) -> Unit,
    private val onSelectionModeChange: (Boolean) -> Unit,
    private val onSelectionCountChange: (Int) -> Unit // NEW
) : ListAdapter<MediaData, RecyclerBinAdapter.ViewHolder>(MediaDiffCallback()) {

    private val selectedMedia = mutableSetOf<MediaData>()
    private var isSelectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_media_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = getItem(position)
        val isSelected = selectedMedia.contains(media)
        holder.bind(media, isSelected, isSelectionMode)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ShapeableImageView = itemView.findViewById(R.id.ivGalleryImgItem)
        private val ivSelect: ImageView = itemView.findViewById(R.id.ivSelectOption)
        private val ivRemainDays: TextView = itemView.findViewById(R.id.tvDuration)
        private val ivPlayIcon: AppCompatImageView = itemView.findViewById(R.id.ivPlayIcon)

        fun bind(media: MediaData, isSelected: Boolean, isSelectionMode: Boolean) {
            val context = imageView.context
            val params = imageView.layoutParams
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val columnCount = 3
            val spacing = context.resources.getDimensionPixelSize(R.dimen.grid_spacing)

            val itemSize = (screenWidth - (spacing * (columnCount + 1))) / columnCount

            params.width = itemSize
            params.height = itemSize
            imageView.layoutParams = params
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.adjustViewBounds = false

            if (media.isVideo) {
                Glide.with(context)
                    .load(media.path)
                    .placeholder(R.drawable.ic_video_placeholder)
                    .error(R.drawable.ic_video_placeholder)
                    .into(imageView)
                ivPlayIcon.visibility = View.VISIBLE
            } else {
                Glide.with(context)
                    .load(media.path)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(imageView)
                ivPlayIcon.visibility = View.GONE
            }

            if (isSelected) {
                val padding =
                    context.resources.getDimensionPixelSize(R.dimen.selected_image_padding)
                imageView.setPadding(padding, padding, padding, padding)
                imageView.shapeAppearanceModel = imageView.shapeAppearanceModel
                    .toBuilder()
                    .setAllCorners(
                        CornerFamily.ROUNDED,
                        context.resources.getDimension(R.dimen.selected_image_corner_radius)
                    )
                    .build()
            } else {
                imageView.setPadding(0, 0, 0, 0)
                imageView.background = null
                imageView.shapeAppearanceModel = imageView.shapeAppearanceModel
                    .toBuilder()
                    .setAllCorners(CornerFamily.ROUNDED, 0f)
                    .build()
            }

            ivSelect.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            ivRemainDays.visibility = View.VISIBLE
            ivSelect.setImageResource(
                if (isSelected) R.drawable.ic_select_active else R.drawable.ic_select_inactive
            )

            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(media)
                } else {
                    onItemClick(media)
                }
            }

            itemView.setOnLongClickListener {
                if (!this@RecyclerBinAdapter.isSelectionMode) {
                    this@RecyclerBinAdapter.isSelectionMode = true
                    onSelectionModeChange(true)
                    notifyDataSetChanged()
                }
                toggleSelection(media)
                true
            }

            ivSelect.setOnClickListener { toggleSelection(media) }
            ivRemainDays.text = context.getString(R.string.days, media.daysRemaining.toString())
        }
    }

    private fun toggleSelection(media: MediaData) {
        val wasInSelectionMode = isSelectionMode

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

        onSelectionCountChange(selectedMedia.size)

        if (wasInSelectionMode != isSelectionMode) {
            notifyDataSetChanged()
        } else {
            val index = currentList.indexOf(media)
            if (index != -1) {
                notifyItemChanged(index)
            }
        }
    }


    fun selectAll() {
        selectedMedia.clear()
        selectedMedia.addAll(currentList)
        isSelectionMode = true
        onSelectionModeChange(true)
        onSelectionCountChange(selectedMedia.size)
        notifyDataSetChanged()
    }

    fun getSelectedMedia(): List<MediaData> = selectedMedia.toList()
    fun clearSelection() {
        selectedMedia.clear()
        isSelectionMode = false
        onSelectionModeChange(false)

        onSelectionCountChange(0)
        notifyDataSetChanged()
    }
}

class MediaDiffCallback : DiffUtil.ItemCallback<MediaData>() {
    override fun areItemsTheSame(oldItem: MediaData, newItem: MediaData): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: MediaData, newItem: MediaData): Boolean {
        return oldItem == newItem
    }
}