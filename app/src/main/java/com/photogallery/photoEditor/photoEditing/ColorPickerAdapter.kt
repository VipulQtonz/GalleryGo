package com.photogallery.photoEditor.photoEditing

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.R

class ColorPickerAdapter internal constructor(
    private var context: Context,
    colorPickerColors: List<Int>
) : RecyclerView.Adapter<ColorPickerAdapter.ViewHolder0825>() {
    private var inflater: LayoutInflater
    private val colorPickerColors: List<Int>
    private var onColorPickerClickListener: OnColorPickerClickListener? =
        null // Changed to nullable
    private var selectedPosition = 0 // Default to 0th index

    internal constructor(context: Context) : this(context, getDefaultColors(context)) {
        this.context = context
        inflater = LayoutInflater.from(context)
    }

    init {
        inflater = LayoutInflater.from(context)
        this.colorPickerColors = colorPickerColors
        // Removed premature listener call
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder0825 {
        val view = inflater.inflate(R.layout.color_picker_item_list, parent, false)
        return ViewHolder0825(view)
    }

    override fun onBindViewHolder(holder: ViewHolder0825, position: Int) {
        val isSelected = position == selectedPosition
        holder.colorPickerView.isSelected = isSelected

        val backgroundDrawable = ContextCompat.getDrawable(
            context,
            if (isSelected) R.drawable.bg_rounded_corner_color_selected
            else R.drawable.bg_rounded_corner_color
        ) as GradientDrawable

        backgroundDrawable.setColor(colorPickerColors[position])
        holder.colorPickerView.background = backgroundDrawable
    }

    override fun getItemCount(): Int {
        return colorPickerColors.size
    }

    fun setOnColorPickerClickListener(listener: OnColorPickerClickListener) {
        this.onColorPickerClickListener = listener
        // Notify listener of default selection when listener is set
        if (colorPickerColors.isNotEmpty() && selectedPosition == 0) {
            listener.onColorPickerClickListener(colorPickerColors[0])
        }
    }

    inner class ViewHolder0825(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var colorPickerView: View = itemView.findViewById(R.id.colorPickerView)

        init {
            itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = adapterPosition

                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                onColorPickerClickListener?.onColorPickerClickListener(
                    colorPickerColors[adapterPosition]
                )
            }
        }
    }

    interface OnColorPickerClickListener {
        fun onColorPickerClickListener(colorCode: Int)
    }

    companion object {
        fun getDefaultColors(context: Context): List<Int> {
            val colorPickerColors = ArrayList<Int>()
            colorPickerColors.add(ContextCompat.getColor(context, R.color.black))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.blue_color_picker))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.brown_color_picker))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.green_color_picker))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.orange_color_picker))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.red_color_picker))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.red_orange_color_picker))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.sky_blue_color_picker))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.violet_color_picker))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.white))
            colorPickerColors.add(ContextCompat.getColor(context, R.color.yellow_color_picker))
            colorPickerColors.add(
                ContextCompat.getColor(
                    context,
                    R.color.yellow_green_color_picker
                )
            )
            return colorPickerColors
        }
    }
}