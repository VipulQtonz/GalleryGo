package com.photogallery.fragment

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.utils.SharedPreferenceHelper
import com.photogallery.utils.ViewMode

abstract class BaseFragment<T : ViewBinding> : Fragment() {
    lateinit var binding: T
    private lateinit var fragmentContext: Context
    internal var ePreferences: SharedPreferenceHelper? = null
    private lateinit var manageStorageLauncher: ActivityResultLauncher<Intent>
    private lateinit var readStoragePermissionLauncher: ActivityResultLauncher<Array<String>>
    var viewMode: ViewMode = ViewMode.DAY
    var permissionDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readStoragePermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val isGranted = permissions.entries.all { it.value }
                if (isGranted) {
                    onStoragePermissionGranted()
                } else {
                    Toast.makeText(
                        requireContext(),
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
                        requireContext(),
                        getString(R.string.permission_not_granted), Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = getViewBinding(inflater, container)
        return binding.root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        fragmentContext = context
        ePreferences = MyApplication.instance.pref
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        init()
        addListener()
    }

    abstract fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): T
    abstract fun init()
    abstract fun addListener()

    internal fun hasStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Environment.isExternalStorageManager()
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.READ_MEDIA_VIDEO
                        ) == PackageManager.PERMISSION_GRANTED
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                // For Android 10, we need both read and write
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
            }
            else -> {
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    private fun requestStoragePermission() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = ("package:" + requireContext().packageName).toUri()
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

    protected open fun onStoragePermissionGranted() {
    }

    protected fun showPermissionDialog() {
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

    fun dpToPx(dp: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), Resources.getSystem().displayMetrics
    ).toInt()

    fun Activity.nextScreenAnimation() {
        overridePendingTransition(
            R.anim.in_right,
            R.anim.out_left
        )
    }

    fun Activity.backScreenAnimation() {
        overridePendingTransition(
            R.anim.in_left,
            R.anim.out_right
        )
    }
}