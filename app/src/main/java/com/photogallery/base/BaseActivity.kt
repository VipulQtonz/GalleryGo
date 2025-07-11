package com.photogallery.base

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.viewbinding.ViewBinding
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.utils.Const.THEME_SYSTEM_DEFAULT
import com.photogallery.utils.SharedPreferenceHelper
import java.io.File

abstract class BaseActivity<T : ViewBinding> : AppCompatActivity() {
    private var customLoadingDialog: Dialog? = null
    internal lateinit var ePreferences: SharedPreferenceHelper
    private lateinit var manageStorageLauncher: ActivityResultLauncher<Intent>
    private lateinit var readStoragePermissionLauncher: ActivityResultLauncher<Array<String>>
    var permissionDialog: AlertDialog? = null
    lateinit var binding: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ePreferences = MyApplication.Companion.instance.pref
        val theme = ePreferences.getInt(SharedPreferenceHelper.Companion.PREF_APP_COLOR, 0)

        when (theme) {
            0 -> {
                setTheme(R.style.AppTheme_1)
            }

            1 -> {
                setTheme(R.style.AppTheme_2)
            }

            2 -> {
                setTheme(R.style.AppTheme_3)
            }

            3 -> {
                setTheme(R.style.AppTheme_4)
            }

            4 -> {
                setTheme(R.style.AppTheme_5)
            }

            5 -> {
                setTheme(R.style.AppTheme_6)
            }

            6 -> {
                setTheme(R.style.AppTheme_7)
            }

            7 -> {
                setTheme(R.style.AppTheme_8)
            }

            else -> {
                setTheme(R.style.AppTheme_1)
            }
        }

        binding = getViewBinding()
        setContentView(binding.root)
        enableEdgeToEdge()
        makeStatusBarTransparentAndHide()
        init(savedInstanceState)
        addListener()
        setupBackButtonCallback()

        readStoragePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val isGranted = permissions.entries.all { it.value }
                if (isGranted) {
                    onStoragePermissionGranted()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.permission_denied), Toast.LENGTH_SHORT
                    ).show()
                }
            }

        manageStorageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                    onStoragePermissionGranted()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.permission_not_granted), Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
            window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_loading, null)
        customLoadingDialog = Dialog(this).apply {
            setContentView(dialogView)
            setCancelable(false)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            show()
        }
    }

    protected fun hideLoading() {
        customLoadingDialog?.dismiss()
        customLoadingDialog = null
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        val currentTheme = ePreferences.getInt(
            SharedPreferenceHelper.Companion.PREF_THEME,
            THEME_SYSTEM_DEFAULT
        )
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
            R.anim.in_right, R.anim.out_left
        )
    }

    fun Activity.backScreenAnimation() {
        overridePendingTransition(
            R.anim.in_left, R.anim.out_right
        )
    }

    protected fun showPermissionDialog(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setCancelable(false)

        val view = View.inflate(context, R.layout.dialog_permission_request, null)
        val btnGrantAccess: Button = view.findViewById(R.id.btnAllow)
        val btnCancel: Button = view.findViewById(R.id.btnCancel)
        btnGrantAccess.setOnClickListener {
            requestStoragePermission()
            permissionDialog?.dismiss()
        }
        btnCancel.setOnClickListener {
            permissionDialog?.dismiss()
        }

        builder.setView(view)
        permissionDialog = builder.create()
        permissionDialog?.setCanceledOnTouchOutside(false)
        permissionDialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        permissionDialog?.show()
    }

    protected open fun onStoragePermissionGranted() {
    }

    private fun requestStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = ("package:" + this.packageName).toUri()
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                readStoragePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                )
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // Request both read and write for Android 10
                readStoragePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }

            else -> {
                readStoragePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }
}