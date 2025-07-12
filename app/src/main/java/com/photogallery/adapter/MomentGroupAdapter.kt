package com.photogallery.adapter

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.model.Moment
import com.photogallery.utils.SharedPreferenceHelper
import com.photogallery.utils.StoriesProgressView
import com.skydoves.balloon.ArrowOrientation
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.random.Random

class MomentGroupAdapter(
    private val context: Context,
    private val ePreferences: SharedPreferenceHelper,
    private var momentGroups: MutableList<Moment>,
    private var isMuted: Boolean,
    private val onCancelClickListener: () -> Unit
) : RecyclerView.Adapter<MomentGroupAdapter.ViewHolder>() {

    private val handler = Handler(Looper.getMainLooper())
    private val autoAdvanceDelay = 5000L
    private var currentPlayingPosition = -1
    private var currentViewHolder: ViewHolder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var musicFiles: List<String> = emptyList()
    private var isNavigating = false // Flag to prevent concurrent navigation
    private var lastNavigationTime = 0L // To debounce rapid navigation

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val flCancel: FrameLayout = view.findViewById(R.id.flCancel)
        val vpMoments: ViewPager2 = view.findViewById(R.id.vpMoments)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val tvEventName: TextView = view.findViewById(R.id.tvEventName)
        val storiesProgressView: StoriesProgressView = view.findViewById(R.id.storiesProgressView)
        val flSoundOnOff: FrameLayout = view.findViewById(R.id.flSoundOnOff)
        val btnSoundOnOff: ImageView = view.findViewById(R.id.btnSoundOnOff)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_moment_group, parent, false)
        if (musicFiles.isEmpty()) {
            musicFiles = parent.context.assets.list("music")?.toList() ?: emptyList()
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val group = momentGroups[position]
        holder.tvDate.text = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(group.date)
        holder.tvEventName.text = group.title
        holder.flCancel.setOnClickListener {
            destroy()
            onCancelClickListener()
        }

        updateSoundButton(holder)
        holder.flSoundOnOff.setOnClickListener {
            isMuted = !isMuted
            updateSoundButton(holder)
            updateMediaPlayerVolume()
            for (i in 0 until itemCount) {
                if (i != holder.adapterPosition) {
                    notifyItemChanged(i)
                }
            }
        }

        val momentAdapter = MomentAdapter(group.allUris) { action ->
            val currentTime = System.currentTimeMillis()
            if (isNavigating || currentTime - lastNavigationTime < 500) {
                return@MomentAdapter
            }
            isNavigating = true
            lastNavigationTime = currentTime
            when (action) {
                "next" -> {
                    val nextPosition = holder.vpMoments.currentItem + 1
                    if (nextPosition < (holder.vpMoments.adapter?.itemCount ?: 0)) {
                        holder.storiesProgressView.pause() // Pause progress to avoid onNext
                        holder.vpMoments.setCurrentItem(nextPosition, true)
                        startAutoAdvance(holder)
                    } else {
                        advanceToNextMomentGroupOrFinish(holder, position)
                    }
                }

                "prev" -> {
                    val prevPosition = holder.vpMoments.currentItem - 1
                    if (prevPosition >= 0) {
                        holder.storiesProgressView.pause() // Pause progress to avoid onNext
                        holder.vpMoments.setCurrentItem(prevPosition, true)
                        startAutoAdvance(holder)
                    }
                }

                "group_prev" -> {
                    if (position > 0) {
                        holder.storiesProgressView.destroy()
                        handler.removeCallbacksAndMessages(null)
                        val recyclerView = holder.view.parent as? RecyclerView
                        recyclerView?.smoothScrollToPosition(position - 1)
                    }
                }

                "group_next" -> {
                    advanceToNextMomentGroupOrFinish(holder, position)
                }

                "pause" -> {
                    holder.storiesProgressView.pause()
                    pauseMediaPlayer()
                    handler.removeCallbacksAndMessages(null)
                }

                "resume" -> {
                    holder.storiesProgressView.resume()
                    resumeMediaPlayer()
                    startAutoAdvance(holder)
                }
            }
            isNavigating = false
        }

        holder.vpMoments.adapter = momentAdapter
        holder.vpMoments.offscreenPageLimit = 1
        holder.vpMoments.setCurrentItem(0, false)

        if (group.allUris.isNotEmpty()) {
            holder.storiesProgressView.destroy() // Clear any existing state
            holder.storiesProgressView.setStoriesCount(group.allUris.size)
            holder.storiesProgressView.setStoryDuration(autoAdvanceDelay)
            holder.storiesProgressView.resetProgress()

            holder.storiesProgressView.setStoriesListener(object :
                StoriesProgressView.StoriesListener {
                override fun onNext() {
                    val currentTime = System.currentTimeMillis()
                    if (isNavigating || currentTime - lastNavigationTime < 500) {
                        return
                    }
                    isNavigating = true
                    lastNavigationTime = currentTime
                    val nextPosition = holder.vpMoments.currentItem + 1
                    if (nextPosition < group.allUris.size) {
                        holder.vpMoments.setCurrentItem(nextPosition, true)
                    } else {
                        advanceToNextMomentGroupOrFinish(holder, position)
                    }
                    isNavigating = false
                }

                override fun onPrev() {
                    val currentTime = System.currentTimeMillis()
                    if (isNavigating || currentTime - lastNavigationTime < 500) {
                        return
                    }
                    isNavigating = true
                    lastNavigationTime = currentTime
                    val prevPosition = holder.vpMoments.currentItem - 1
                    if (prevPosition >= 0) {
                        holder.vpMoments.setCurrentItem(prevPosition, true)
                    }
                    isNavigating = false
                }

                override fun onComplete() {
                    advanceToNextMomentGroupOrFinish(holder, position)
                }
            })

            holder.vpMoments.registerOnPageChangeCallback(object :
                ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val currentTime = System.currentTimeMillis()
                    if (isNavigating || currentTime - lastNavigationTime < 500) {
                        return
                    }
                    isNavigating = true
                    lastNavigationTime = currentTime
                    handler.removeCallbacksAndMessages(null)
                    holder.storiesProgressView.setProgressForManualChange(position)
                    (holder.vpMoments.adapter as? MomentAdapter)?.setCurrentPosition(position)
                    holder.storiesProgressView.resume() // Resume progress after manual navigation
                    startAutoAdvance(holder)
                    isNavigating = false
                }
            })

            if (position == currentPlayingPosition) {
                startNewMusic(holder)
                holder.storiesProgressView.startStories(0)
                startAutoAdvance(holder)
            } else {
                holder.storiesProgressView.pause()
            }
        }

        val animation = AnimationUtils.loadAnimation(holder.view.context, R.anim.reels_fade_in)
        holder.view.startAnimation(animation)


        if (ePreferences.getBoolean("isFirstTimeMomentViewCancel", true)) {
            setupTooltip(
                context,
                holder.flCancel,
                context.getString(R.string.click_to_exit_moment_view),
                ArrowOrientation.BOTTOM,
                ePreferences,
                "isFirstTimeMomentViewCancel"
            )
            {
                if (ePreferences.getBoolean("isFirstTimeMomentViewSoundOnOff", true)) {
                    setupTooltip(
                        context,
                        holder.flSoundOnOff,
                        context.getString(R.string.click_to_turn_on_off_sound),
                        ArrowOrientation.BOTTOM,
                        ePreferences,
                        "isFirstTimeMomentViewSoundOnOff"
                    )
                }
            }
        }
    }

    private fun advanceToNextMomentGroupOrFinish(holder: ViewHolder, currentPosition: Int) {
        stopMediaPlayer()
        holder.storiesProgressView.destroy()
        handler.removeCallbacksAndMessages(null)
        if (currentPosition < momentGroups.size - 1) {
            val recyclerView = holder.view.parent as? RecyclerView
            recyclerView?.let {
                val layoutManager = it.layoutManager
                if (layoutManager is LinearLayoutManager) {
                    val currentRvPosition = layoutManager.findFirstVisibleItemPosition()
                    if (currentRvPosition != currentPosition + 1) {
                        it.smoothScrollToPosition(currentPosition + 1)
                    } else {
                        Log.d(
                            "MomentGroupAdapter",
                            "Scroll blocked: already at position ${currentPosition + 1}"
                        )
                    }
                } else {
                    it.smoothScrollToPosition(currentPosition + 1)
                }
            }
        } else {
            destroy()
            onCancelClickListener()
        }
    }

    private fun startAutoAdvance(holder: ViewHolder) {
        handler.removeCallbacksAndMessages(null)
        if ((holder.vpMoments.adapter?.itemCount ?: 0) > 0) {
            handler.postDelayed({
                if (holder == currentViewHolder && !isNavigating) {
                    isNavigating = true
                    val currentTime = System.currentTimeMillis()
                    lastNavigationTime = currentTime
                    val nextPosition = holder.vpMoments.currentItem + 1
                    val itemCount = holder.vpMoments.adapter?.itemCount ?: 0
                    if (nextPosition < itemCount) {
                        holder.vpMoments.setCurrentItem(nextPosition, true)
                    } else {
                        advanceToNextMomentGroupOrFinish(holder, holder.adapterPosition)
                    }
                    isNavigating = false
                }
            }, autoAdvanceDelay)
        }
    }

    private fun startNewMusic(holder: ViewHolder) {
        if (musicFiles.isEmpty()) return
        stopMediaPlayer()
        try {
            mediaPlayer = MediaPlayer().apply {
                val musicFile = musicFiles[Random.nextInt(musicFiles.size)]
                val afd: AssetFileDescriptor = holder.view.context.assets.openFd("music/$musicFile")
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                prepare()
                if (!isMuted) {
                    setVolume(1f, 1f)
                } else {
                    setVolume(0f, 0f)
                }
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun pauseMediaPlayer() {
        mediaPlayer?.pause()
    }

    private fun resumeMediaPlayer() {
        if (!isMuted) {
            mediaPlayer?.start()
        }
    }

    private fun stopMediaPlayer() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }

    private fun updateMediaPlayerVolume() {
        mediaPlayer?.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
    }

    private fun updateSoundButton(holder: ViewHolder) {
        holder.btnSoundOnOff.setImageResource(
            if (isMuted) R.drawable.ic_sound_off else R.drawable.ic_sound_on
        )
        ePreferences.putBoolean("isMuted", isMuted)
    }

    override fun getItemCount(): Int = momentGroups.size

    override fun onViewAttachedToWindow(holder: ViewHolder) {
        super.onViewAttachedToWindow(holder)
        if (currentViewHolder != null && currentViewHolder != holder) {
            currentViewHolder?.storiesProgressView?.pause()
            pauseMediaPlayer()
            handler.removeCallbacksAndMessages(null)
        }

        currentViewHolder = holder
        currentPlayingPosition = holder.adapterPosition

        // Reset ViewPager2 and StoriesProgressView to start from the beginning
        holder.vpMoments.setCurrentItem(0, false)
        holder.storiesProgressView.destroy() // Clear any previous state
        holder.storiesProgressView.setStoriesCount(momentGroups[holder.adapterPosition].allUris.size)
        holder.storiesProgressView.setStoryDuration(autoAdvanceDelay)
        holder.storiesProgressView.resetProgress()
        startNewMusic(holder)
        holder.storiesProgressView.startStories(0)
        startAutoAdvance(holder)
        // Notify adapter to ensure zoom starts
        holder.vpMoments.adapter?.notifyDataSetChanged()
    }

    override fun onViewDetachedFromWindow(holder: ViewHolder) {
        super.onViewDetachedFromWindow(holder)
        if (holder == currentViewHolder) {
            currentViewHolder = null
            currentPlayingPosition = -1
            stopMediaPlayer()
        }
        handler.removeCallbacksAndMessages(null)
        holder.storiesProgressView.pause()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        stopMediaPlayer()
        handler.removeCallbacksAndMessages(null)
        currentViewHolder = null
        currentPlayingPosition = -1
    }

    fun destroy() {
        stopMediaPlayer()
        handler.removeCallbacksAndMessages(null)
        currentViewHolder = null
        currentPlayingPosition = -1
    }
}