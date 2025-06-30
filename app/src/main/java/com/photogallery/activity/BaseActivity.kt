package com.photogallery.activity

import android.R
import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import com.photogallery.MyApplication
import com.photogallery.utils.SharedPreferenceHelper
import com.photogallery.utils.SharedPreferenceHelper.Companion.PREF_APP_COLOR
import com.photogallery.utils.SharedPreferenceHelper.Companion.PREF_THEME
import com.photogallery.utils.SharedPreferenceHelper.Companion.THEME_SYSTEM_DEFAULT
import java.io.File

abstract class BaseActivity<T : ViewBinding> : AppCompatActivity() {
    private var customLoadingDialog: android.app.Dialog? = null
    internal lateinit var ePreferences: SharedPreferenceHelper
    lateinit var binding: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ePreferences = MyApplication.instance.pref
        val theme = ePreferences.getInt(PREF_APP_COLOR, 0)

        when (theme) {
            0 -> {
                setTheme(com.photogallery.R.style.AppTheme_1)
            }

            1 -> {
                setTheme(com.photogallery.R.style.AppTheme_2)
            }

            2 -> {
                setTheme(com.photogallery.R.style.AppTheme_3)
            }

            3 -> {
                setTheme(com.photogallery.R.style.AppTheme_4)
            }

            4 -> {
                setTheme(com.photogallery.R.style.AppTheme_5)
            }

            5 -> {
                setTheme(com.photogallery.R.style.AppTheme_6)
            }

            6 -> {
                setTheme(com.photogallery.R.style.AppTheme_7)
            }

            7 -> {
                setTheme(com.photogallery.R.style.AppTheme_8)
            }

            else -> {
                setTheme(com.photogallery.R.style.AppTheme_1)
            }
        }

        binding = getViewBinding()
        setContentView(binding.root)
        enableEdgeToEdge()
        makeStatusBarTransparentAndHide()
        init(savedInstanceState)
        addListener()
        setupBackButtonCallback()
    }

    abstract fun getViewBinding(): T
    abstract fun init(savedInstanceState: Bundle?)
    abstract fun addListener()
    abstract fun onBackPressedDispatcher()

    private fun setupBackButtonCallback() {
        val callback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onBackPressedDispatcher()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)

    }

    private fun makeStatusBarTransparentAndHide() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }
    }

    fun moveToDeletedFolder(originalFile: File): File? {
        val deletedDir = File(this.getExternalFilesDir(null), "recycle_bin")
        if (!deletedDir.exists()) deletedDir.mkdirs()

        val newFile = File(deletedDir, originalFile.name)
        return try {
            originalFile.renameTo(newFile)
            newFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    protected fun showLoading() {
        if (customLoadingDialog?.isShowing == true) return
        val dialogView = layoutInflater.inflate(com.photogallery.R.layout.dialog_loading, null)
        customLoadingDialog = android.app.Dialog(this).apply {
            setContentView(dialogView)
            setCancelable(false)
            window?.setBackgroundDrawableResource(R.color.transparent)
            show()
        }
    }

    protected fun hideLoading() {
        customLoadingDialog?.dismiss()
        customLoadingDialog = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val currentTheme = ePreferences.getInt(PREF_THEME, THEME_SYSTEM_DEFAULT)
        if (currentTheme == THEME_SYSTEM_DEFAULT) {
            recreate()
        }
    }

    fun getAttributeColor(attrId: Int): Int {
        val typedArray = obtainStyledAttributes(intArrayOf(attrId))
        val color = typedArray.getColor(0, 0)
        typedArray.recycle()
        return color
    }

    fun Activity.nextScreenAnimation() {
        overridePendingTransition(
            com.photogallery.R.anim.in_right,
            com.photogallery.R.anim.out_left
        )
    }

    fun Activity.backScreenAnimation() {
        overridePendingTransition(
            com.photogallery.R.anim.in_left,
            com.photogallery.R.anim.out_right
        )
    }
}