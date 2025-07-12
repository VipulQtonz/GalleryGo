package com.photogallery.adapter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.VideoView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.model.MediaData
import com.photogallery.utils.SharedPreferenceHelper
import com.skydoves.balloon.ArrowOrientation

class ImagePagerAdapter(
    private val context: Context,
    private var mediaList: MutableList<MediaData>,
    private val rotationMap: MutableMap<Int, Float>,
    private var isLoopingEnabled: Boolean,
    private val ePreferences: SharedPreferenceHelper
) : RecyclerView.Adapter<ImagePagerAdapter.ImageViewHolder>() {

    private val videoLoadTimeout = 10000L // 10 seconds
    private val activeViewHolders = mutableListOf<ImageViewHolder>()
    private val loopingStateMap = mutableMapOf<Int, Boolean>()
    private var isMuted: Boolean = ePreferences.getBoolean("isMuted", false)
    private val activeMediaPlayers = mutableListOf<MediaPlayer>()

    init {
        setHasStableIds(true)
        mediaList.indices.forEach { position ->
            loopingStateMap[position] = isLoopingEnabled
        }
    }

    override fun getItemId(position: Int): Long {
        return mediaList[position].id
    }

    override fun onViewAttachedToWindow(holder: ImageViewHolder) {
        super.onViewAttachedToWindow(holder)
        activeViewHolders.add(holder)
        holder.updateLoopingState()
        holder.updateSoundIcon(isMuted)
    }

    override fun onViewDetachedFromWindow(holder: ImageViewHolder) {
        super.onViewDetachedFromWindow(holder)
        activeViewHolders.remove(holder)
    }

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
        val videoView: VideoView = itemView.findViewById(R.id.videoView)
        val mediaControls: ViewGroup = itemView.findViewById(R.id.mediaControls)
        val btnPlayPause: ImageView = itemView.findViewById(R.id.btnPlayPause)
        val btnSoundOnOff: ImageView = itemView.findViewById(R.id.btnSoundOnOff)
        val seekBar: SeekBar = itemView.findViewById(R.id.seekBar)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val flPlayPause: FrameLayout = itemView.findViewById(R.id.flPlayPause)
        val flSoundOnOff: FrameLayout = itemView.findViewById(R.id.flSoundOnOff)
        val tvCurrentTime: TextView = itemView.findViewById(R.id.tvCurrentTime)
        val loadingProgress: ProgressBar = itemView.findViewById(R.id.loadingProgress)
        var mediaPlayer: MediaPlayer? = null
        var progressHandler: Handler? = null
        var updateProgressRunnable: Runnable? = null
        var isControlsVisible = false
        private val controlsHideDelay = 3000L
        internal val handler = Handler(Looper.getMainLooper())
        private val hideControlsRunnable = Runnable { hideControls() }
        internal val videoLoadTimeoutRunnable = Runnable {
            if (loadingProgress.isVisible) {
                showError()
            }
        }

        fun updateLoopingState() {
            try {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val shouldLoop = loopingStateMap[position] ?: isLoopingEnabled
                    mediaPlayer?.isLooping = shouldLoop

                    videoView.setOnCompletionListener {
                        if (shouldLoop) {
                            videoView.seekTo(0)
                            videoView.start()
                        } else {
                            btnPlayPause.setImageResource(R.drawable.ic_play_video)
                        }
                    }
                }
            } catch (_: IllegalStateException) {
            }
        }

        fun updateSoundIcon(muted: Boolean) {
            try {
                mediaPlayer?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                btnSoundOnOff.setImageResource(
                    if (muted) R.drawable.ic_sound_off else R.drawable.ic_sound_on
                )

                if (mediaPlayer != null) {
                    if (muted) {
                        setVideoViewVolume(0f)
                    } else {
                        setVideoViewVolume(1f)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun setVideoViewVolume(volume: Float) {
            try {
                val mMediaPlayerField = VideoView::class.java.getDeclaredField("mMediaPlayer")
                mMediaPlayerField.isAccessible = true
                val mediaPlayer = mMediaPlayerField.get(videoView) as MediaPlayer?
                mediaPlayer?.setVolume(volume, volume)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun showControls() {
            isControlsVisible = true
            mediaControls.alpha = 0f
            mediaControls.visibility = View.VISIBLE
            mediaControls.animate().alpha(1f).setDuration(100).start()

            flPlayPause.alpha = 0f
            flPlayPause.visibility = View.VISIBLE
            flPlayPause.animate().alpha(1f).setDuration(100).start()

            resetHideTimer()
        }

        fun hideControls() {
            isControlsVisible = false
            mediaControls.animate().alpha(0f).setDuration(100).withEndAction {
                mediaControls.visibility = View.GONE
            }.start()

            flPlayPause.animate().alpha(0f).setDuration(100).withEndAction {
                flPlayPause.visibility = View.GONE
            }.start()

            handler.removeCallbacks(hideControlsRunnable)
        }

        fun resetHideTimer() {
            handler.removeCallbacks(hideControlsRunnable)
            handler.postDelayed(hideControlsRunnable, controlsHideDelay)
        }

        fun showError() {
            loadingProgress.visibility = View.GONE
            videoView.visibility = View.GONE
            mediaControls.visibility = View.GONE
            flPlayPause.visibility = View.GONE
            handler.removeCallbacks(videoLoadTimeoutRunnable)
        }

        fun cleanup() {
            handler.removeCallbacks(hideControlsRunnable)
            handler.removeCallbacks(videoLoadTimeoutRunnable)
            progressHandler?.removeCallbacks(updateProgressRunnable ?: return)

            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.reset()
                } catch (_: IllegalStateException) {
                } finally {
                    player.release()
                }
            }

            mediaPlayer = null
            loadingProgress.visibility = View.GONE
            videoView.setVideoURI(null)
            imageView.setImageDrawable(null)
            Glide.with(imageView.context).clear(imageView)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.item_image_pager, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.loadingProgress.visibility = View.VISIBLE
        val mediaData = mediaList[position]

        if (mediaData.isVideo) {
            holder.updateLoopingState()
            holder.videoView.setVideoURI(null)
            holder.mediaPlayer?.release()
            holder.mediaPlayer = null
        }

        if (!mediaData.isVideo) {
            holder.hideControls()
            holder.videoView.visibility = View.GONE
            holder.imageView.visibility = View.VISIBLE

            Glide.with(holder.imageView.context).asBitmap().load(mediaData.path)
                .format(DecodeFormat.PREFER_ARGB_8888).diskCacheStrategy(DiskCacheStrategy.RESOURCE)
                .skipMemoryCache(false).error(R.drawable.ic_image_placeholder)
                .placeholder(R.drawable.ic_image_placeholder).thumbnail(0.25f)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap, transition: Transition<in Bitmap>?
                    ) {
                        try {
                            if (resource.allocationByteCount > 100 * 1024 * 1024) {
                                Glide.with(holder.imageView.context).asBitmap().load(mediaData.path)
                                    .error(R.drawable.ic_image_placeholder)
                                    .placeholder(R.drawable.ic_image_placeholder)
                                    .override(resource.width / 2, resource.height / 2)
                                    .into(holder.imageView)
                            } else {
                                holder.imageView.setImageBitmap(resource)
                            }
                            holder.loadingProgress.visibility = View.GONE
                            val rotation = rotationMap.getOrDefault(position, 0f)
                            holder.imageView.rotation = rotation
                        } catch (_: Exception) {
                            Glide.with(holder.imageView.context).load(mediaData.path)
                                .error(R.drawable.ic_image_placeholder)
                                .placeholder(R.drawable.ic_image_placeholder).into(holder.imageView)
                            holder.loadingProgress.visibility = View.GONE
                        }
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        holder.imageView.setImageDrawable(placeholder)
                        holder.loadingProgress.visibility = View.GONE
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        holder.imageView.setImageDrawable(errorDrawable)
                        holder.loadingProgress.visibility = View.GONE
                    }
                })
        } else {
            holder.imageView.visibility = View.VISIBLE
            holder.videoView.visibility = View.VISIBLE
            holder.flPlayPause.visibility = View.VISIBLE
            holder.hideControls()

            if (!isUriValid(holder.imageView.context, mediaData.uri)) {
                holder.showError()
                return
            }

            logVideoMetadata(holder.imageView.context, mediaData.uri)

            Glide.with(holder.imageView.context).asBitmap().load(mediaData.uri)
                .format(DecodeFormat.PREFER_RGB_565).thumbnail(0.25f).override(512, 512)
                .diskCacheStrategy(DiskCacheStrategy.ALL).error(R.drawable.ic_video_placeholder)
                .placeholder(R.drawable.ic_video_placeholder).into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(
                        resource: Bitmap, transition: Transition<in Bitmap>?
                    ) {
                        holder.imageView.setImageBitmap(resource)
                    }

                    override fun onLoadFailed(errorDrawable: Drawable?) {
                        loadThumbnailWithRetriever(
                            holder.imageView.context, mediaData.uri, holder.imageView
                        )
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        holder.imageView.setImageDrawable(placeholder)
                    }
                })

            try {
                holder.videoView.setVideoURI(mediaData.uri)
                holder.handler.postDelayed(holder.videoLoadTimeoutRunnable, videoLoadTimeout)
                setupMediaControls(holder)
            } catch (_: Exception) {
                holder.showError()
            }

            holder.videoView.setOnClickListener {
                if (holder.isControlsVisible) holder.hideControls() else holder.showControls()
            }

            if (ePreferences.getBoolean("isFirstTimeVideoPlayerAdapterPlayPause", true)) {
                holder.hideControls()
                holder.showControls()
                setupTooltip(
                    context,
                    holder.flPlayPause,
                    context.getString(R.string.click_to_play_pause),
                    ArrowOrientation.BOTTOM,
                    ePreferences,
                    "isFirstTimeVideoPlayerAdapterPlayPause"
                ) {
                    if (ePreferences.getBoolean("isFirstTimeVideoPlayerAdapterCurrentTime", true)) {
                        holder.hideControls()
                        holder.showControls()
                        setupTooltip(
                            context,
                            holder.tvCurrentTime,
                            context.getString(R.string.current_time),
                            ArrowOrientation.BOTTOM,
                            ePreferences,
                            "isFirstTimeVideoPlayerAdapterCurrentTime"
                        ) {
                            if (ePreferences.getBoolean(
                                    "isFirstTimeVideoPlayerAdapterTotalTime", true
                                )
                            ) {
                                holder.hideControls()
                                holder.showControls()
                                setupTooltip(
                                    context,
                                    holder.tvDuration,
                                    context.getString(R.string.total_video_duration),
                                    ArrowOrientation.BOTTOM,
                                    ePreferences,
                                    "isFirstTimeVideoPlayerAdapterTotalTime"
                                )
                                {
                                    if (ePreferences.getBoolean(
                                            "isFirstTimeVideoPlayerAdapterMuteOnOff",
                                            true
                                        )
                                    ) {
                                        holder.hideControls()
                                        holder.showControls()
                                        setupTooltip(
                                            context,
                                            holder.flSoundOnOff,
                                            context.getString(R.string.mute_and_unmute_video_sound),
                                            ArrowOrientation.BOTTOM,
                                            ePreferences,
                                            "isFirstTimeVideoPlayerAdapterMuteOnOff"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val rotation = rotationMap.getOrDefault(position, 0f)
        holder.imageView.rotation = rotation
    }

    override fun onViewRecycled(holder: ImageViewHolder) {
        super.onViewRecycled(holder)
        holder.mediaPlayer?.let {
            activeMediaPlayers.remove(it)
        }
        holder.cleanup()
    }

    override fun getItemCount(): Int = mediaList.size

    fun updateMediaList(newMediaList: List<MediaData>) {
        mediaList.clear()
        mediaList.addAll(newMediaList)
        notifyDataSetChanged()
    }

    private fun setupMediaControls(holder: ImageViewHolder) {
        holder.btnPlayPause.setOnClickListener {
            if (holder.videoView.isPlaying) {
                holder.videoView.pause()
                holder.btnPlayPause.setImageResource(R.drawable.ic_play_video)
            } else {
                if (holder.videoView.currentPosition >= holder.seekBar.max - 100) {
                    holder.videoView.seekTo(0)
                    holder.seekBar.progress = 0
                    holder.tvCurrentTime.text = formatDuration(0)
                }
                holder.videoView.start()
                holder.btnPlayPause.setImageResource(R.drawable.ic_pause_video)

                holder.progressHandler?.removeCallbacks(holder.updateProgressRunnable!!)
                holder.progressHandler?.post(holder.updateProgressRunnable!!)
            }
            holder.resetHideTimer()
        }

        holder.flSoundOnOff.setOnClickListener {
            toggleSoundState()
        }

        holder.videoView.setOnPreparedListener { mp ->
            holder.mediaPlayer = mp
            mp.isLooping = isLoopingEnabled
            mp.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
            holder.seekBar.max = mp.duration
            holder.tvDuration.text = formatDuration(mp.duration.toLong())
            holder.tvCurrentTime.text = formatDuration(0L)
            holder.setVideoViewVolume(if (isMuted) 0f else 1f)
            mp.start()

            holder.btnPlayPause.setImageResource(R.drawable.ic_pause_video)
            holder.btnSoundOnOff.setImageResource(
                if (isMuted) R.drawable.ic_sound_off else R.drawable.ic_sound_on
            )
            holder.showControls()
            holder.loadingProgress.visibility = View.GONE
            holder.handler.removeCallbacks(holder.videoLoadTimeoutRunnable)

            holder.progressHandler = Handler(Looper.getMainLooper())
            holder.updateProgressRunnable = object : Runnable {
                override fun run() {
                    if (holder.videoView.isPlaying) {
                        val current = holder.videoView.currentPosition
                        holder.seekBar.progress = current
                        holder.tvCurrentTime.text = formatDuration(current.toLong())
                        holder.progressHandler?.postDelayed(this, 500)
                    }
                }
            }
            holder.progressHandler?.post(holder.updateProgressRunnable!!)
        }

        holder.videoView.setOnCompletionListener {
            holder.btnPlayPause.setImageResource(R.drawable.ic_play_video)
            holder.seekBar.progress = holder.seekBar.max
            holder.tvCurrentTime.text = formatDuration(holder.seekBar.max.toLong())
        }

        holder.videoView.setOnErrorListener { mp, what, extra ->
            holder.showError()
            true
        }

        holder.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            var userSeeking = false

            override fun onProgressChanged(
                seekBar: SeekBar?, progress: Int, fromUser: Boolean
            ) {
                if (fromUser) {
                    holder.tvCurrentTime.text = formatDuration(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                userSeeking = true
                holder.videoView.pause()
                holder.progressHandler?.removeCallbacks(holder.updateProgressRunnable!!)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                userSeeking = false
                seekBar?.let {
                    holder.videoView.seekTo(it.progress)
                    holder.videoView.start()
                    holder.btnPlayPause.setImageResource(R.drawable.ic_pause_video)
                    holder.progressHandler?.post(holder.updateProgressRunnable!!)
                }
            }
        })

        holder.mediaControls.setOnTouchListener { _, _ ->
            holder.resetHideTimer()
            false
        }
    }

    private fun toggleSoundState() {
        isMuted = !isMuted
        ePreferences.putBoolean("isMuted", isMuted)

        activeMediaPlayers.forEach { mp ->
            try {
                mp.setVolume(if (isMuted) 0f else 1f, if (isMuted) 0f else 1f)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        activeViewHolders.forEach { holder ->
            holder.updateSoundIcon(isMuted)
            holder.setVideoViewVolume(if (isMuted) 0f else 1f)
        }
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000) % 60
        val minutes = (millis / (1000 * 60)) % 60
        val hours = millis / (1000 * 60 * 60)
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private fun isUriValid(context: Context, uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun loadThumbnailWithRetriever(context: Context, uri: Uri, imageView: ImageView) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else {
                imageView.setImageResource(R.drawable.ic_image_placeholder)
            }
            retriever.release()
        } catch (_: Exception) {
            imageView.setImageResource(R.drawable.ic_image_placeholder)
        }
    }

    private fun logVideoMetadata(context: Context, uri: Uri) {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            retriever.release()
        } catch (_: Exception) {
        }
    }

    fun updateLoopingForAllItems(enabled: Boolean) {
        isLoopingEnabled = enabled
        mediaList.indices.forEach { position ->
            loopingStateMap[position] = enabled
        }

        activeViewHolders.forEach { holder ->
            holder.updateLoopingState()
        }
    }
}