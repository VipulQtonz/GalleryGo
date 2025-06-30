package com.photogallery.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.adapter.CustomPopupMenuAdapter
import com.photogallery.adapter.SelectedOptionsAdapter
import com.photogallery.databinding.ActivityHomeBinding
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.db.model.MediaDataEntity
import com.photogallery.dialog.ExitAppCustomDialog
import com.photogallery.dialog.RateUsAppCustomDialog
import com.photogallery.fragment.ExploreFragment
import com.photogallery.fragment.PhotosFragment
import com.photogallery.fragment.SearchFragment
import com.photogallery.fragment.VideosFragment
import com.photogallery.model.EditOptionItems
import com.photogallery.model.MediaData
import com.photogallery.model.PopupMenuItem
import com.photogallery.utils.SharedPreferenceHelper
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class HomeActivity : BaseActivity<ActivityHomeBinding>() {

    private var currentSelectedItemId: Int = 0
    private var popupMenuItemSelectedId: Int = 1
    private var currentFragment: Fragment? = null

    override fun getViewBinding(): ActivityHomeBinding {
        return ActivityHomeBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        ePreferences.putBoolean("isCompleteGetStartAndPermissionFlow", true)
        MyApplication.isPhotoFetchReload = true
        MyApplication.isVideoFetchReload = true
        currentFragment = supportFragmentManager.findFragmentById(binding.frameLayoutHome.id)
            ?: PhotosFragment.newInstance(this, false)

        setDefaultSelected(savedInstanceState)
        handleInsets()

        val recyclerView = binding.rwSelectedPhotosOptions
        recyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        val editOptions = mutableListOf(
            EditOptionItems(R.drawable.ic_share, getString(R.string.share)),
            EditOptionItems(R.drawable.ic_delete_new, getString(R.string.delete)),
            EditOptionItems(R.drawable.ic_lock_new, getString(R.string.lock)),
            EditOptionItems(R.drawable.ic_add_to_albom_new, getString(R.string.add_to_album)),
            EditOptionItems(R.drawable.ic_move_new, getString(R.string.move_to_album)),
        )

        val adapter = SelectedOptionsAdapter(editOptions).apply {
            setOnItemClickListener { position ->
                when (editOptions[position].label) {
                    getString(R.string.share) -> shareSelectedMedia()
                    getString(R.string.delete) -> deleteSelectedMedia()
                    getString(R.string.lock) -> showLockPhotoDialog()
                    getString(R.string.add_to_album) -> addToAlbumSelected()
                    getString(R.string.move_to_album) -> addToAlbumSelected()
                }
            }
        }
        recyclerView.adapter = adapter

        if (MyApplication.instance.hasStoragePermission()) {
            if (ePreferences.getBoolean("isFirstTimeViewHome", true)) {
                Handler(Looper.getMainLooper()).postDelayed({
                    setupTooltip(
                        this,
                        binding.llDropDownMenu,
                        getString(R.string.select_media_type),
                        ArrowOrientation.BOTTOM,
                        ePreferences,
                        "isFirstTimeViewHome"
                    )
                }, 2000)
            }
        }
    }

    private fun setDefaultSelected(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            binding.bottomNavigationView.selectedItemId = R.id.photos
            replaceFragment(PhotosFragment.newInstance(this, false))
        }
    }

    override fun addListener() {
        binding.ivSettings.setOnClickListener {
            startActivity(Intent(this, SettingActivity::class.java))
            nextScreenAnimation()
        }

        binding.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }

        onPopupMenuClick(binding.llDropDownMenu, {
            binding.bottomNavigationView.selectedItemId = R.id.photos
            binding.tvToolbarTitle.text = resources.getString(R.string.photos)
            replaceFragment(PhotosFragment.newInstance(this, false))
        }, {
            binding.bottomNavigationView.selectedItemId = R.id.videos
            binding.tvToolbarTitle.text = resources.getString(R.string.videos)
            replaceFragment(VideosFragment.newInstance(this))
        }, {
            binding.bottomNavigationView.selectedItemId = R.id.photos
            binding.tvToolbarTitle.text = resources.getString(R.string.gifs)
            replaceFragment(PhotosFragment.newInstance(this, true))
        })

        binding.bottomNavigationView.setOnItemSelectedListener {
            if (it.itemId == currentSelectedItemId) {
                return@setOnItemSelectedListener true
            }
            currentSelectedItemId = it.itemId

            when (it.itemId) {
                R.id.photos -> {
                    popupMenuItemSelectedId = 1
                    binding.llDropDownMenu.visibility = View.VISIBLE
                    binding.tvToolbarTitle.text = resources.getString(R.string.photos)
                    binding.tvToolbarNewTitle.visibility = View.GONE
                    replaceFragment(PhotosFragment.newInstance(this, false))
                }

                R.id.videos -> {
                    popupMenuItemSelectedId = 2
                    binding.llDropDownMenu.visibility = View.VISIBLE
                    binding.tvToolbarNewTitle.visibility = View.GONE
                    binding.tvToolbarTitle.text = resources.getString(R.string.videos)
                    replaceFragment(VideosFragment.newInstance(this))
                }

                R.id.search -> {
                    binding.llDropDownMenu.visibility = View.GONE
                    binding.tvToolbarNewTitle.visibility = View.VISIBLE
                    binding.tvToolbarNewTitle.text = getString(R.string.ai_photo_gallery)
                    replaceFragment(SearchFragment.newInstance(this))
                }

                R.id.explore -> {
                    binding.llDropDownMenu.visibility = View.GONE
                    binding.tvToolbarNewTitle.visibility = View.VISIBLE
                    binding.tvToolbarNewTitle.text = getString(R.string.ai_photo_gallery)
                    replaceFragment(ExploreFragment.newInstance(this))
                }
            }
            return@setOnItemSelectedListener true
        }
    }

    private fun onPopupMenuClick(
        view: View, onPhotosClick: () -> Unit, onVideosClick: () -> Unit, onGifsClick: () -> Unit
    ) {
        view.setOnClickListener {
            val popupWindow = PopupWindow(this)
            val listView = ListView(this)

            val menuItems = listOf(
                PopupMenuItem(
                    R.drawable.ic_photos_selector,
                    resources.getString(R.string.photos),
                    1
                ),
                PopupMenuItem(
                    R.drawable.ic_videos_selector,
                    resources.getString(R.string.videos),
                    2
                ),
                PopupMenuItem(R.drawable.ic_gifs_selector, resources.getString(R.string.gifs), 3)
            )

            val adapter = CustomPopupMenuAdapter(this, popupMenuItemSelectedId, menuItems)
            listView.adapter = adapter

            listView.setOnItemClickListener { _, _, position, _ ->
                when (menuItems[position].itemId) {
                    1 -> onPhotosClick()
                    2 -> onVideosClick()
                    3 -> onGifsClick()
                }
                popupMenuItemSelectedId = position + 1
                popupWindow.dismiss()
            }

            val displayMetrics = resources.displayMetrics
            val popupWidth = (displayMetrics.widthPixels * 0.6).toInt()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                view.post {
                    popupWindow.contentView.windowInsetsController?.let { controller ->
                        controller.hide(WindowInsets.Type.systemBars())
                        controller.systemBarsBehavior =
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            } // Remove the else block to avoid systemUiVisibility issues on older APIs

            binding.ivArrow.animate().rotation(180f).setDuration(300).start()
            popupWindow.setOnDismissListener {
                binding.ivArrow.animate().rotation(0f).setDuration(300).start()
            }
            popupWindow.contentView = listView
            popupWindow.isFocusable = true
            popupWindow.width = popupWidth
            popupWindow.height = LinearLayout.LayoutParams.WRAP_CONTENT
            popupWindow.setBackgroundDrawable(
                ContextCompat.getDrawable(
                    this,
                    R.drawable.bg_custom_pop_up
                )
            )
            popupWindow.elevation = 20f
            popupWindow.showAsDropDown(view, -100, 40)
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        MyApplication.isPhotoFetchReload = true
        MyApplication.isVideoFetchReload = true
        currentFragment = fragment
        supportFragmentManager.beginTransaction()
            .replace(binding.frameLayoutHome.id, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun handleInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.bottomNavigationView)) { view, insets ->
            insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, 0, 0, 0)
            insets
        }
    }

    fun showMainToolbarAndBottomNav() {
        binding.rlToolbar.visibility = View.VISIBLE
        binding.rlSelectedItemView.visibility = View.GONE
        binding.bottomNavigationView.visibility = View.VISIBLE
        binding.rwSelectedPhotosOptions.visibility = View.GONE
    }

    fun showSelectionModeToolbarAndOptions() {
        if (ePreferences.getBoolean("isFirstTimeHomeOptionRecyclerView", true)) {
            setupTooltip(
                this,
                binding.FlBottomView,
                getString(R.string.scroll_horizontal_for_available_options),
                ArrowOrientation.BOTTOM,
                ePreferences,
                "isFirstTimeHomeOptionRecyclerView"
            )
        }
        binding.rlToolbar.visibility = View.GONE
        binding.rlSelectedItemView.visibility = View.VISIBLE
        binding.bottomNavigationView.visibility = View.GONE
        binding.rwSelectedPhotosOptions.visibility = View.VISIBLE
    }

    fun updateSelectedCount(count: Int) {
        binding.tvSelectedImages.text = getString(R.string.selected, count)
    }

    override fun onBackPressedDispatcher() {
        if (binding.rlSelectedItemView.isVisible) {
            (supportFragmentManager.findFragmentById(binding.frameLayoutHome.id) as? PhotosFragment)?.clearSelection()
            (supportFragmentManager.findFragmentById(binding.frameLayoutHome.id) as? VideosFragment)?.clearSelection()
        } else {
            dialogExitFromApp()
        }
    }

    fun getSelectedMedia(): List<MediaData> {
        return when (val fragment = currentFragment) {
            is PhotosFragment -> fragment.getSelectedMedia()
            is VideosFragment -> fragment.getSelectedMedia()
            else -> emptyList()
        }
    }

    fun clearSelection() {
        if (currentFragment is PhotosFragment) {
            (currentFragment as PhotosFragment).clearSelection()
        }
        if (currentFragment is VideosFragment) {
            (currentFragment as VideosFragment).clearSelection()
        }
    }

    private fun shareSelectedMedia() {
        val selected = getSelectedMedia()
        if (selected.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_items_selected), Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val shareUris = ArrayList<Uri>()
            selected.forEach { media ->
                shareUris.add(media.uri)
            }
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_via)))
            nextScreenAnimation()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.error_sharing_items, e.message ?: "Unknown error"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun deleteSelectedMedia() {
        val selected = getSelectedMedia()
        if (selected.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_items_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_move_to_recyclebin, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val tvTitle = dialogView.findViewById<TextView>(R.id.tvTitle)
        val tvDescription = dialogView.findViewById<TextView>(R.id.tvDescription)
        val btnMoveToBeen = dialogView.findViewById<Button>(R.id.btnMoveToBeen)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnApply)
        val ivClose = dialogView.findViewById<ImageView>(R.id.ivClose)

        tvTitle.text = getString(R.string.delete_photo)
        tvDescription.text = getString(R.string.are_you_sure_you_want_to_delete_photo)

        btnMoveToBeen.setOnClickListener {
            showLoading()
            dialog.dismiss()
            CoroutineScope(Dispatchers.IO).launch {
                val database = PhotoGalleryDatabase.getDatabase(this@HomeActivity)
                selected.forEach { media ->
                    MyApplication.instance.notifyFileDeleted(media.uri)
                    val favorite = database.photoGalleryDao().getFavoriteById(media.id)
                    if (favorite != null) {
                        database.photoGalleryDao().deleteFavorite(favorite)
                    }

                    val file = File(media.path)
                    val newFile = moveToDeletedFolder(file)
                    if (newFile != null) {
                        try {
                            if (file.exists()) {
                                file.delete()
                            }

                            val mediaDataEntity = MediaDataEntity(
                                id = media.id,
                                name = media.name,
                                originalPath = newFile.path,
                                recyclePath = newFile.absolutePath,
                                uri = Uri.fromFile(newFile),
                                dateTaken = media.dateTaken,
                                isVideo = media.isVideo,
                                duration = media.duration,
                                deletedAt = System.currentTimeMillis()
                            )
                            database.photoGalleryDao().insertDeletedMedia(mediaDataEntity)

                            // Notify MediaStore of the deletion
                            val contentResolver = this@HomeActivity.contentResolver
                            try {
                                val uri = media.uri
                                contentResolver.delete(uri, null, null)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                MediaScannerConnection.scanFile(
                                    this@HomeActivity,
                                    arrayOf(newFile.absolutePath),
                                    arrayOf(if (media.isVideo) "video/*" else "image/*")
                                ) { _, _ ->
                                    // Scan complete, no action needed
                                }
                            }
                            withContext(Dispatchers.Main) {
                                hideLoading()
                                (supportFragmentManager.findFragmentById(binding.frameLayoutHome.id) as? PhotosFragment)?.removeMedia(
                                    media
                                )
                                (supportFragmentManager.findFragmentById(binding.frameLayoutHome.id) as? VideosFragment)?.removeMedia(
                                    media
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    hideLoading()
                    clearSelection()
                    dialog.dismiss()
                }
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        ivClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

//    private fun lockSelectedMedia() {
//        val selectedMedia = getSelectedMedia()
//        if (selectedMedia.isEmpty()) return
//
//        CoroutineScope(Dispatchers.IO).launch {
//            val lockedDir = File(
//                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
//                "LockedMedia"
//            )
//            if (!lockedDir.exists()) lockedDir.mkdirs()
//
//            val database = PhotoGalleryDatabase.getDatabase(this@HomeActivity)
//            var count = 0
//            for (media in selectedMedia) {
//                // First check and remove from favorites if exists
//                MyApplication.instance.notifyFileDeleted(media.uri)
//                val favorite = database.photoGalleryDao().getFavoriteById(media.id)
//                if (favorite != null) {
//                    database.photoGalleryDao().deleteFavorite(favorite)
//                }
//
//                val originalFile = File(media.path)
//                val encryptedFile = File(lockedDir, originalFile.name + ".lockimg")
//
//                try {
//                    FileEncryptor.encryptFile(originalFile, encryptedFile)
//                    count++
//
//                    // Delete original file after successful encryption
//                    if (originalFile.exists()) {
//                        originalFile.delete()
//                    }
//
//                    // Notify MediaStore of the deletion
//                    val contentResolver = this@HomeActivity.contentResolver
//                    try {
//                        val uri = media.uri
//                        contentResolver.delete(uri, null, null)
//                    } catch (e: Exception) {
//                        e.printStackTrace()
//                        // Optionally, use MediaScannerConnection to scan the new file location
//                        MediaScannerConnection.scanFile(
//                            this@HomeActivity,
//                            arrayOf(lockedDir.absolutePath),
//                            arrayOf(if (media.isVideo) "video/*" else "image/*")
//                        ) { _, _ ->
//                            // Scan complete, no action needed
//                        }
//                    }
//                    // Notify fragment to remove the item
//                    withContext(Dispatchers.Main) {
//                        (supportFragmentManager.findFragmentById(binding.frameLayoutHome.id) as? PhotosFragment)?.removeMedia(
//                            media
//                        )
//                        (supportFragmentManager.findFragmentById(binding.frameLayoutHome.id) as? VideosFragment)?.removeMedia(
//                            media
//                        )
//                    }
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//
//            withContext(Dispatchers.Main) {
//                clearSelection()
//                if (count > 0) {
//                    Toast.makeText(this@HomeActivity, "$count items locked", Toast.LENGTH_SHORT)
//                        .show()
//                }
//            }
//        }
//    }


    private fun lockSelectedMedia() {
        val selectedMedia = getSelectedMedia()
        if (selectedMedia.isEmpty()) return

        CoroutineScope(Dispatchers.IO).launch {
            val lockedDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "LockedMedia"
            )
            if (!lockedDir.exists()) lockedDir.mkdirs()

            val database = PhotoGalleryDatabase.getDatabase(this@HomeActivity)
            var count = 0
            for (media in selectedMedia) {
                MyApplication.instance.notifyFileDeleted(media.uri)
                val favorite = database.photoGalleryDao().getFavoriteById(media.id)
                if (favorite != null) {
                    database.photoGalleryDao().deleteFavorite(favorite)
                }

                val originalFile = File(media.path)
                val newFile = File(lockedDir, originalFile.name + ".lockimg")

                try {
                    // Move and rename file
                    originalFile.renameTo(newFile)
                    count++

                    // Notify MediaStore of the deletion
                    val contentResolver = this@HomeActivity.contentResolver
                    try {
                        val uri = media.uri
                        contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        // Optionally, use MediaScannerConnection to scan the new file location
                        MediaScannerConnection.scanFile(
                            this@HomeActivity,
                            arrayOf(lockedDir.absolutePath),
                            arrayOf(if (media.isVideo) "video/*" else "image/*")
                        ) { _, _ ->
                            // Scan complete, no action needed
                        }
                    }
                    // Notify fragment to remove the item
                    withContext(Dispatchers.Main) {
                        hideLoading()
                        (supportFragmentManager.findFragmentById(binding.frameLayoutHome.id) as? PhotosFragment)?.removeMedia(
                            media
                        )
                        (supportFragmentManager.findFragmentById(binding.frameLayoutHome.id) as? VideosFragment)?.removeMedia(
                            media
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            withContext(Dispatchers.Main) {
                hideLoading()
                clearSelection()
                if (count > 0) {
                    Toast.makeText(
                        this@HomeActivity,
                        getString(R.string.items_locked_, count), Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
    }

    private fun addToAlbumSelected() {
        val selectedMedia = getSelectedMedia()
        if (selectedMedia.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_items_selected), Toast.LENGTH_SHORT).show()
            return
        }

        MyApplication.instance.setSelectedMediaAndAction(selectedMedia, "MOVE")
        startActivity(Intent(this, AddToAlbumActivity::class.java))
        nextScreenAnimation()
    }

    override fun onStop() {
        super.onStop()
        clearSelection()
    }

    private fun showLockPhotoDialog(
    ) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_remove_favorites, null)
        val dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(dialogView)
            setCancelable(true)
            window?.apply {
                setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.CENTER)
            }
        }

        val btnLock = dialogView.findViewById<AppCompatButton>(R.id.btnRemoveFromFavorite)
        val btnCancel = dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
        val ivClose = dialogView.findViewById<AppCompatImageView>(R.id.ivClose)
        val tvTitle = dialogView.findViewById<AppCompatTextView>(R.id.tvTitle)
        val tvDescription = dialogView.findViewById<AppCompatTextView>(R.id.tvDescription)

        btnLock.text = getString(R.string.lock)
        tvTitle.text = getString(R.string.lock_photo)
        tvDescription.text = getString(R.string.are_you_sure_want_to_lock_this_photo)
        btnLock.setOnClickListener {
            showLoading()
            lockSelectedMedia()
            dialog.dismiss()
        }

        ivClose.setOnClickListener {
            dialog.dismiss()
        }

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun dialogExitFromApp() {
        if (!ePreferences.getBoolean(SharedPreferenceHelper.RATE_US, false)) {
            ExitAppCustomDialog(
                isFromRateUse = true,
                context = this,
                onYesClick = {
                    RateUsAppCustomDialog(this as Activity, true).show()
                }
            ).show()
        } else {
            ExitAppCustomDialog(
                isFromRateUse = false,
                context = this,
                onYesClick = {
                }
            ).show()
        }
    }
}