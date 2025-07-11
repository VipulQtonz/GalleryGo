package com.photogallery.activity

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.photogallery.R
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityCropImageBinding
import com.photogallery.crop.Crop
import com.photogallery.crop.CropFragment
import com.photogallery.crop.CropFragmentCallback
import com.photogallery.utils.Const.REQUEST_SELECT_PICTURE_FOR_FRAGMENT
import java.io.File

class CropImageActivity : BaseActivity<ActivityCropImageBinding>(), CropFragmentCallback {
    private val requestMode = 1
    private var fragment: CropFragment? = null
    private var mShowLoader = false
    private var sourceUri: Uri? = null

    override fun getViewBinding(): ActivityCropImageBinding {
        return ActivityCropImageBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        sourceUri = intent.getParcelableExtra("uri")
        if (sourceUri != null) {
            startCrop(sourceUri!!)
        } else {
            Toast.makeText(this, getString(R.string.no_image_uri_received), Toast.LENGTH_SHORT)
                .show()
            finish()
            backScreenAnimation()
        }
    }

    override fun addListener() {}

    override fun onBackPressedDispatcher() {
        fragment?.let {
            if (supportFragmentManager.findFragmentByTag(CropFragment.TAG) != null) {
                supportFragmentManager.beginTransaction()
                    .remove(it)
                    .commitNowAllowingStateLoss()
            }
            fragment = null
        }
        setResult(RESULT_CANCELED)
        backScreenAnimation()
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Crop.REQUEST_CROP) {
            when (resultCode) {
                RESULT_OK -> data?.let { handleCropResult(it) }
                Crop.RESULT_ERROR -> data?.let { handleCropError(it) }
                RESULT_CANCELED -> {
                    finish()
                    nextScreenAnimation()
                }
            }
        } else if (requestCode == requestMode && resultCode == RESULT_OK) {
            val selectedUri = data?.data
            if (selectedUri != null) {
                sourceUri = selectedUri
                startCrop(selectedUri)
            } else {
                Toast.makeText(
                    this@CropImageActivity,
                    R.string.toast_cannot_retrieve_selected_image,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun startCrop(sourceUri: Uri) {
        val extension = ".png"
        val destinationFileName = "temp_crop_${System.currentTimeMillis()}" + extension
        val destinationFile = File(cacheDir, destinationFileName)
        val destinationUri = Uri.fromFile(destinationFile)

        var crop = Crop.of(sourceUri, destinationUri)
        crop = advancedConfig(crop)

        if (requestMode == REQUEST_SELECT_PICTURE_FOR_FRAGMENT) {
            setupFragment(crop)
        } else {
            crop.start(this@CropImageActivity)
        }
    }

    private fun advancedConfig(crop: Crop): Crop {
        val options = Crop.Options()
        options.setCompressionFormat(Bitmap.CompressFormat.PNG)
        options.setCompressionQuality(100)
        options.setHideBottomControls(false)
        options.setFreeStyleCropEnabled(true)
        return crop.withOptions(options)
    }

    private fun handleCropResult(result: Intent) {
        val resultUri = Crop.getOutput(result)
        if (resultUri != null) {
            setResult(RESULT_OK, Intent().putExtra("croppedUri", resultUri))
            nextScreenAnimation()
            finish()
        } else {
            Toast.makeText(
                this@CropImageActivity,
                R.string.toast_cannot_retrieve_cropped_image,
                Toast.LENGTH_SHORT
            ).show()
            setResult(RESULT_CANCELED)
            nextScreenAnimation()
            finish()
        }
    }

    private fun handleCropError(result: Intent) {
        val cropError = Crop.getError(result)
        if (cropError != null) {
            Toast.makeText(this@CropImageActivity, cropError.message, Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(
                this@CropImageActivity,
                R.string.toast_unexpected_error,
                Toast.LENGTH_SHORT
            ).show()
        }
        setResult(RESULT_CANCELED)
        nextScreenAnimation()
        finish()
    }

    override fun loadingProgress(showLoader: Boolean) {
        mShowLoader = showLoader
        supportInvalidateOptionsMenu()
    }

    override fun onCropFinish(result: CropFragment.UCropResult) {
        when (result.mResultCode) {
            RESULT_OK -> handleCropResult(result.mResultData)
            Crop.RESULT_ERROR -> handleCropError(result.mResultData)
        }
    }

    fun setupFragment(crop: Crop) {
        fragment = crop.getFragment(crop.getIntent(this).extras) as CropFragment?
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment!!, CropFragment.TAG)
            .commitAllowingStateLoss()
    }
}