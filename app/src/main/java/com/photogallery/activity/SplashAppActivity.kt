package com.photogallery.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivitySplashBinding

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
}