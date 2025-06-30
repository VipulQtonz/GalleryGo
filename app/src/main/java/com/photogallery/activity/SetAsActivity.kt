package com.photogallery.activity

import android.app.WallpaperManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import com.bumptech.glide.Glide
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.databinding.ActivitySetAsBinding
import com.skydoves.balloon.ArrowOrientation
import java.io.IOException

class SetAsActivity : BaseActivity<ActivitySetAsBinding>() {
    private lateinit var imageUri: Uri

    override fun getViewBinding(): ActivitySetAsBinding {
        return ActivitySetAsBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        imageUri = intent.getParcelableExtra<Uri>("SetAsImage") ?: return run {
            Toast.makeText(this, getString(R.string.invalid_image_path), Toast.LENGTH_SHORT).show()
            backScreenAnimation()
            finish()
        }

        Glide.with(this)
            .load(imageUri)
            .centerCrop()
            .into(binding.ivImage)

        binding.tvCarouselWallpaper.visibility = View.GONE
//            isCarouselWallpaperSupported()

        if (ePreferences.getBoolean("isFirstTimeSetAsApply", true)) {
            setupTooltip(
                this,
                binding.btnApply,
                getString(R.string.click_to_open_available_set_as_options),
                ArrowOrientation.BOTTOM,
                ePreferences,
                "isFirstTimeSetAsApply"
            )
        }
    }

    override fun addListener() {
        binding.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }

        binding.btnApply.setOnClickListener {
            binding.llApplyOptions.isVisible = true
        }

        binding.tvHomeScreen.setOnClickListener {
            showLoading()
            setWallpaper(WallpaperManager.FLAG_SYSTEM)
            binding.llApplyOptions.isVisible = false
        }

        // Lock Screen
        binding.tvLockScreen.setOnClickListener {
            showLoading()
            setWallpaper(WallpaperManager.FLAG_LOCK)
            binding.llApplyOptions.isVisible = false
        }

        // Carousel Wallpaper (not supported, show Toast)
        binding.tvCarouselWallpaper.setOnClickListener {
            showLoading()
            if (isCarouselWallpaperSupported()) {
                setCarouselWallpaper()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.carousel_wallpaper_not_supported),
                    Toast.LENGTH_SHORT
                ).show()
                hideLoading()
            }
            binding.llApplyOptions.isVisible = false
        }

        // Home and Lock Screens
        binding.tvHomeAndLockScreens.setOnClickListener {
            showLoading()
            setWallpaper(WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            binding.llApplyOptions.isVisible = false
        }

        // Cancel (hide llApplyOptions)
        binding.tvCancel.setOnClickListener {
            binding.llApplyOptions.isVisible = false
        }
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    private fun setWallpaper(flag: Int) {
        try {
            val wallpaperManager = WallpaperManager.getInstance(this)
            contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                wallpaperManager.setBitmap(bitmap, null, true, flag)
                Toast.makeText(
                    this,
                    getString(R.string.wallpaper_set_successfully),
                    Toast.LENGTH_SHORT
                ).show()
                hideLoading()
            } ?: throw IOException(getString(R.string.failed_to_open_image_stream))
        } catch (e: IOException) {
            hideLoading()
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_setting_wallpaper), Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun isCarouselWallpaperSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val wallpaperManager = WallpaperManager.getInstance(this)
            return wallpaperManager.isWallpaperSupported
        }
        return false
    }

    private fun setCarouselWallpaper() {
        // Placeholder for carousel wallpaper implementation
        // Since there’s no standard API, show a toast or fall back to static wallpaper
        try {
            // Example: Fall back to setting a static wallpaper for home screen
            val wallpaperManager = WallpaperManager.getInstance(this)
            contentResolver.openInputStream(imageUri)?.use { inputStream ->
                val bitmap = BitmapFactory.decodeStream(inputStream)
                wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                hideLoading()
            } ?: throw IOException(getString(R.string.failed_to_open_image_stream))
        } catch (e: IOException) {
            hideLoading()
            e.printStackTrace()
            Toast.makeText(
                this,
                getString(R.string.error_setting_wallpaper),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}