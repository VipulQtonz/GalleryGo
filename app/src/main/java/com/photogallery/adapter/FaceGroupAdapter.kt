package com.photogallery.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.photogallery.R
import com.photogallery.databinding.ItemFaceGroupBinding
import com.photogallery.process.FaceGroupingUtils

class FaceGroupAdapter(
    private var faceGroups: List<FaceGroupingUtils.FaceGroup>,
    private val context: Context,
    private val onClick: (FaceGroupingUtils.FaceGroup) -> Unit
) : RecyclerView.Adapter<FaceGroupAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFaceGroupBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemFaceGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = faceGroups[position]
        with(holder.binding) {
            val representativeUri = group.uris.firstOrNull()
            if (representativeUri != null) {
                Glide.with(context).load(representativeUri).into(imageView)
            } else {
                imageView.setImageResource(android.R.color.transparent)
            }
            root.setOnClickListener { onClick(group) }
        }
    }

    override fun getItemCount(): Int = faceGroups.size

    fun updateData(newGroups: List<FaceGroupingUtils.FaceGroup>) {
        faceGroups = newGroups
        notifyDataSetChanged()
    }
}