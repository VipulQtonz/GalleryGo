package com.photogallery.crop;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.datatransport.backend.cct.BuildConfig;
import com.photogallery.activity.CropActivity;

public class Crop {
    public static final int REQUEST_CROP = 69;
    public static final int RESULT_ERROR = 96;
    private static final String EXTRA_PREFIX = BuildConfig.LIBRARY_PACKAGE_NAME;
    public static final String EXTRA_INPUT_URI = EXTRA_PREFIX + ".InputUri";
    public static final String EXTRA_OUTPUT_URI = EXTRA_PREFIX + ".OutputUri";
    public static final String EXTRA_OUTPUT_CROP_ASPECT_RATIO = EXTRA_PREFIX + ".CropAspectRatio";
    public static final String EXTRA_OUTPUT_IMAGE_WIDTH = EXTRA_PREFIX + ".ImageWidth";
    public static final String EXTRA_OUTPUT_IMAGE_HEIGHT = EXTRA_PREFIX + ".ImageHeight";
    public static final String EXTRA_OUTPUT_OFFSET_X = EXTRA_PREFIX + ".OffsetX";
    public static final String EXTRA_OUTPUT_OFFSET_Y = EXTRA_PREFIX + ".OffsetY";
    public static final String EXTRA_ERROR = EXTRA_PREFIX + ".Error";
    public static final String EXTRA_ASPECT_RATIO_X = EXTRA_PREFIX + ".AspectRatioX";
    public static final String EXTRA_ASPECT_RATIO_Y = EXTRA_PREFIX + ".AspectRatioY";
    public static final String EXTRA_MAX_SIZE_X = EXTRA_PREFIX + ".MaxSizeX";
    public static final String EXTRA_MAX_SIZE_Y = EXTRA_PREFIX + ".MaxSizeY";
    private Intent mCropIntent;
    private Bundle mCropOptionsBundle;

    public static Crop of(@NonNull Uri source, @NonNull Uri destination) {
        return new Crop(source, destination);
    }

    private Crop(@NonNull Uri source, @NonNull Uri destination) {
        mCropIntent = new Intent();
        mCropOptionsBundle = new Bundle();
        mCropOptionsBundle.putParcelable(EXTRA_INPUT_URI, source);
        mCropOptionsBundle.putParcelable(EXTRA_OUTPUT_URI, destination);
    }

    public Crop withOptions(@NonNull Options options) {
        mCropOptionsBundle.putAll(options.getOptionBundle());
        return this;
    }

    public void start(@NonNull Activity activity) {
        start(activity, REQUEST_CROP);
    }

    public void start(@NonNull Activity activity, int requestCode) {
        activity.startActivityForResult(getIntent(activity), requestCode);
    }

    public void start(@NonNull Context context, @NonNull Fragment fragment) {
        start(context, fragment, REQUEST_CROP);
    }

