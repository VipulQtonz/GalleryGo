package com.photogallery.photoEditor.photoEditing

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.photogallery.R

class EmojiBSFragment : BottomSheetDialogFragment() {

    private var mEmojiListener: EmojiListener? = null
    private lateinit var emojisList: ArrayList<String> // ✅ Lazy initialization

    interface EmojiListener {
        fun onEmojiClick(emojiUnicode: String)
    }

    private val mBottomSheetBehaviorCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                dismiss()
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)

        val contentView = View.inflate(context, R.layout.fragment_bottom_sticker_emoji_dialog, null)
        dialog.setContentView(contentView)

        val params = (contentView.parent as View).layoutParams as CoordinatorLayout.LayoutParams
        val behavior = params.behavior
        if (behavior is BottomSheetBehavior<*>) {
            behavior.addBottomSheetCallback(mBottomSheetBehaviorCallback)
        }
        (contentView.parent as View).setBackgroundColor(resources.getColor(android.R.color.transparent))

        val rvEmoji: RecyclerView = contentView.findViewById(R.id.rvEmoji)
        rvEmoji.layoutManager = GridLayoutManager(activity, 5)

        emojisList = getEmojis(requireContext())
        val emojiAdapter = EmojiAdapter()
        rvEmoji.adapter = emojiAdapter
        rvEmoji.setHasFixedSize(true)
        rvEmoji.setItemViewCacheSize(emojisList.size)
    }

    fun setEmojiListener(emojiListener: EmojiListener?) {
        mEmojiListener = emojiListener
    }

    inner class EmojiAdapter : RecyclerView.Adapter<EmojiAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.row_emoji, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.txtEmoji.text = emojisList[position]
        }

        override fun getItemCount(): Int {
            return emojisList.size
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val txtEmoji: TextView = itemView.findViewById(R.id.txtEmoji)

            init {
                itemView.setOnClickListener {
                    mEmojiListener?.onEmojiClick(emojisList[layoutPosition])
                    dismiss()
                }
            }
        }
    }

    companion object {
        fun getEmojis(context: Context): ArrayList<String> {
            val convertedEmojiList = ArrayList<String>()
            val emojiArray = context.resources.getStringArray(R.array.photo_editor_emoji)
            for (emojiUnicode in emojiArray) {
                convertedEmojiList.add(convertEmoji(emojiUnicode))
            }
            return convertedEmojiList
        }

        private fun convertEmoji(emoji: String): String {
            return try {
                val convertEmojiToInt = emoji.substring(2).toInt(16)
                String(Character.toChars(convertEmojiToInt))
            } catch (_: NumberFormatException) {
                ""
            }
        }
    }
}
