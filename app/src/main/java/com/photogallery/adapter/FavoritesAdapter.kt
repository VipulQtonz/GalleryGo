package com.photogallery.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.databinding.ItemFavoriteBinding
import com.photogallery.db.model.MediaFavoriteData
import com.photogallery.utils.SharedPreferenceHelper
import com.skydoves.balloon.ArrowOrientation

class FavoritesAdapter(
    private val context: Context,
    private val ePreferences: SharedPreferenceHelper,
    private val onItemClick: (MediaFavoriteData) -> Unit,
    private val onUnFavoriteClick: (MediaFavoriteData) -> Unit
) : ListAdapter<MediaFavoriteData, FavoritesAdapter.MediaViewHolder>(MediaDiffCallback()) {

    inner class MediaViewHolder(private val binding: ItemFavoriteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(media: MediaFavoriteData) {
            if (adapterPosition == 0) {
                if (ePreferences.getBoolean("isFirstTimeFavoriteAdapter", true)) {
                    setupTooltip(
                        context,
                        binding.ivFavorite,
                        context.getString(R.string.tap_to_remove_from_favorites),
                        ArrowOrientation.BOTTOM,
                        ePreferences,
                        "isFirstTimeFavoriteAdapter"
                    )
                }
            }
            if (media.isVideo) {
                binding.ivPlayIcon.visibility = View.VISIBLE
                binding.tvDuration.visibility = View.VISIBLE
                binding.tvDuration.text = formatedDuration(media.duration)
                Glide.with(itemView.context)
                    .load(media.uri)
                    .placeholder(R.drawable.ic_video_placeholder)
                    .error(R.drawable.ic_video_placeholder)
                    .thumbnail(0.25f)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(binding.ivGalleryImgItem)
            } else {
                binding.ivPlayIcon.visibility = View.GONE
                binding.tvDuration.visibility = View.GONE
                Glide.with(itemView.context)
                    .load(media.uri)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .thumbnail(0.25f)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(binding.ivGalleryImgItem)
            }
            binding.apply {
                ivFavorite.visibility = View.VISIBLE
                root.setOnClickListener {
                    onItemClick(media) // Launch PhotoViewActivity
                }
                ivFavorite.setOnClickListener {
                    onUnFavoriteClick(media)
                }
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val binding =
            ItemFavoriteBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        val context = binding.ivGalleryImgItem.context
        val params = binding.ivGalleryImgItem.layoutParams
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val columnCount = 3 // Default grid columns
        val spacing = context.resources.getDimensionPixelSize(R.dimen.grid_spacing)

        // Calculate square dimensions
        val itemSize = (screenWidth - (spacing * (columnCount + 1))) / columnCount

        params.width = itemSize
        params.height = itemSize // Make it square
        binding.ivGalleryImgItem.layoutParams = params
        binding.ivGalleryImgItem.scaleType = ImageView.ScaleType.CENTER_CROP
        binding.ivGalleryImgItem.adjustViewBounds = false

        return MediaViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MediaDiffCallback : DiffUtil.ItemCallback<MediaFavoriteData>() {
        override fun areItemsTheSame(
            oldItem: MediaFavoriteData,
            newItem: MediaFavoriteData
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(
            oldItem: MediaFavoriteData,
            newItem: MediaFavoriteData
        ): Boolean {
            return oldItem == newItem
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
}