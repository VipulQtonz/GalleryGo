package com.photogallery.adapter

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.R

class ThemeColorAdapter internal constructor(
    private var context: Context,
    private var selectedPosition: Int = 0,
    colorPickerColors: List<Int>
) : RecyclerView.Adapter<ThemeColorAdapter.ViewHolder0825>() {
    private var inflater: LayoutInflater
    private val colorPickerColors: List<Int>
    private var onColorPickerClickListener: OnThemeColorPickerClickListener? =
        null // Changed to nullable

    internal constructor(context: Context, position: Int) : this(
        context,
        position,
        getDefaultColors(context)
    ) {
        this.context = context
        this.selectedPosition = position
        inflater = LayoutInflater.from(context)
    }

    init {
        inflater = LayoutInflater.from(context)
        this.colorPickerColors = colorPickerColors
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder0825 {
        val view = inflater.inflate(R.layout.theme_color_item, parent, false)
        return ViewHolder0825(view)
    }

    override fun onBindViewHolder(holder: ViewHolder0825, position: Int) {
        val isSelected = position == selectedPosition
        holder.colorPickerView.isSelected = isSelected

        if (isSelected) {
            holder.ivSelect.visibility = View.VISIBLE
        } else {
            holder.ivSelect.visibility = View.GONE
        }

        val backgroundDrawable = ContextCompat.getDrawable(
            context,
            if (isSelected) R.drawable.bg_rounded_corner_color
            else R.drawable.bg_rounded_corner_color
        ) as GradientDrawable

        backgroundDrawable.setColor(colorPickerColors[position])
        holder.colorPickerView.background = backgroundDrawable
    }

    override fun getItemCount(): Int {
        return colorPickerColors.size
    }

    inner class ViewHolder0825(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var colorPickerView: View = itemView.findViewById(R.id.vvColorPicker)
        var ivSelect: ImageView = itemView.findViewById(R.id.ivSelectedIcon)

        init {
            itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = adapterPosition

                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                onColorPickerClickListener?.onColorPickerClickListener(
                    adapterPosition
                )
            }
        }
    }

    interface OnThemeColorPickerClickListener {
        fun onColorPickerClickListener(position: Int)
    }

    fun setOnColorPickerClickListener(listener: OnThemeColorPickerClickListener) {
        this.onColorPickerClickListener = listener
        if (colorPickerColors.isNotEmpty() && selectedPosition == 0) {
            listener.onColorPickerClickListener(0)
        }
    }

    companion object {
        fun getDefaultColors(context: Context): List<Int> {
            val colorPickerColors = ArrayList<Int>()
            colorPickerColors.add(ContextCompat.getColor(context, R.color.theme_color_1))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.theme_color_2))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.theme_color_3))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.theme_color_4))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.theme_color_5))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.theme_color_6))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.theme_color_7))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.theme_color_8))
            return colorPickerColors
        }
    }
}