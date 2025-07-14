package com.photogallery.adapter

import android.animation.ValueAnimator
import android.net.Uri
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView
import com.photogallery.R

class MomentAdapter(
    private val moments: List<Uri>,
    private val onNavigation: (String) -> Unit
) : RecyclerView.Adapter<MomentAdapter.ViewHolder>() {

    private var currentPosition = 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_moment_with_progress, parent, false)
        return ViewHolder(view, onNavigation)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val moment = moments[position]
        Glide.with(holder.ivRepresentative.context)
            .load(moment)
            .into(holder.ivRepresentative)

        holder.clearZoom()

        if (position == currentPosition) {
            holder.startZoom()
        }
    }

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (holder.adapterPosition == currentPosition) {
            holder.startZoom()
        }
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        holder.clearZoom()
    }

    override fun getItemCount(): Int = moments.size

    fun setCurrentPosition(position: Int) {
        if (position != currentPosition) {
            val previousPosition = currentPosition
            currentPosition = position
            notifyItemChanged(previousPosition)
            notifyItemChanged(currentPosition)
        }
    }

    class ViewHolder(val view: View, private val onNavigation: (String) -> Unit) :
        RecyclerView.ViewHolder(view) {
        val ivRepresentative: ShapeableImageView = view.findViewById(R.id.ivRepresentative)
        private var isLongPressed = false
        private var zoomAnimator: ValueAnimator? = null
        private val zoomDuration = 5000L
        private val maxZoom = 1.2f

        private fun findViewPagerParent(view: View): ViewPager2? {
            var currentView: View? = view
            while (currentView != null) {
                if (currentView is ViewPager2) {
                    return currentView
                }
                currentView = currentView.parent as? View
            }
            return null
        }

        private val gestureDetector =
            GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    val screenWidth = view.width.toFloat()
                    val x = e.x
                    val viewPager = findViewPagerParent(view)
                    viewPager?.let {
                        if (!isLongPressed) {
                            clearZoom()
                            val currentItem = it.currentItem
                            val itemCount = it.adapter?.itemCount ?: 0
                            if (x > screenWidth / 2) {
                                if (currentItem < itemCount - 1) {
                                    it.setCurrentItem(currentItem + 1, true)
                                    onNavigation("next")
                                } else {
                                    onNavigation("group_next")
                                }
                            } else {
                                if (currentItem > 0) {
                                    it.setCurrentItem(currentItem - 1, true)
                                    onNavigation("prev")
                                } else {
                                    onNavigation("group_prev")
                                }
                            }
                        }
                    }
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    isLongPressed = true
                    onNavigation("pause")
                    pauseZoom()
                }

            })

        init {
            view.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                when (event.action) {
                    MotionEvent.ACTION_UP -> {
                        if (isLongPressed) {
                            isLongPressed = false
                            onNavigation("resume")
                            resumeZoom()
                        }
                    }
                }
                true
            }
            setupZoomAnimation()
        }

        private fun setupZoomAnimation() {
            zoomAnimator = ValueAnimator.ofFloat(1.0f, maxZoom).apply {
                duration = zoomDuration
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                addUpdateListener { animator ->
                    val scale = animator.animatedValue as Float
                    ivRepresentative.scaleX = scale
                    ivRepresentative.scaleY = scale
                }
            }
        }

        fun startZoom() {
            zoomAnimator?.start()
        }

        fun pauseZoom() {
            zoomAnimator?.pause()
        }

        fun resumeZoom() {
            if (zoomAnimator?.isPaused == true) {
                zoomAnimator?.resume()
            } else {
                startZoom()
            }
        }

        fun clearZoom() {
            zoomAnimator?.cancel()
            ivRepresentative.scaleX = 1.0f
            ivRepresentative.scaleY = 1.0f
        }
    }
}