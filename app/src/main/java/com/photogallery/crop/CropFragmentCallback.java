package com.photogallery.crop;

public interface CropFragmentCallback {

    void loadingProgress(boolean showLoader);

    void onCropFinish(CropFragment.UCropResult result);
}
