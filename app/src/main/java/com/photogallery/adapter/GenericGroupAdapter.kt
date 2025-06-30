package com.photogallery.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.request.RequestOptions
import com.photogallery.R
import com.photogallery.databinding.ItemAlbumSeeAllBinding
import com.photogallery.model.DocumentGroup
import com.photogallery.model.GroupedLocationPhoto
import com.photogallery.model.PeopleGroup

class GenericGroupAdapter(
    private val context: Context,
    private val onItemClick: (Any) -> Unit
) : ListAdapter<Any, GenericGroupAdapter.GroupViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemAlbumSeeAllBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        val imageView = binding.imageViewAlbumCover
        imageView.post {
            val width = imageView.width
            if (width > 0) {
                imageView.layoutParams = imageView.layoutParams.apply {
                    height = width
                }
            }
        }
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = getItem(position)
        holder.bind(group)
    }

    inner class GroupViewHolder(private val binding: ItemAlbumSeeAllBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(group: Any) {
            val name = when (group) {
                is GroupedLocationPhoto -> group.locationName ?: "Unknown Location"
                is PeopleGroup -> context.getString(R.string.face_group, group.groupId)
                is DocumentGroup -> group.name
                else -> ""
            }
            val uri = when (group) {
                is GroupedLocationPhoto -> group.representativeUri
                is PeopleGroup -> group.allUris.firstOrNull()
                is DocumentGroup -> group.allUris.firstOrNull()
                else -> null
            }
            val count = when (group) {
                is GroupedLocationPhoto -> group.allUris.size
                is PeopleGroup -> group.allUris.size
                is DocumentGroup -> group.allUris.size
                else -> 0
            }

            binding.textViewAlbumName.text = name
            binding.textViewPhotoCount.text = context.getString(R.string.photos_, count)

            uri?.let {
                Glide.with(context)
                    .load(it)
                    .error(R.drawable.ic_image_placeholder)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .apply(RequestOptions().transform(CenterCrop()))
                    .into(binding.imageViewAlbumCover)
            }

            binding.root.setOnClickListener {
                onItemClick(group)
            }
        }
    }

    class GroupDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is GroupedLocationPhoto && newItem is GroupedLocationPhoto ->
                    oldItem.locationName == newItem.locationName
                oldItem is PeopleGroup && newItem is PeopleGroup ->
                    oldItem.groupId == newItem.groupId
                oldItem is DocumentGroup && newItem is DocumentGroup ->
                    oldItem.name == newItem.name
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is GroupedLocationPhoto && newItem is GroupedLocationPhoto ->
                    oldItem.locationName == newItem.locationName &&
                            oldItem.representativeUri == newItem.representativeUri &&
                            oldItem.allUris == newItem.allUris &&
                            oldItem.latitude == newItem.latitude &&
                            oldItem.longitude == newItem.longitude
                oldItem is PeopleGroup && newItem is PeopleGroup ->
                    oldItem.groupId == newItem.groupId &&
                            oldItem.allUris == newItem.allUris
                oldItem is DocumentGroup && newItem is DocumentGroup ->
                    oldItem.name == newItem.name &&
                            oldItem.allUris == newItem.allUris
                else -> false
            }
        }
    }
}