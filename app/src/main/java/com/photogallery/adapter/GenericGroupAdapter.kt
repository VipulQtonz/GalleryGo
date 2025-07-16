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
import com.photogallery.databinding.ItemFaceGroupBinding
import com.photogallery.model.DocumentGroup
import com.photogallery.model.GroupedLocationPhoto
import com.photogallery.model.PeopleGroup
import com.photogallery.process.FaceGroupingUtils
import com.photogallery.utils.Const.TYPE_FACE_GROUP
import com.photogallery.utils.Const.TYPE_OTHER_GROUP

class GenericGroupAdapter(
    private val context: Context,
    private val onItemClick: (Any) -> Unit
) : ListAdapter<Any, RecyclerView.ViewHolder>(GroupDiffCallback()) {

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is FaceGroupingUtils.FaceGroup -> TYPE_FACE_GROUP
            else -> TYPE_OTHER_GROUP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_FACE_GROUP -> {
                val binding = ItemFaceGroupBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                FaceGroupViewHolder(binding)
            }

            else -> {
                val binding = ItemAlbumSeeAllBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                binding.imageViewAlbumCover.post {
                    val width = binding.imageViewAlbumCover.width
                    if (width > 0) {
                        binding.imageViewAlbumCover.layoutParams.height = width
                    }
                }
                OtherGroupViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val group = getItem(position)
        when (holder) {
            is FaceGroupViewHolder -> holder.bind(group as FaceGroupingUtils.FaceGroup)
            is OtherGroupViewHolder -> holder.bind(group)
        }
    }

    inner class FaceGroupViewHolder(private val binding: ItemFaceGroupBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(group: FaceGroupingUtils.FaceGroup) {
            Glide.with(context)
                .load(group.representativeUri)
                .circleCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(binding.imageView)

            binding.root.setOnClickListener { onItemClick(group) }
        }
    }

    inner class OtherGroupViewHolder(private val binding: ItemAlbumSeeAllBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(group: Any) {
            val name = when (group) {
                is GroupedLocationPhoto -> group.locationName ?: "Unknown Location"
                is DocumentGroup -> group.name
                else -> ""
            }

            val uri = when (group) {
                is GroupedLocationPhoto -> group.representativeUri
                is DocumentGroup -> group.allUris.firstOrNull()
                else -> null
            }

            val count = when (group) {
                is GroupedLocationPhoto -> group.allUris.size
                is DocumentGroup -> group.allUris.size
                else -> 0
            }

            binding.textViewAlbumName.text = name
            binding.textViewPhotoCount.text = context.getString(R.string.photos_, count)

            Glide.with(context)
                .load(uri)
                .apply(RequestOptions().transform(CenterCrop()))
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_placeholder)
                .into(binding.imageViewAlbumCover)

            binding.root.setOnClickListener { onItemClick(group) }
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

                oldItem is FaceGroupingUtils.FaceGroup && newItem is FaceGroupingUtils.FaceGroup ->
                    oldItem.groupId == newItem.groupId

                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is GroupedLocationPhoto && newItem is GroupedLocationPhoto ->
                    oldItem == newItem

                oldItem is PeopleGroup && newItem is PeopleGroup ->
                    oldItem == newItem

                oldItem is DocumentGroup && newItem is DocumentGroup ->
                    oldItem == newItem

                oldItem is FaceGroupingUtils.FaceGroup && newItem is FaceGroupingUtils.FaceGroup ->
                    oldItem == newItem

                else -> false
            }
        }
    }
}