package com.photogallery.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.processDuplicates
import com.photogallery.MyApplication.Companion.processLocationPhotos
import com.photogallery.MyApplication.Companion.processMoments
import com.photogallery.MyApplication.Companion.processPhotoClassification
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivitySplashBinding
import com.photogallery.utils.isInternet

class SplashAppActivity : BaseActivity<ActivitySplashBinding>() {
    override fun getViewBinding(): ActivitySplashBinding {
        return ActivitySplashBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (ePreferences.getBoolean("isCompleteGetStartAndPermissionFlow", false)) {
                startActivity(Intent(this@SplashAppActivity, HomeActivity::class.java))
            } else {
                startActivity(Intent(this@SplashAppActivity, StartActivity::class.java))
            }
            nextScreenAnimation()
            finish()
        }, 2000)
    }

    override fun addListener() {
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (MyApplication.instance.hasStoragePermission()) {
            processLocationPhotos(this)
            if (isInternet()) {
                processPhotoClassification(this)
                processMoments(this)
                processDuplicates(this)
            }
        }
    }
}