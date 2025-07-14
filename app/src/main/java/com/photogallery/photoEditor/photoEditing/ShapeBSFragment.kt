package com.photogallery.photoEditor.photoEditing

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.photogallery.R
import com.photogallery.adapter.ShapeOptionsAdapter
import com.photogallery.photoEditor.photoEditing.ColorPickerAdapter.OnColorPickerClickListener
import com.photogallery.photoEditor.photoEditorHelper.shape.ShapeType
import com.photogallery.model.ShapeOptionItems

class ShapeBSFragment : BottomSheetDialogFragment(), SeekBar.OnSeekBarChangeListener {
    private var mProperties: Properties? = null
    private var selectedColor: Int = 0
    private var selectedOpacity: Int = 100
    private var selectedSize: Int = 20
    private var selectedShape: ShapeType = ShapeType.Brush

    interface Properties {
        fun onColorChanged(colorCode: Int)
        fun onOpacityChanged(opacity: Int)
        fun onShapeSizeChanged(shapeSize: Int)
        fun onShapePicked(shapeType: ShapeType)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_bottom_shapes_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val rvColor: RecyclerView = view.findViewById(R.id.shapeColors)
        val sbOpacity = view.findViewById<SeekBar>(R.id.shapeOpacity)
        val sbBrushSize = view.findViewById<SeekBar>(R.id.shapeSize)
        val rvShape = view.findViewById<RecyclerView>(R.id.shapeOptionsRecyclerView)
        val tvCancel = view.findViewById<TextView>(R.id.tvCancel)
        val tvDone = view.findViewById<TextView>(R.id.tvDone)
        view.findViewById<TextView>(R.id.tvToolName).text = context?.getString(R.string.shape)

        val shapeOptions = listOf(
            ShapeOptionItems(
                R.drawable.ic_shape_brush,
                getString(R.string.brush),
                ShapeType.Brush
            ),
            ShapeOptionItems(
                R.drawable.ic_shape_line,
                getString(R.string.line),
                ShapeType.Line
            ),
            ShapeOptionItems(
                R.drawable.ic_shape_arrow,
                getString(R.string.arrow),
                ShapeType.Arrow()
            ),
            ShapeOptionItems(
                R.drawable.ic_shape_oval,
                getString(R.string.oval),
                ShapeType.Oval
            ),
            ShapeOptionItems(
                R.drawable.ic_shape_rectangle,
                getString(R.string.rectangle),
                ShapeType.Rectangle
            )
        )

        val shapeAdapter = ShapeOptionsAdapter(shapeOptions).apply {
            setOnItemClickListener { position ->
                selectedShape = shapeOptions[position].shapeType
                notifyDataSetChanged()
            }
        }
        rvShape.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvShape.adapter = shapeAdapter

        sbOpacity.max = 100
        sbOpacity.progress = selectedOpacity
        sbBrushSize.max = 100
        sbBrushSize.progress = selectedSize

        tvDone.setOnClickListener {
            saveChanges()
            dismiss()
        }

        tvCancel.setOnClickListener {
            dismiss()
        }

        sbOpacity.setOnSeekBarChangeListener(this)
        sbBrushSize.setOnSeekBarChangeListener(this)

        val activity = requireActivity()
        val layoutManager = LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
        rvColor.layoutManager = layoutManager
        val colorPickerAdapter = ColorPickerAdapter(activity)
        colorPickerAdapter.setOnColorPickerClickListener(object : OnColorPickerClickListener {
            override fun onColorPickerClickListener(colorCode: Int) {
                selectedColor = colorCode
            }
        })
        rvColor.adapter = colorPickerAdapter
    }

    fun setPropertiesChangeListener(properties: Properties?) {
        mProperties = properties
    }

    override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
        when (seekBar.id) {
            R.id.shapeOpacity -> {
                selectedOpacity = i
            }

            R.id.shapeSize -> {
                selectedSize = i
            }
        }
    }

    private fun saveChanges() {
        mProperties?.apply {
            onColorChanged(selectedColor)
            onOpacityChanged(selectedOpacity)
            onShapeSizeChanged(selectedSize)
            onShapePicked(selectedShape)
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

    override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
}