package com.photogallery.adapter

import android.media.MediaMetadataRetriever
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
import com.photogallery.activity.LockedImagesActivity
import java.io.File

class LockedMediaAdapter(
    private val context: LockedImagesActivity,
    private val onItemClick: (File) -> Unit,
    private val onSelectionModeChange: (Boolean) -> Unit,
    private val onSelectionCountChange: (Int) -> Unit
) : ListAdapter<File, LockedMediaAdapter.ViewHolder>(MediaDiffCallbackNew()) {

    private val selectedMedia = mutableSetOf<File>()
    private var isSelectionMode = false

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_media_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val media = getItem(position)
        if (!media.exists() || !media.canRead()) {
            holder.imageView.setImageResource(R.drawable.ic_image_placeholder)
            return
        }
        val isSelected = selectedMedia.contains(media)
        holder.bind(media, isSelected, isSelectionMode)
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ShapeableImageView = itemView.findViewById(R.id.ivGalleryImgItem)
        private val ivSelect: ImageView = itemView.findViewById(R.id.ivSelectOption)
        private val ivPlayIcon: AppCompatImageView = itemView.findViewById(R.id.ivPlayIcon)
        private val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)

        fun bind(media: File, isSelected: Boolean, isSelectionMode: Boolean) {

            val originalName = media.name.removeSuffix(".lockimg")
            val newFile = File(originalName)

            val context = imageView.context
            val params = imageView.layoutParams
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val columnCount = 3 // Default grid columns
            val spacing = context.resources.getDimensionPixelSize(R.dimen.grid_spacing)

            val itemSize = (screenWidth - (spacing * (columnCount + 1))) / columnCount

            params.width = itemSize
            params.height = itemSize // Make it square
            imageView.layoutParams = params
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.adjustViewBounds = false

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
            ivSelect.setImageResource(
                if (isSelected) R.drawable.ic_select_active else R.drawable.ic_select_inactive
            )

            val isVideo = isVideoFile(newFile)
            if (isVideo) {
                ivPlayIcon.visibility = View.VISIBLE
                tvDuration.visibility = View.VISIBLE
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(media.path)
                    val durationStr =
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durationMs = durationStr?.toLongOrNull() ?: 0L

                    tvDuration.text = formatedDuration(durationMs)
                    retriever.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                    ivPlayIcon.visibility = View.GONE
                    tvDuration.visibility = View.GONE
                }
                Glide.with(context).load(media).placeholder(R.drawable.ic_video_placeholder)
                    .error(R.drawable.ic_video_placeholder).into(imageView)
            } else {
                Glide.with(context).load(media).placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder).into(imageView)
            }


            itemView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(media)
                } else {
                    onItemClick(media)
                }
            }

            itemView.setOnLongClickListener {
                if (!this@LockedMediaAdapter.isSelectionMode) {
                    this@LockedMediaAdapter.isSelectionMode = true
                    onSelectionModeChange(true)
                    notifyDataSetChanged()
                }
                toggleSelection(media)
                true
            }

            ivSelect.setOnClickListener { toggleSelection(media) }
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

    private fun toggleSelection(media: File) {
        val index = currentList.indexOf(media)
        if (index == -1) return // Item not found

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
            notifyItemChanged(index)
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

    fun getSelectedMedia(): List<File> = selectedMedia.toList()

    fun clearSelection() {
        selectedMedia.clear()
        isSelectionMode = false
        onSelectionModeChange(false)
        onSelectionCountChange(0)
        notifyDataSetChanged()
    }

    private fun isVideoFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf(
            "mp4", "mkv", "webm", "avi", "3gp", "mov",
            "flv", "wmv", "mpeg", "mpg", "m4v", "mts",
            "m2ts", "vob", "ts", "mxf", "rm", "rmvb", "asf", "f4v"
        )
    }
}

class MediaDiffCallbackNew : DiffUtil.ItemCallback<File>() {
    override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem.path == newItem.path
    }

    override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {
        return oldItem == newItem
    }
}