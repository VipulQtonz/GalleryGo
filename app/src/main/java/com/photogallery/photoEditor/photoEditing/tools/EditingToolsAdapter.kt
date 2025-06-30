package com.photogallery.photoEditor.photoEditing.tools

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.R

class EditingToolsAdapter(private val mOnItemSelected: OnItemSelected, context: Context) :
    RecyclerView.Adapter<EditingToolsAdapter.ViewHolder>() {
    private val mToolList: MutableList<ToolModel> = ArrayList()

    interface OnItemSelected {
        fun onToolSelected(toolType: ToolType)
    }

    internal inner class ToolModel(
        val mToolName: String,
        val mToolIcon: Int,
        val mToolType: ToolType
    )

    fun initializeTools(context: Context) {
        mToolList.clear()
        mToolList.add(ToolModel(context.getString(R.string.crop), R.drawable.ic_crop, ToolType.CROP))
        mToolList.add(ToolModel(context.getString(R.string.rotate), R.drawable.ic_rotate, ToolType.ROTATE))
        mToolList.add(ToolModel(context.getString(R.string.filter), R.drawable.ic_filter, ToolType.FILTER))
        mToolList.add(ToolModel(context.getString(R.string.text), R.drawable.ic_text, ToolType.TEXT))
        mToolList.add(ToolModel(context.getString(R.string.blur), R.drawable.ic_blur, ToolType.BLUR))
        mToolList.add(ToolModel(context.getString(R.string.shape), R.drawable.ic_shape, ToolType.SHAPE))
        mToolList.add(ToolModel(context.getString(R.string.sticker), R.drawable.ic_sticker, ToolType.STICKER))
        mToolList.add(ToolModel(context.getString(R.string.eraser), R.drawable.ic_eraser, ToolType.ERASER))
        mToolList.add(ToolModel(context.getString(R.string.emoji), R.drawable.ic_insert_emoticon, ToolType.EMOJI))
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.edit_options_single_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = mToolList[position]
        holder.txtTool.text = item.mToolName
        holder.imgToolIcon.setImageResource(item.mToolIcon)
    }

    override fun getItemCount(): Int {
        return mToolList.size
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imgToolIcon: ImageView = itemView.findViewById(R.id.ivEditOptionIcon)
        val txtTool: TextView = itemView.findViewById(R.id.tvEditOptionText)

        init {
            itemView.setOnClickListener { _: View? ->
                mOnItemSelected.onToolSelected(
                    mToolList[layoutPosition].mToolType
                )
            }
        }
    }
}