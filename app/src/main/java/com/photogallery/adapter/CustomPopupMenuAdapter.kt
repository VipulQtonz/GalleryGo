package com.photogallery.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import com.photogallery.R
import com.photogallery.model.PopupMenuItem

class CustomPopupMenuAdapter(
    private val context: Context,
    private var selectedId: Int,
    private val menuItems: List<PopupMenuItem>
) : ArrayAdapter<PopupMenuItem>(context, 0, menuItems) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_custom_popup_menu, parent, false)
        val menuItem = getItem(position)!!

        val icon = view.findViewById<ImageView>(R.id.ivMenuIcon)
        val title = view.findViewById<TextView>(R.id.tvMenuTitle)
        icon.setImageResource(menuItem.iconRes)
        title.text = menuItem.title

        icon.isSelected = menuItem.itemId == selectedId
        title.isSelected = menuItem.itemId == selectedId

        return view
    }
}
