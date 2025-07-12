package com.photogallery.activity

import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.MyApplication
import com.photogallery.adapter.MomentGroupAdapter
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityMomentDetailsBinding
import com.photogallery.model.Moment
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MomentDetailsActivity : BaseActivity<ActivityMomentDetailsBinding>() {
    private lateinit var adapter: MomentGroupAdapter
    private lateinit var moment: Moment
    private val momentList = ArrayList<Moment>()
    private var isScrolling = false
    private var isMuted: Boolean = false

    override fun getViewBinding(): ActivityMomentDetailsBinding {
        return ActivityMomentDetailsBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        isMuted = ePreferences.getBoolean("isMuted", false)
        moment = MyApplication.getSelectedMoment()!!
        MyApplication.momentsLiveData.observe(this) { momentGroups ->
            momentList.clear()
            if (!momentGroups.isNullOrEmpty()) {
                for (group in momentGroups) {
                    for (moment in group.moments) {
                        momentList.add(moment)
                    }
                }
                adapter.notifyDataSetChanged()

                val selectedMomentIndex = momentList.indexOfFirst { it == moment }
                if (selectedMomentIndex != -1) {
                    val layoutManager = binding.rvMomentDetails.layoutManager as LinearLayoutManager
                    layoutManager.scrollToPositionWithOffset(selectedMomentIndex, 0)
                    applyScaleAnimation(binding.rvMomentDetails)
                }
            }
        }

        adapter =
            MomentGroupAdapter(this@MomentDetailsActivity, ePreferences, momentList, isMuted) {
                backScreenAnimation()
                finish()
            }
        binding.rvMomentDetails.layoutManager = LinearLayoutManager(this)
        binding.rvMomentDetails.adapter = adapter

        val snapHelper = LinearSnapHelper()
        snapHelper.attachToRecyclerView(binding.rvMomentDetails)

        binding.rvMomentDetails.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                when (newState) {
                    RecyclerView.SCROLL_STATE_IDLE -> {
                        isScrolling = false
                        applyScaleAnimation(recyclerView)
                    }

                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        isScrolling = true
                    }

                    RecyclerView.SCROLL_STATE_SETTLING -> {
                        applyScaleAnimation(recyclerView)
                    }
                }
            }

            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (isScrolling) {
                    applyScaleAnimation(recyclerView)
                }
            }
        })

        binding.rvMomentDetails.onFlingListener = object : RecyclerView.OnFlingListener() {
            override fun onFling(velocityX: Int, velocityY: Int): Boolean {
                val layoutManager = binding.rvMomentDetails.layoutManager as LinearLayoutManager
                val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                val lastVisiblePosition = layoutManager.findLastVisibleItemPosition()
                val targetPosition = if (velocityY > 0) {
                    min(firstVisiblePosition + 1, momentList.size - 1)
                } else {
                    max(lastVisiblePosition - 1, 0)
                }
                binding.rvMomentDetails.smoothScrollToPosition(targetPosition)
                return true
            }
        }
    }

    private fun applyScaleAnimation(recyclerView: RecyclerView) {
        recyclerView.layoutManager as LinearLayoutManager
        val center = recyclerView.height / 2

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)
            val childCenter = (child.top + child.bottom) / 2
            val distanceFromCenter = abs(center - childCenter)

            val scale = 1f - (distanceFromCenter.toFloat() / center.toFloat()) * 0.2f
            child.scaleX = scale
            child.scaleY = scale
        }
    }

    override fun addListener() {
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }
}