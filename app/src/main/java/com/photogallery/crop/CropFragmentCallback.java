package com.photogallery.crop;

public interface CropFragmentCallback {

    /**
     * Return loader status
     * @param showLoader
     */
    void loadingProgress(boolean showLoader);

    /**
     * Return cropping result or error
     * @param result
     */
    void onCropFinish(CropFragment.UCropResult result);

}
