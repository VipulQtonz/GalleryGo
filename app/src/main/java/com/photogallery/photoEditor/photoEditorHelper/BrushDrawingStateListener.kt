package com.photogallery.photoEditor.photoEditorHelper

class BrushDrawingStateListener internal constructor(
    private val mPhotoEditorView: PhotoEditorView,
    private val mViewState: PhotoEditorViewState
) : BrushViewChangeListener {
    private var mOnPhotoEditorListener: OnPhotoEditorListener? = null
    fun setOnPhotoEditorListener(onPhotoEditorListener: OnPhotoEditorListener?) {
        mOnPhotoEditorListener = onPhotoEditorListener
    }

    override fun onViewAdd(drawingView: DrawingView) {
        if (mViewState.redoViewsCount > 0) {
            mViewState.popRedoView()
        }
        mViewState.addAddedView(drawingView)
        mOnPhotoEditorListener?.onAddViewListener(
            ViewType.BRUSH_DRAWING,
            mViewState.addedViewsCount
        )
    }

    override fun onViewRemoved(drawingView: DrawingView) {
        if (mViewState.addedViewsCount > 0) {
            val removeView = mViewState.removeAddedView(
                mViewState.addedViewsCount - 1
            )
            if (removeView !is DrawingView) {
                mPhotoEditorView.removeView(removeView)
            }
            mViewState.pushRedoView(removeView)
        }
        mOnPhotoEditorListener?.onRemoveViewListener(
            ViewType.BRUSH_DRAWING,
            mViewState.addedViewsCount
        )
    }

    override fun onStartDrawing() {
        mOnPhotoEditorListener?.onStartViewChangeListener(ViewType.BRUSH_DRAWING)

    }

    override fun onStopDrawing() {
        if (mViewState.redoViewsCount > 0) {
            mViewState.clearRedoViews()
        }
        mOnPhotoEditorListener?.onStopViewChangeListener(ViewType.BRUSH_DRAWING)
    }
}