    public void start(@NonNull Context context, @NonNull androidx.fragment.app.Fragment fragment) {
        start(context, fragment, REQUEST_CROP);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void start(@NonNull Context context, @NonNull Fragment fragment, int requestCode) {
        fragment.startActivityForResult(getIntent(context), requestCode);
    }

    public void start(@NonNull Context context, @NonNull androidx.fragment.app.Fragment fragment, int requestCode) {
        fragment.startActivityForResult(getIntent(context), requestCode);
    }

    public void start(@NonNull Context context,
                      @NonNull ActivityResultLauncher<Intent> activityResultLauncher) {
        activityResultLauncher.launch(getIntent(context));
    }

    public Intent getIntent(@NonNull Context context) {
        mCropIntent.setClass(context, CropActivity.class);
        mCropIntent.putExtras(mCropOptionsBundle);
        return mCropIntent;
    }

    public CropFragment getFragment() {
        return CropFragment.newInstance(mCropOptionsBundle);
    }

    public CropFragment getFragment(Bundle bundle) {
        mCropOptionsBundle = bundle;
        return getFragment();
    }

    @Nullable
    public static Uri getOutput(@NonNull Intent intent) {
        return intent.getParcelableExtra(EXTRA_OUTPUT_URI);
    }

    @Nullable
    public static Throwable getError(@NonNull Intent result) {
        return (Throwable) result.getSerializableExtra(EXTRA_ERROR);
    }

    public static class Options {
        public static final String EXTRA_COMPRESSION_FORMAT_NAME = EXTRA_PREFIX + ".CompressionFormatName";
        public static final String EXTRA_COMPRESSION_QUALITY = EXTRA_PREFIX + ".CompressionQuality";

        public static final String EXTRA_ALLOWED_GESTURES = EXTRA_PREFIX + ".AllowedGestures";

        public static final String EXTRA_MAX_BITMAP_SIZE = EXTRA_PREFIX + ".MaxBitmapSize";
        public static final String EXTRA_MAX_SCALE_MULTIPLIER = EXTRA_PREFIX + ".MaxScaleMultiplier";
        public static final String EXTRA_IMAGE_TO_CROP_BOUNDS_ANIM_DURATION = EXTRA_PREFIX + ".ImageToCropBoundsAnimDuration";

        public static final String EXTRA_DIMMED_LAYER_COLOR = EXTRA_PREFIX + ".DimmedLayerColor";
        public static final String EXTRA_CIRCLE_DIMMED_LAYER = EXTRA_PREFIX + ".CircleDimmedLayer";

        public static final String EXTRA_SHOW_CROP_FRAME = EXTRA_PREFIX + ".ShowCropFrame";
        public static final String EXTRA_CROP_FRAME_COLOR = EXTRA_PREFIX + ".CropFrameColor";
        public static final String EXTRA_CROP_FRAME_STROKE_WIDTH = EXTRA_PREFIX + ".CropFrameStrokeWidth";

        public static final String EXTRA_SHOW_CROP_GRID = EXTRA_PREFIX + ".ShowCropGrid";
        public static final String EXTRA_CROP_GRID_ROW_COUNT = EXTRA_PREFIX + ".CropGridRowCount";
        public static final String EXTRA_CROP_GRID_COLUMN_COUNT = EXTRA_PREFIX + ".CropGridColumnCount";
        public static final String EXTRA_CROP_GRID_COLOR = EXTRA_PREFIX + ".CropGridColor";
        public static final String EXTRA_CROP_GRID_CORNER_COLOR = EXTRA_PREFIX + ".CropGridCornerColor";
        public static final String EXTRA_CROP_GRID_STROKE_WIDTH = EXTRA_PREFIX + ".CropGridStrokeWidth";

        public static final String EXTRA_UCROP_COLOR_CONTROLS_WIDGET_ACTIVE = EXTRA_PREFIX + ".UcropColorControlsWidgetActive";

        public static final String EXTRA_UCROP_WIDGET_COLOR_TOOLBAR = EXTRA_PREFIX + ".UcropToolbarWidgetColor";
        public static final String EXTRA_UCROP_TITLE_TEXT_TOOLBAR = EXTRA_PREFIX + ".UcropToolbarTitleText";
        public static final String EXTRA_UCROP_WIDGET_CROP_DRAWABLE = EXTRA_PREFIX + ".UcropToolbarCropDrawable";

        public static final String EXTRA_UCROP_LOGO_COLOR = EXTRA_PREFIX + ".UcropLogoColor";

        public static final String EXTRA_HIDE_BOTTOM_CONTROLS = EXTRA_PREFIX + ".HideBottomControls";
        public static final String EXTRA_FREE_STYLE_CROP = EXTRA_PREFIX + ".FreeStyleCrop";

        public static final String EXTRA_ASPECT_RATIO_SELECTED_BY_DEFAULT = EXTRA_PREFIX + ".AspectRatioSelectedByDefault";
        public static final String EXTRA_ASPECT_RATIO_OPTIONS = EXTRA_PREFIX + ".AspectRatioOptions";

        public static final String EXTRA_UCROP_ROOT_VIEW_BACKGROUND_COLOR = EXTRA_PREFIX + ".UcropRootViewBackgroundColor";


        private final Bundle mOptionBundle;

        public Options() {
            mOptionBundle = new Bundle();
        }

        @NonNull
        public Bundle getOptionBundle() {
            return mOptionBundle;
        }

        public void setCompressionFormat(@NonNull Bitmap.CompressFormat format) {
            mOptionBundle.putString(EXTRA_COMPRESSION_FORMAT_NAME, format.name());
        }

        public void setCompressionQuality(@IntRange(from = 0) int compressQuality) {
            mOptionBundle.putInt(EXTRA_COMPRESSION_QUALITY, compressQuality);
        }

        public void setHideBottomControls(boolean hide) {
            mOptionBundle.putBoolean(EXTRA_HIDE_BOTTOM_CONTROLS, hide);
        }

        public void setFreeStyleCropEnabled(boolean enabled) {
            mOptionBundle.putBoolean(EXTRA_FREE_STYLE_CROP, enabled);
        }
    }
}
