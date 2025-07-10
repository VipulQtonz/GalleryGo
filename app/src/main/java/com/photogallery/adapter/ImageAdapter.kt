package com.photogallery.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photogallery.R
import android.content.res.Resources

class ImageAdapter(
    private var imageUris: List<Uri>,
    private val onItemClick: (Uri) -> Unit = {},
    private val onItemLongClick: (Uri) -> Unit = {}
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }


    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val uri = imageUris[position]
        Glide.with(holder.imageView.context)
            .load(uri)
            .thumbnail(0.25f)
            .error(R.drawable.ic_image_placeholder)
            .placeholder(R.drawable.ic_image_placeholder)
            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
            .into(holder.imageView)
        holder.imageView.setOnClickListener { onItemClick(uri) }
        holder.imageView.setOnLongClickListener {
            onItemLongClick(uri)
            true // Consume the long click event
        }
    }

    override fun getItemCount(): Int = imageUris.size

    fun updateUris(newUris: List<Uri>) {
        imageUris = newUris
        notifyDataSetChanged()
    }
}