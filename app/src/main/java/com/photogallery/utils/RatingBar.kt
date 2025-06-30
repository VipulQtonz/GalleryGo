package  com.photogallery.utils

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import com.photogallery.R
import kotlin.math.sign

open class RatingBar : View {
    private var mMaxCount = 5
    private var mRating = 0f
    private var mMinSelectionAllowed = 0
    private var mStarSize = 0
    private var isIndicator = false
    private var mStepSize = 1f
    private var selectTheTappedRating = false

    @DrawableRes
    private var filledDrawable = 0

    @DrawableRes
    private var emptyDrawable = 0
    private var baseDrawable: Drawable? = null
    private var overlayDrawable: ClipDrawable? = null
    var margin = 0
        private set
    private var mRatingBarListener: OnRatingBarChangeListener? = null
    private val mTouchListener: OnTouchListener = object : OnTouchListener {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            if (isIndicator) {
                return true
            }
            val x: Float = event.x.toInt().toFloat()
            var selectedAmount = 0f
            if (x >= 0 && x <= width) {
                val xPerStar: Int = margin * 2 + mStarSize
                if (x < xPerStar * .25f) {
                    selectedAmount = 0f
                } else {
                    if (mStepSize <= 0) {
                        mStepSize = 0.1f
                    }
                    selectedAmount = getSelectedRating(x, xPerStar, mStepSize)
                }
            }
            if (x < 0) {
                selectedAmount = 0f
            } else if (x > width) {
                selectedAmount = mMaxCount.toFloat()
            }
            setRating(selectedAmount, true)
            return true
        }
    }

    constructor(context: Context?) : super(context) {
        init(null)
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    @Suppress("unused")
    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(attrs)
    }

    protected fun init(attributeSet: AttributeSet?) {
        if (attributeSet != null) {
            context.withStyledAttributes(attributeSet, R.styleable.RatingBar) {
                filledDrawable =
                    getResourceId(R.styleable.RatingBar_filledDrawable, DEFAULT_FILLED_DRAWABLE)
                emptyDrawable =
                    getResourceId(R.styleable.RatingBar_emptyDrawable, DEFAULT_EMPTY_DRAWABLE)
                mStarSize =
                    getDimensionPixelSize(R.styleable.RatingBar_starSize, getPixelValueForDP(20))
                mMaxCount =
                    getInt(
                        R.styleable.RatingBar_numStars,
                        5
                    )
                mMinSelectionAllowed = getInt(R.styleable.RatingBar_minAllowedStars, 0)
                margin =
                    getDimensionPixelSize(R.styleable.RatingBar_starSpacing, getPixelValueForDP(2))
                mRating = getFloat(R.styleable.RatingBar_rating, mMinSelectionAllowed.toFloat())
                isIndicator = getBoolean(R.styleable.RatingBar_isIndicator, false)
                mStepSize = getFloat(R.styleable.RatingBar_stepSize, 1f)
                selectTheTappedRating =
                    getBoolean(R.styleable.RatingBar_selectTheTappedRating, false)
            }
        } else {
            setDefaultDrawables()
        }
        setEmptyDrawable(emptyDrawable)
        setFilledDrawable(filledDrawable)
        setIsIndicator(isIndicator)
    }

    private fun setDefaultDrawables() {
        setFilledDrawable(DEFAULT_FILLED_DRAWABLE)
        setEmptyDrawable(DEFAULT_EMPTY_DRAWABLE)
    }

    fun setRating(newRating: Float, fromUser: Boolean) {
        var mod = newRating % mStepSize

        if (mod < mStepSize) {
            mod = 0f
        }
        mRating = newRating - mod
        if (mRating < mMinSelectionAllowed) {
            mRating = mMinSelectionAllowed.toFloat()
        } else if (mRating > mMaxCount) {
            mRating = mMaxCount.toFloat()
        }
        if (mRatingBarListener != null) {
            mRatingBarListener!!.onRatingChanged(this, mRating, fromUser)
        }
        postInvalidate()
    }

    fun setShouldSelectTheTappedRating(selectTheTappedRating: Boolean) {
        this.selectTheTappedRating = selectTheTappedRating
    }

    var rating: Float
        get() = mRating
        set(rating) {
            setRating(rating, false)
        }
    var max: Int
        get() = mMaxCount
        set(count) {
            mMaxCount = count
            post { requestLayout() }
        }
    var minimumSelectionAllowed: Int
        get() = mMinSelectionAllowed
        set(minStarCount) {
            mMinSelectionAllowed = minStarCount
            postInvalidate()
        }

    fun setStarMarginsInDP(marginInDp: Int) {
        setStarMargins(getPixelValueForDP(marginInDp))
    }

    fun setStarMargins(margins: Int) {
        margin = margins
        post { requestLayout() }
    }

    private fun getPixelValueForDP(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    fun setStarSizeInDp(size: Int) {
        setStarSize(getPixelValueForDP(size))
    }

    fun setStarSize(size: Int) {
        mStarSize = size
        if (baseDrawable != null) {
            baseDrawable!!.setBounds(0, 0, mStarSize, mStarSize)
        }
        if (overlayDrawable != null) {
            overlayDrawable!!.setBounds(0, 0, mStarSize, mStarSize)
        }
        post { requestLayout() }
    }

    @SuppressLint("RtlHardcoded")
    private fun createFilledClipDrawable(d: Drawable) {
        overlayDrawable = ClipDrawable(
            d,
            Gravity.LEFT,
            ClipDrawable.HORIZONTAL
        )
        overlayDrawable!!.setBounds(0, 0, mStarSize, mStarSize)
    }

    fun setFilledDrawable(filledDrawable: Drawable) {
        if (overlayDrawable == null) {
            if (true) {
                createFilledClipDrawable(filledDrawable)
            }
        } else {
            if (false) {
                overlayDrawable = null
            } else {
                createFilledClipDrawable(filledDrawable)
            }
        }
        postInvalidate()
    }

    fun setFilledDrawable(@DrawableRes filledDrawable: Int) {
        val newVersion: Drawable = ContextCompat.getDrawable(context, filledDrawable)!!
        setFilledDrawable(newVersion)
    }

    fun setEmptyDrawable(emptyDrawable: Drawable?) {
        baseDrawable = emptyDrawable
        baseDrawable!!.setBounds(0, 0, mStarSize, mStarSize)
        postInvalidate()
    }

    fun setEmptyDrawable(@DrawableRes emptyDrawable: Int) {
        this.emptyDrawable = emptyDrawable
        val d: Drawable = ContextCompat.getDrawable(context, emptyDrawable)!!
        setEmptyDrawable(d)
    }

    fun setIsIndicator(isIndicator: Boolean) {
        this.isIndicator = isIndicator
        if (this.isIndicator) {
            super.setOnTouchListener(null)
        } else {
            super.setOnTouchListener(mTouchListener)
        }
    }

    override fun setOnTouchListener(l: OnTouchListener) {}
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val height = margin * 2 + mStarSize
        val width = height * mMaxCount
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var movedX = 0f
        canvas.translate(0f, margin.toFloat())
        var remaining = mRating
        for (i in 0 until mMaxCount) {
            canvas.translate(margin.toFloat(), 0f)
            movedX += margin.toFloat()
            if (baseDrawable != null) {
                baseDrawable!!.draw(canvas)
            }
            if (overlayDrawable != null) {
                if (remaining >= 1) {
                    overlayDrawable!!.setLevel(10000)
                    overlayDrawable!!.draw(canvas)
                } else if (remaining > 0) {
                    overlayDrawable!!.setLevel((remaining * 10000).toInt())
                    overlayDrawable!!.draw(canvas)
                } else {
                    overlayDrawable!!.setLevel(0)
                }
                remaining -= 1f
            }
            canvas.translate(mStarSize.toFloat(), 0f)
            movedX += mStarSize.toFloat()
            canvas.translate(margin.toFloat(), 0f)
            movedX += margin.toFloat()
        }
        canvas.translate(movedX * -1, (margin * -1).toFloat())
    }

    protected fun getSelectedRating(xOfRating: Float, xPerStar: Int, stepSize: Float): Float {
        var selectedAmount = (xOfRating - xPerStar) / xPerStar + 1
        val remainder = selectedAmount % stepSize
        selectedAmount = selectedAmount - remainder
        if (selectTheTappedRating) {
            val directionalStep = sign(remainder) * stepSize
            selectedAmount += directionalStep
        }
        return selectedAmount
    }

    //Interfaces
    interface OnRatingBarChangeListener {
        fun onRatingChanged(
            ratingBarUtility: RatingBar?,
            rating: Float,
            fromUser: Boolean
        ) //Possibly add a previously selected and currently selected part, but later.
    }

    fun getOnRatingBarChangeListener(): OnRatingBarChangeListener? {
        return mRatingBarListener
    }

    fun setOnRatingBarChangeListener(listener: OnRatingBarChangeListener) {
        this.mRatingBarListener = listener
    }

    companion object {
        protected val DEFAULT_FILLED_DRAWABLE: Int = R.drawable.icon_star_active
        protected val DEFAULT_EMPTY_DRAWABLE: Int = R.drawable.icon_star_inactive
    }
}