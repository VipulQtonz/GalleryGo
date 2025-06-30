package com.photogallery.crop.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.photogallery.crop.callback.CropBoundsChangeListener;
import com.photogallery.crop.callback.OverlayViewChangeListener;
import com.photogallery.R;

public class CropView extends FrameLayout {

    private GestureCropImageView mGestureCropImageView;
    private final OverlayView mViewOverlay;

    public CropView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CropView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        LayoutInflater.from(context).inflate(R.layout.crop_view, this, true);
        mGestureCropImageView = findViewById(R.id.imageViewCrop);
        mViewOverlay = findViewById(R.id.viewOverlay);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.CropView);
        mViewOverlay.processStyledAttributes(a);
        mGestureCropImageView.processStyledAttributes(a);
        a.recycle();


        setListenersToViews();
    }

    private void setListenersToViews() {
        mGestureCropImageView.setCropBoundsChangeListener(mViewOverlay::setTargetAspectRatio);
        mViewOverlay.setOverlayViewChangeListener(cropRect -> mGestureCropImageView.setCropRect(cropRect));
    }

    @Override
    public boolean shouldDelayChildPressedState() {
        return false;
    }

    @NonNull
    public GestureCropImageView getCropImageView() {
        return mGestureCropImageView;
    }

    @NonNull
    public OverlayView getOverlayView() {
        return mViewOverlay;
    }

    /**
     * Method for reset state for UCropImageView such as rotation, scale, translation.
     * Be careful: this method recreate UCropImageView instance and reattach it to layout.
     */
    public void resetCropImageView() {
        removeView(mGestureCropImageView);
        mGestureCropImageView = new GestureCropImageView(getContext());
        setListenersToViews();
        mGestureCropImageView.setCropRect(getOverlayView().getCropViewRect());
        addView(mGestureCropImageView, 0);
    }
}