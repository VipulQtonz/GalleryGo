package com.photogallery.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.photogallery.R
import com.photogallery.model.GroupedLocationPhoto

class LocationPhotosAdapter(
    private var groupedLocationPhotos: List<GroupedLocationPhoto>,
    private val context: Context,
    private val onClick: (GroupedLocationPhoto) -> Unit
) : RecyclerView.Adapter<LocationPhotosAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ShapeableImageView = itemView.findViewById(R.id.imageView)
        val locationText: TextView = itemView.findViewById(R.id.locationText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_photo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val groupedPhoto = groupedLocationPhotos[position]

        Glide.with(context)
            .load(groupedPhoto.allUris[0])
            .thumbnail(0.1f)
            .error(R.drawable.ic_image_placeholder)
            .placeholder(R.drawable.ic_image_placeholder)
            .into(holder.imageView)

        holder.locationText.text = groupedPhoto.locationName ?: context.getString(R.string.unknown_location)
        holder.locationText.visibility = if (groupedPhoto.locationName != null) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener { onClick(groupedPhoto) }
    }

    fun updateData(newGroupedPhotos: List<GroupedLocationPhoto>) {
        groupedLocationPhotos = newGroupedPhotos
        notifyDataSetChanged()
    }

    override fun getItemCount() = groupedLocationPhotos.size
}