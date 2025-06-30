package com.photogallery.adapter

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.google.android.material.imageview.ShapeableImageView
import com.photogallery.R
import com.photogallery.model.Album

class AlbumAdapter(
    private val albums: List<Album>,
    private val context: Context,
    private val onAlbumClick: (Album) -> Unit
) : RecyclerView.Adapter<AlbumAdapter.AlbumViewHolder>() {

    override fun getItemCount() = albums.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlbumViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_album, parent, false)
        val imageView = view.findViewById<ShapeableImageView>(R.id.imageViewAlbumCover)
        imageView.post {
            val width = imageView.width
            if (width > 0) {
                imageView.layoutParams = imageView.layoutParams.apply {
                    height = width
                }
            }
        }
        return AlbumViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlbumViewHolder, position: Int) {
        val album = albums[position]
        holder.itemView.setOnClickListener {
            onAlbumClick(album)
        }
        if (album.isAddAlbum) {
            holder.bindAddAlbum()
        } else {
            holder.bind(album)
        }
    }

    inner class AlbumViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cover = view.findViewById<ShapeableImageView>(R.id.imageViewAlbumCover)
        private val name = view.findViewById<TextView>(R.id.textViewAlbumName)
        private val count = view.findViewById<TextView>(R.id.textViewPhotoCount)

        fun bind(album: Album) {
            name.text = album.name
            count.text = album.photoUris.size.toString()
            if (album.photoUris.isEmpty()) {
                Glide.with(context)
                    .load(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(cover)
            } else {
                Glide.with(context)
                    .load(album.photoUris.firstOrNull())
                    .error(R.drawable.ic_image_placeholder)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(cover)
            }
        }

        fun bindAddAlbum() {
            Glide.with(context)
                .load(R.drawable.ic_add_new_album_placeholder)
                .error(R.drawable.ic_add_new_album_placeholder)
                .transform(RoundedCorners(getRadiusPx()))
                .into(cover)
            name.text = context.getString(R.string.new_album)
            count.text = ""
        }

        private fun getRadiusPx(): Int {
            val radiusDp = 12f
            return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                radiusDp,
                cover.resources.displayMetrics
            ).toInt()
        }
    }
}