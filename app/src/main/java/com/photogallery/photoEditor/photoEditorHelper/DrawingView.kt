package com.photogallery.photoEditor.photoEditorHelper

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.photogallery.photoEditor.photoEditorHelper.shape.AbstractShape
import com.photogallery.photoEditor.photoEditorHelper.shape.BrushShape
import com.photogallery.photoEditor.photoEditorHelper.shape.LineShape
import com.photogallery.photoEditor.photoEditorHelper.shape.OvalShape
import com.photogallery.photoEditor.photoEditorHelper.shape.RectangleShape
import com.photogallery.photoEditor.photoEditorHelper.shape.ShapeAndPaint
import com.photogallery.photoEditor.photoEditorHelper.shape.ShapeBuilder
import com.photogallery.photoEditor.photoEditorHelper.shape.ShapeType
import java.util.Stack

class DrawingView @JvmOverloads constructor(
    context: Context?,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {
    private val drawShapes = Stack<ShapeAndPaint?>()
    private val redoShapes = Stack<ShapeAndPaint?>()
    internal var currentShape: ShapeAndPaint? = null
    var isDrawingEnabled = false
        private set
    private var viewChangeListener: BrushViewChangeListener? = null
    var currentShapeBuilder: ShapeBuilder

    private var isErasing = false
    var eraserSize = DEFAULT_ERASER_SIZE

    private fun createPaint(): Paint {
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isDither = true
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeCap = Paint.Cap.ROUND
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)

        currentShapeBuilder.apply {
            paint.strokeWidth = this.shapeSize
            paint.color = this.shapeColor
            shapeOpacity?.also { paint.alpha = it }
        }
        return paint
    }

    private fun createEraserPaint(): Paint {
        val paint = createPaint()
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        paint.strokeWidth = eraserSize
        return paint
    }

    fun clearAll() {
        drawShapes.clear()
        redoShapes.clear()
        invalidate()
    }

    fun setBrushViewChangeListener(brushViewChangeListener: BrushViewChangeListener?) {
        viewChangeListener = brushViewChangeListener
    }

    public override fun onDraw(canvas: Canvas) {
        for (shape in drawShapes) {
            shape?.shape?.draw(canvas, shape.paint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (isDrawingEnabled) {
            val touchX = event.x
            val touchY = event.y
            when (event.action) {
                MotionEvent.ACTION_DOWN -> onTouchEventDown(touchX, touchY)
                MotionEvent.ACTION_MOVE -> onTouchEventMove(touchX, touchY)
                MotionEvent.ACTION_UP -> onTouchEventUp()
            }
            invalidate()
            true
        } else {
            false
        }
    }

    private fun onTouchEventDown(touchX: Float, touchY: Float) {
        createShape()
        currentShape?.shape?.startShape(touchX, touchY)
    }

    private fun onTouchEventMove(touchX: Float, touchY: Float) {
        currentShape?.shape?.moveShape(touchX, touchY)
    }

    private fun onTouchEventUp() {
        currentShape?.apply {
            shape.stopShape()
            endShape()
        }
    }

    private fun createShape() {
        var paint = createPaint()
        var shape: AbstractShape = BrushShape()

        if (isErasing) {
            paint = createEraserPaint()
        } else {
            when (val shapeType = currentShapeBuilder.shapeType) {
                ShapeType.Oval -> {
                    shape = OvalShape()
                }
                ShapeType.Brush -> {
                    shape = BrushShape()
                }
                ShapeType.Rectangle -> {
                    shape = RectangleShape()
                }
                ShapeType.Line -> {
                    shape = LineShape(context)
                }
                is ShapeType.Arrow -> {
                    shape = LineShape(context, shapeType.pointerLocation)
                }
            }
        }

        currentShape = ShapeAndPaint(shape, paint)
        drawShapes.push(currentShape)
        viewChangeListener?.onStartDrawing()
    }

    private fun endShape() {
        if (currentShape?.shape?.hasBeenTapped() == true) {
            drawShapes.remove(currentShape)
        }
        viewChangeListener?.apply {
            onStopDrawing()
            if(redoShapes.isNotEmpty()) {
                redoShapes.clear()
            }
            onViewAdd(this@DrawingView)
        }
    }

    fun undo(): Boolean {
        if (!drawShapes.empty()) {
            redoShapes.push(drawShapes.pop())
            invalidate()
        }
        viewChangeListener?.onViewRemoved(this)
        return !drawShapes.empty()
    }

    fun redo(): Boolean {
        if (!redoShapes.empty()) {
            drawShapes.push(redoShapes.pop())
            invalidate()
        }
        viewChangeListener?.onViewAdd(this)
        return !redoShapes.empty()
    }

    fun brushEraser() {
        isDrawingEnabled = true
        isErasing = true
    }

    fun enableDrawing(brushDrawMode: Boolean) {
        isDrawingEnabled = brushDrawMode
        isErasing = !brushDrawMode
        if (brushDrawMode) {
            visibility = VISIBLE
        }
    }

    companion object {
        const val DEFAULT_ERASER_SIZE = 50.0f
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        visibility = GONE
        currentShapeBuilder = ShapeBuilder()
    }
}
