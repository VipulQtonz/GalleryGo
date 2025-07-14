package com.photogallery.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.photogallery.R
import com.photogallery.model.DocumentGroup

class DocumentAdapter(
    private var documentGroups: List<DocumentGroup>,
    private val context: Context,
    private val onItemClick: (DocumentGroup) -> Unit
) : RecyclerView.Adapter<DocumentAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val textView: TextView = itemView.findViewById(R.id.locationText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_location_photo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = documentGroups[position]
        holder.textView.text = group.name

        Glide.with(context).clear(holder.imageView)
        holder.imageView.setImageDrawable(null)

        if (group.allUris.isNotEmpty()) {
            Glide.with(context)
                .load(group.allUris[0])
                .apply(
                    RequestOptions()
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                )
                .into(holder.imageView)
        } else {
            holder.imageView.setImageResource(R.drawable.ic_image_placeholder)
        }

        holder.itemView.setOnClickListener { onItemClick(group) }
    }

    override fun getItemCount(): Int = documentGroups.size

    fun updateData(newGroups: List<DocumentGroup>) {
        documentGroups = newGroups
        notifyDataSetChanged()
    }
}