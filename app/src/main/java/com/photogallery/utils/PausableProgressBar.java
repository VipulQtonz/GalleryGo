package com.photogallery.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.Transformation;
import android.widget.FrameLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.photogallery.R;

public final class PausableProgressBar extends FrameLayout {

    private static final int DEFAULT_PROGRESS_DURATION = 2000;

    private final View frontProgressView;
    private final View maxProgressView;

    private PausableScaleAnimation animation;
    private long duration = DEFAULT_PROGRESS_DURATION;
    private Callback callback;

    public interface Callback {
        void onStartProgress();

        void onFinishProgress();
    }
    public PausableProgressBar(Context context) {
        this(context, null);
    }

    public PausableProgressBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PausableProgressBar(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater.from(context).inflate(R.layout.pausable_progress, this);
        frontProgressView = findViewById(R.id.frontProgress);
        maxProgressView = findViewById(R.id.maxProgress); // work around
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public void setCallback(@NonNull Callback callback) {
        this.callback = callback;
    }

    public void setMax() {
        finishProgress(true);
    }

    void setMin() {
        finishProgress(false);
    }

    public void setMinWithoutCallback() {
        maxProgressView.setBackgroundResource(R.color.progress_secondary);
        maxProgressView.setVisibility(VISIBLE);
        if (animation != null) {
            animation.setAnimationListener(null);
            animation.cancel();
        }
    }

    public void setMaxWithoutCallback() {
        maxProgressView.setBackgroundResource(R.color.progress_max_active);
        maxProgressView.setVisibility(VISIBLE);
        if (animation != null) {
            animation.setAnimationListener(null);
            animation.cancel();
        }
    }

    private void finishProgress(boolean isMax) {
        if (isMax) maxProgressView.setBackgroundResource(R.color.progress_max_active);
        maxProgressView.setVisibility(isMax ? VISIBLE : GONE);
        if (animation != null) {
            animation.setAnimationListener(null);
            animation.cancel();
            if (callback != null) {
                callback.onFinishProgress();
            }
        }
    }

    public void startProgress() {
        maxProgressView.setVisibility(GONE);

        animation = new PausableScaleAnimation(0, 1, 1, 1, Animation.ABSOLUTE, 0, Animation.RELATIVE_TO_SELF, 0);
        animation.setDuration(duration);
        animation.setInterpolator(new LinearInterpolator());
        animation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                frontProgressView.setVisibility(View.VISIBLE);
                if (callback != null) callback.onStartProgress();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                if (callback != null) callback.onFinishProgress();
            }
        });
        animation.setFillAfter(true);
        frontProgressView.startAnimation(animation);
    }

    public void pauseProgress() {
        if (animation != null) {
            animation.pause();
        }
    }

    public void setProgress(float progress) {
        // Ensure progress is within valid range
        progress = Math.max(0f, Math.min(1f, progress));

        // Clear any existing animation
        clear();

        // Create a new animation that starts from 0 and goes to the specified progress
        animation = new PausableScaleAnimation(0, progress, 1, 1,
                Animation.ABSOLUTE, 0, Animation.RELATIVE_TO_SELF, 0);
        animation.setDuration(1); // Very short duration for immediate effect
        animation.setInterpolator(new LinearInterpolator());
        animation.setFillAfter(true);

        // Start the animation
        frontProgressView.startAnimation(animation);

        // Immediately complete the animation to show the progress
        animation.start();
        animation.getTransformation(AnimationUtils.currentAnimationTimeMillis(), null);

        // Pause the animation so it doesn't continue running
        animation.pause();
    }

    public void resumeProgress() {
        if (animation != null) {
            animation.resume();
        }
    }

    public void clear() { // Changed to public
        if (animation != null) {
            animation.setAnimationListener(null);
            animation.cancel();
            animation = null;
        }
    }


    private static class PausableScaleAnimation extends ScaleAnimation {

        private long mElapsedAtPause = 0;
        private boolean mPaused = false;

        PausableScaleAnimation(float fromX, float toX, float fromY,
                               float toY, int pivotXType, float pivotXValue, int pivotYType,
                               float pivotYValue) {
            super(fromX, toX, fromY, toY, pivotXType, pivotXValue, pivotYType,
                    pivotYValue);
        }

        @Override
        public boolean getTransformation(long currentTime, Transformation outTransformation, float scale) {
            if (mPaused && mElapsedAtPause == 0) {
                mElapsedAtPause = currentTime - getStartTime();
            }
            if (mPaused) {
                setStartTime(currentTime - mElapsedAtPause);
            }
            return super.getTransformation(currentTime, outTransformation, scale);
        }

        void pause() {
            if (mPaused) return;
            mElapsedAtPause = 0;
            mPaused = true;
        }

        void resume() {
            mPaused = false;
        }
    }
}