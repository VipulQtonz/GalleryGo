package com.photogallery.photoEditor.photoEditing

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.R
import com.photogallery.photoEditor.photoEditing.ColorPickerAdapter.OnColorPickerClickListener

class TextEditorDialogFragment : DialogFragment() {
    private lateinit var mAddTextEditText: EditText
    private lateinit var mAddTextDoneTextView: TextView
    private lateinit var mInputMethodManager: InputMethodManager
    private var mColorCode = 0
    private var mTextEditorListener: TextEditorListener? = null

    interface TextEditorListener {
        fun onDone(inputText: String, colorCode: Int)
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            val width = ViewGroup.LayoutParams.MATCH_PARENT
            val height = ViewGroup.LayoutParams.MATCH_PARENT
            dialog.window!!.setLayout(width, height)
            dialog.window!!.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.add_text_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity()

        mAddTextEditText = view.findViewById(R.id.edtAddText)
        mInputMethodManager = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        mAddTextDoneTextView = view.findViewById(R.id.tvAddTextDone)

        // Ensure EditText is focusable and request focus
        mAddTextEditText.isFocusable = true
        mAddTextEditText.isFocusableInTouchMode = true
        mAddTextEditText.requestFocus()

        // Force show the soft keyboard
        mInputMethodManager.showSoftInput(mAddTextEditText, InputMethodManager.SHOW_FORCED)

        // Setup the color picker for text color
        val addTextColorPickerRecyclerView: RecyclerView = view.findViewById(R.id.rwAddTextColorPicker)
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        addTextColorPickerRecyclerView.layoutManager = layoutManager
        val colorPickerAdapter = ColorPickerAdapter(activity)

        // This listener will change the text color when clicked on any color from picker
        colorPickerAdapter.setOnColorPickerClickListener(object : OnColorPickerClickListener {
            override fun onColorPickerClickListener(colorCode: Int) {
                mColorCode = colorCode
                mAddTextEditText.setTextColor(colorCode)
            }
        })

        addTextColorPickerRecyclerView.adapter = colorPickerAdapter

        val arguments = requireArguments()

        mAddTextEditText.setText(arguments.getString(EXTRA_INPUT_TEXT))
        mColorCode = arguments.getInt(EXTRA_COLOR_CODE)
        mAddTextEditText.setTextColor(mColorCode)

        // Make a callback on activity when user is done with text editing
        mAddTextDoneTextView.setOnClickListener { onClickListenerView ->
            mInputMethodManager.hideSoftInputFromWindow(onClickListenerView.windowToken, 0)
            dismiss()
            val inputText = mAddTextEditText.text.toString()
            val textEditorListener = mTextEditorListener
            if (inputText.isNotEmpty() && textEditorListener != null) {
                textEditorListener.onDone(inputText, mColorCode)
            }
        }
    }

    // Callback to listener if user is done with text editing
    fun setOnTextEditorListener(textEditorListener: TextEditorListener) {
        mTextEditorListener = textEditorListener
    }

    companion object {
        private val TAG: String = TextEditorDialogFragment::class.java.simpleName
        const val EXTRA_INPUT_TEXT = "extra_input_text"
        const val EXTRA_COLOR_CODE = "extra_color_code"

        @JvmOverloads
        fun show(
            appCompatActivity: AppCompatActivity,
            inputText: String = "",
            @ColorInt colorCode: Int = ContextCompat.getColor(appCompatActivity, R.color.white)
        ): TextEditorDialogFragment {
            val args = Bundle()
            args.putString(EXTRA_INPUT_TEXT, inputText)
            args.putInt(EXTRA_COLOR_CODE, colorCode)
            val fragment = TextEditorDialogFragment()
            fragment.arguments = args
            fragment.show(appCompatActivity.supportFragmentManager, TAG)
            return fragment
        }
    }
}