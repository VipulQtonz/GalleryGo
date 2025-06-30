package com.photogallery.photoEditor.photoEditing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.photogallery.R

class RotateBSFragment : BottomSheetDialogFragment() {

    interface RotateProperties {
        fun onRotate()
        fun onFlipHorizontal()
        fun onFlipVertical()
        fun onRotateSaved()
        fun onRotateCancelled()
    }

    private var rotateListener: RotateProperties? = null
    private var rotationCount: Int = 0 // Track 90-degree rotations (mod 4)
    private var flipHorizontal: Boolean = false
    private var flipVertical: Boolean = false
    private var savedRotationCount: Int = 0
    private var savedFlipHorizontal: Boolean = false
    private var savedFlipVertical: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_rotate_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ivRotate = view.findViewById<LinearLayout>(R.id.llRotate)
        val ivFlipHorizontal = view.findViewById<LinearLayout>(R.id.llFlipHorizontal)
        val ivFlipVertical = view.findViewById<LinearLayout>(R.id.llFlipVertical)
        val tvCancel = view.findViewById<TextView>(R.id.tvCancel)
        val tvSave = view.findViewById<TextView>(R.id.tvDone)

        ivRotate.setOnClickListener {
            rotationCount = (rotationCount + 1) % 4 // Rotate 90 degrees clockwise
            rotateListener?.onRotate()
        }

        ivFlipHorizontal.setOnClickListener {
            flipHorizontal = !flipHorizontal
            rotateListener?.onFlipHorizontal()
        }

        ivFlipVertical.setOnClickListener {
            flipVertical = !flipVertical
            rotateListener?.onFlipVertical()
        }

        tvCancel.setOnClickListener {
            rotateListener?.onRotateCancelled()
            dismiss()
        }

        tvSave.setOnClickListener {
            savedRotationCount = rotationCount
            savedFlipHorizontal = flipHorizontal
            savedFlipVertical = flipVertical
            rotateListener?.onRotateSaved()
            dismiss()
        }
    }

    fun setRotatePropertiesListener(listener: RotateProperties) {
        this.rotateListener = listener
    }

    companion object {
        fun newInstance(): RotateBSFragment {
            return RotateBSFragment()
        }
    }
}