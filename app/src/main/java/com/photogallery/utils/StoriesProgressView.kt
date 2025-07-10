package com.photogallery.utils

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.photogallery.R
import androidx.core.content.withStyledAttributes

class StoriesProgressView : LinearLayout {
    private val PROGRESS_BAR_LAYOUT_PARAM = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
    private val SPACE_LAYOUT_PARAM = LayoutParams(5, LayoutParams.WRAP_CONTENT)
    private val progressBars = ArrayList<PausableProgressBar>()
    private var storiesCount = -1
    private var current = -1
    private var storiesListener: StoriesListener? = null
    private var isComplete = false
    private var isSkipStart = false
    private var isReverseStart = false

    interface StoriesListener {
        fun onNext()
        fun onPrev()
        fun onComplete()
    }

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        orientation = HORIZONTAL
        context.withStyledAttributes(attrs, R.styleable.StoriesProgressView) {
            storiesCount = getInt(R.styleable.StoriesProgressView_progressCount, 0)
        }
        bindViews()
    }

    private fun bindViews() {
        progressBars.clear()
        removeAllViews()

        for (i in 0 until storiesCount) {
            val p = createProgressBar()
            progressBars.add(p)
            addView(p)
            if (i + 1 < storiesCount) {
                addView(createSpace())
            }
        }
    }

    private fun createProgressBar(): PausableProgressBar {
        val p = PausableProgressBar(context)
        p.layoutParams = PROGRESS_BAR_LAYOUT_PARAM
        return p
    }

    private fun createSpace(): View {
        val v = View(context)
        v.layoutParams = SPACE_LAYOUT_PARAM
        return v
    }

    fun setStoriesCount(storiesCount: Int) {
        this.storiesCount = storiesCount
        bindViews()
    }

    fun setStoriesListener(listener: StoriesListener?) {
        this.storiesListener = listener
    }

    fun setStoryDuration(duration: Long) {
        for (i in progressBars.indices) {
            progressBars[i].setDuration(duration)
            progressBars[i].setCallback(callback(i))
        }
    }

    private fun callback(index: Int): PausableProgressBar.Callback {
        return object : PausableProgressBar.Callback {
            override fun onStartProgress() {
                current = index
            }

            override fun onFinishProgress() {
                if (isReverseStart) {
                    storiesListener?.onPrev()
                    if (current > 0) {
                        progressBars[current - 1].setMinWithoutCallback()
                        progressBars[--current].startProgress()
                    } else {
                        progressBars[current].startProgress()
                    }
                    isReverseStart = false
                    return
                }
                val next = current + 1
                if (next < progressBars.size) {
                    storiesListener?.onNext()
                    progressBars[next].startProgress()
                } else {
                    isComplete = true
                    storiesListener?.onComplete()
                }
                isSkipStart = false
            }
        }
    }

    fun startStories(from: Int = 0) {
        resetProgress() // Reset progress to start from first item
        if (from < progressBars.size) {
            progressBars[from].startProgress()
        }
    }

    fun destroy() {
        for (p in progressBars) {
            p.clear()
        }
    }

    fun pause() {
        if (current in 0 until progressBars.size) {
            progressBars[current].pauseProgress()
        }
    }

    fun resume() {
        if (current in 0 until progressBars.size) {
            progressBars[current].resumeProgress()
            isComplete = false
        }
    }

    fun resetProgress() {
        current = -1
        isComplete = false
        isSkipStart = false
        isReverseStart = false
        for (p in progressBars) {
            p.clear()
        }
    }

    fun setProgressForManualChange(position: Int) {
        if (position !in 0 until progressBars.size) return
        current = position
        isComplete = false
        for (i in progressBars.indices) {
            when {
                i < position -> progressBars[i].setMaxWithoutCallback() // Fill previous
                i == position -> {
                    progressBars[i].clear() // Reset current
                    progressBars[i].startProgress() // Start current
                }
                else -> progressBars[i].clear() // Empty next
            }
        }
    }
}