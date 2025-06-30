package com.photogallery.photoEditor.photoEditing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.photogallery.R

class BlurBSFragment : BottomSheetDialogFragment() {

    interface BlurProperties {
        fun onBlurChanged(blurRadius: Int)
        fun onBlurSaved(blurRadius: Int)
        fun onBlurCancelled()
    }

    private var blurListener: BlurProperties? = null
    private var currentBlurRadius: Int = 1 // Start at 1 to avoid zero
    private var lastSavedBlurRadius: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_blur_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val seekBarBlur = view.findViewById<SeekBar>(R.id.sbBlue)
        val tvCancel = view.findViewById<TextView>(R.id.tvCancel)
        val tvSave = view.findViewById<TextView>(R.id.tvDone)

        seekBarBlur.max = 25
        seekBarBlur.min = 1
        seekBarBlur.progress = currentBlurRadius
        seekBarBlur.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                currentBlurRadius = progress
                blurListener?.onBlurChanged(currentBlurRadius)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        tvCancel.setOnClickListener {
            blurListener?.onBlurCancelled()
            dismiss()
        }

        tvSave.setOnClickListener {
            lastSavedBlurRadius = currentBlurRadius
            blurListener?.onBlurSaved(currentBlurRadius)
            dismiss()
        }
    }

    fun setBlurPropertiesListener(listener: BlurProperties) {
        this.blurListener = listener
    }

    companion object {
        fun newInstance(): BlurBSFragment {
            return BlurBSFragment()
        }
    }
}