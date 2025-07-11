package com.photogallery.crop.callback;

import android.graphics.RectF;

public interface OverlayViewChangeListener {
    void onCropRectUpdated(RectF cropRect);
}