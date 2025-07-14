package com.photogallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.carousel.MaskableFrameLayout
import com.photogallery.R
import com.photogallery.model.Moment

class MomentsImgAdapter(
    private var imgArrayList: ArrayList<Moment>,
    private val onItemClickListener: OnItemClickListener
) : RecyclerView.Adapter<MomentsImgAdapter.ViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(position: Int, moment: Moment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view: View = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_moments_carousel, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val moment = imgArrayList[position]
        val uri = moment.representativeUri

        Glide.with(holder.imageView.context)
            .load(uri)
            .error(R.drawable.ic_image_placeholder)
            .placeholder(R.drawable.ic_image_placeholder)
            .into(holder.imageView)

        holder.tvTitle.text = moment.title
        holder.caroselItemContainer.isClickable = false
        holder.caroselItemContainer.isFocusable = false
        holder.itemView.setOnClickListener {
            onItemClickListener.onItemClick(position, moment)
        }
    }

    override fun getItemCount(): Int {
        return imgArrayList.size
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var imageView: AppCompatImageView = itemView.findViewById(R.id.ivMomentsCarousel)
        var tvTitle: AppCompatTextView = itemView.findViewById(R.id.tvTitle)
        var caroselItemContainer: MaskableFrameLayout =
            itemView.findViewById(R.id.carousel_item_container)
    }
}