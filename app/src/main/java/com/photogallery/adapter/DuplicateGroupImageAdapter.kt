package com.photogallery.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photogallery.R
import com.photogallery.model.DuplicateImageGroup

class DuplicateGroupImageAdapter(
    private val context: Context,
    private val uris: List<DuplicateImageGroup>,
    private val onItemClick: (DuplicateImageGroup) -> Unit
) : RecyclerView.Adapter<DuplicateGroupImageAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageViewAlbumCover)
        val imageCount: TextView = itemView.findViewById(R.id.textViewPhotoCount)
        val name: TextView = itemView.findViewById(R.id.textViewAlbumName)

        fun bind(uri: DuplicateImageGroup) {
            itemView.post {
                val width = imageView.width
                if (width > 0) {
                    val layoutParams = imageView.layoutParams
                    layoutParams.height = width
                    imageView.layoutParams = layoutParams
                }
            }

            Glide.with(context)
                .load(uri.allUris.random())
                .error(R.drawable.ic_image_placeholder)
                .placeholder(R.drawable.ic_image_placeholder)
                .into(imageView)
            itemView.setOnClickListener { onItemClick(uri) }
            name.visibility = View.GONE
            imageCount.text = context.getString(R.string.photos_, uri.allUris.size)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_album, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(uris[position])
    }

    override fun getItemCount(): Int = uris.size
}