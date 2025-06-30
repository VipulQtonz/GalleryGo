package com.photogallery.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.model.EditOptionItems

class EditOptionsAdapter(
    private val options: List<EditOptionItems>
) : RecyclerView.Adapter<EditOptionsAdapter.EditOptionViewHolder>() {

    private var itemClickListener: ((Int) -> Unit)? = null
    fun setOnItemClickListener(listener: (Int) -> Unit) {
        itemClickListener = listener
    }

    inner class EditOptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.ivEditOptionIcon)
        val label: TextView = itemView.findViewById(R.id.tvEditOptionText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EditOptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.edit_options_single_item, parent, false)
        return EditOptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: EditOptionViewHolder, position: Int) {
        val option = options[position]
        holder.icon.setImageResource(option.iconResId)
        holder.label.text = option.label
        holder.itemView.setOnClickListener {
            MyApplication.instance.preventTwoClick(holder.itemView)
            itemClickListener?.invoke(position)
        }
    }

    override fun getItemCount(): Int = options.size
}
