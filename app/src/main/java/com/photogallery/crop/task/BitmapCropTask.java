package com.photogallery.crop.task;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;
import android.graphics.Matrix;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import com.photogallery.crop.callback.BitmapCropCallback;
import com.photogallery.crop.model.CropParameters;
import com.photogallery.crop.model.ExifInfo;
import com.photogallery.crop.model.ImageState;
import com.photogallery.crop.util.FileUtils;
import com.photogallery.crop.util.ImageHeaderParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Crops part of image that fills the crop bounds.
 * <p/>
 * First image is downscaled if max size was set and if resulting image is larger that max size.
 * Then image is rotated accordingly.
 * Finally new Bitmap object is created and saved to file.
 */
public class BitmapCropTask extends AsyncTask<Void, Void, Throwable> {

    private static final String TAG = "BitmapCropTask";

    private Bitmap mViewBitmap;

    private final RectF mCropRect;
    private final RectF mCurrentImageRect;

    private float mCurrentScale, mCurrentAngle;
    private final int mMaxResultImageSizeX, mMaxResultImageSizeY;

    private final Bitmap.CompressFormat mCompressFormat;
    private final int mCompressQuality;
    private final String mImageInputPath, mImageOutputPath;
    private final ExifInfo mExifInfo;
    private final BitmapCropCallback mCropCallback;

    private int mCroppedImageWidth, mCroppedImageHeight;
    private int cropOffsetX, cropOffsetY;

    public BitmapCropTask(@Nullable Bitmap viewBitmap, @NonNull ImageState imageState, @NonNull CropParameters cropParameters,
                          @Nullable BitmapCropCallback cropCallback) {

        mViewBitmap = viewBitmap;
        mCropRect = imageState.getCropRect();
        mCurrentImageRect = imageState.getCurrentImageRect();

        mCurrentScale = imageState.getCurrentScale();
        mCurrentAngle = imageState.getCurrentAngle();
        mMaxResultImageSizeX = cropParameters.getMaxResultImageSizeX();
        mMaxResultImageSizeY = cropParameters.getMaxResultImageSizeY();

        mCompressFormat = cropParameters.getCompressFormat();
        mCompressQuality = cropParameters.getCompressQuality();

        mImageInputPath = cropParameters.getImageInputPath();
        mImageOutputPath = cropParameters.getImageOutputPath();
        mExifInfo = cropParameters.getExifInfo();

        mCropCallback = cropCallback;
    }

    @Override
    @Nullable
    protected Throwable doInBackground(Void... params) {
        if (mViewBitmap == null) {
            return new NullPointerException("ViewBitmap is null");
        } else if (mViewBitmap.isRecycled()) {
            return new NullPointerException("ViewBitmap is recycled");
        } else if (mCurrentImageRect.isEmpty()) {
            return new NullPointerException("CurrentImageRect is empty");
        }

        float resizeScale = resize();

        try {
            crop(resizeScale);
            mViewBitmap = null;
        } catch (Throwable throwable) {
            return throwable;
        }

        return null;
    }

    private float resize() {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(mImageInputPath, options);

        boolean swapSides = mExifInfo.getExifDegrees() == 90 || mExifInfo.getExifDegrees() == 270;
        float scaleX = (swapSides ? options.outHeight : options.outWidth) / (float) mViewBitmap.getWidth();
        float scaleY = (swapSides ? options.outWidth : options.outHeight) / (float) mViewBitmap.getHeight();

        float resizeScale = Math.min(scaleX, scaleY);

        mCurrentScale /= resizeScale;

        resizeScale = 1;
        if (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0) {
            float cropWidth = mCropRect.width() / mCurrentScale;
            float cropHeight = mCropRect.height() / mCurrentScale;

            if (cropWidth > mMaxResultImageSizeX || cropHeight > mMaxResultImageSizeY) {

                scaleX = mMaxResultImageSizeX / cropWidth;
                scaleY = mMaxResultImageSizeY / cropHeight;
                resizeScale = Math.min(scaleX, scaleY);

                mCurrentScale /= resizeScale;
            }
        }
        return resizeScale;
    }

    private boolean crop(float resizeScale) throws IOException {
        ExifInterface originalExif = new ExifInterface(mImageInputPath);

        cropOffsetX = Math.round((mCropRect.left - mCurrentImageRect.left) / mCurrentScale);
        cropOffsetY = Math.round((mCropRect.top - mCurrentImageRect.top) / mCurrentScale);
        mCroppedImageWidth = Math.round(mCropRect.width() / mCurrentScale);
        mCroppedImageHeight = Math.round(mCropRect.height() / mCurrentScale);

        boolean shouldCrop = shouldCrop(mCroppedImageWidth, mCroppedImageHeight);
        Log.i(TAG, "Should crop: " + shouldCrop);

        if (shouldCrop) {
            // Load the source bitmap
            Bitmap sourceBitmap = BitmapFactory.decodeFile(mImageInputPath);
            if (sourceBitmap == null) {
                throw new IOException("Failed to decode input image");
            }

            // Apply rotation if needed
            if (mCurrentAngle != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(mCurrentAngle);
                sourceBitmap = Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.getWidth(), sourceBitmap.getHeight(), matrix, true);
            }

            // Crop the bitmap
            Bitmap croppedBitmap = Bitmap.createBitmap(sourceBitmap, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight);

            // Save the cropped bitmap
            FileOutputStream out = new FileOutputStream(mImageOutputPath);
            croppedBitmap.compress(mCompressFormat, mCompressQuality, out);
            out.close();

            // Copy EXIF data for JPEG
            if (mCompressFormat.equals(Bitmap.CompressFormat.JPEG)) {
                ImageHeaderParser.copyExif(originalExif, mCroppedImageWidth, mCroppedImageHeight, mImageOutputPath);
            }

            // Clean up
            sourceBitmap.recycle();
            croppedBitmap.recycle();
            return true;
        } else {
            FileUtils.copyFile(mImageInputPath, mImageOutputPath);
            return false;
        }
    }

    /**
     * Check whether an image should be cropped at all or just file can be copied to the destination path.
     * For each 1000 pixels there is one pixel of error due to matrix calculations etc.
     *
     * @param width  - crop area width
     * @param height - crop area height
     * @return - true if image must be cropped, false - if original image fits requirements
     */
    private boolean shouldCrop(int width, int height) {
        int pixelError = 1;
        pixelError += Math.round(Math.max(width, height) / 1000f);
        return (mMaxResultImageSizeX > 0 && mMaxResultImageSizeY > 0)
                || Math.abs(mCropRect.left - mCurrentImageRect.left) > pixelError
                || Math.abs(mCropRect.top - mCurrentImageRect.top) > pixelError
                || Math.abs(mCropRect.bottom - mCurrentImageRect.bottom) > pixelError
                || Math.abs(mCropRect.right - mCurrentImageRect.right) > pixelError
                || mCurrentAngle != 0;
    }

    @SuppressWarnings("JniMissingFunction")
    native public static boolean cropCImg(
            String inputPath, String outputPath,
            int left, int top, int right, int bottom,
            float angle, float resizeScale,
            int format, int quality,
            int exifDegrees, int exifTranslation) throws IOException, OutOfMemoryError;

    @Override
    protected void onPostExecute(@Nullable Throwable t) {
        if (mCropCallback != null) {
            if (t == null) {
                Uri uri = Uri.fromFile(new File(mImageOutputPath));
                mCropCallback.onBitmapCropped(uri, cropOffsetX, cropOffsetY, mCroppedImageWidth, mCroppedImageHeight);
            } else {
                mCropCallback.onCropFailure(t);
            }
        }
    }

}
