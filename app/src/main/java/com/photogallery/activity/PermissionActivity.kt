package com.photogallery.activity

import android.content.Intent
import android.os.Bundle
import com.photogallery.databinding.ActivityPermissionBinding

class PermissionActivity : BaseActivity<ActivityPermissionBinding>() {

    override fun getViewBinding(): ActivityPermissionBinding {
        return ActivityPermissionBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
    }

    override fun addListener() {
        binding.btnContinue.setOnClickListener {
            startActivity(Intent(this@PermissionActivity, HomeActivity::class.java))
            nextScreenAnimation()
            finish()
        }
    }

    override fun onBackPressedDispatcher() {
        finish()
    }
}