package com.photogallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.R
import com.photogallery.model.ShapeOptionItems

class ShapeOptionsAdapter(
    private val options: List<ShapeOptionItems>
) : RecyclerView.Adapter<ShapeOptionsAdapter.ShapeOptionViewHolder>() {

    private var selectedPosition = 0
    private var itemClickListener: ((Int) -> Unit)? = null

    fun setOnItemClickListener(listener: (Int) -> Unit) {
        itemClickListener = listener
    }

    inner class ShapeOptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.ivEditOptionIcon)
        val label: TextView = itemView.findViewById(R.id.tvEditOptionText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShapeOptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.shape_single_item, parent, false)
        return ShapeOptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShapeOptionViewHolder, position: Int) {
        val option = options[position]
        holder.icon.setImageResource(option.iconResId)
        holder.label.text = option.label
        holder.itemView.isSelected = position == selectedPosition
        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = position
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            itemClickListener?.invoke(position)
        }
    }

    override fun getItemCount(): Int = options.size
}