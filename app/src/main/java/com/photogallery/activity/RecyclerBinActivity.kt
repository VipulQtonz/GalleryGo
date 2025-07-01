package com.photogallery.activity

import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.GridLayoutManager
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.adapter.RecyclerBinAdapter
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityRecyclerBinBinding
import com.photogallery.databinding.DialogDeleteOptionsBinding
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.model.MediaData
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class RecyclerBinActivity : BaseActivity<ActivityRecyclerBinBinding>() {
    private lateinit var adapter: RecyclerBinAdapter
    var personaliseLayoutDialog: AlertDialog? = null
    private var deleteInDays = 30

    override fun getViewBinding(): ActivityRecyclerBinBinding {
        return ActivityRecyclerBinBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        deleteInDays = ePreferences.getInt("deleteInDays", 30)
        setupToolbar()
        setupRecyclerView()
        loadDeletedMedia()

        binding.llRecyclerBinEmpty.tvTitle.text = getString(R.string.no_photos_yet)
        binding.llRecyclerBinEmpty.tvDescription.text =
            getString(R.string.files_deleted_within_30_days_can_be_restored_from_the_recycle_bin)
        binding.llRecyclerBinEmpty.ivIllustrator.setImageResource(R.drawable.ic_recycle_bin__empty)
        binding.llRecyclerBinEmpty.btnOpen.visibility = View.GONE


        if (ePreferences.getBoolean("isFirstTimeRecycleMoreOption", true)) {
            setupTooltip(
                this,
                binding.toolbar.ivMore,
                getString(R.string.tap_to_view_delete_options),
                ArrowOrientation.BOTTOM,
                ePreferences,
                "isFirstTimeRecycleMoreOption"
            )
        }
    }

    override fun addListener() {
        binding.ivSelectImages.setOnClickListener {
            if (adapter.getSelectedMedia().size == adapter.currentList.size) {
                adapter.clearSelection()
            } else {
                adapter.selectAll()
            }
        }

        binding.llRestore.setOnClickListener {
            if (adapter.getSelectedMedia().isNotEmpty()) {
                restoreSelectedMedia()
            } else {
                Toast.makeText(this, getString(R.string.please_select_image), Toast.LENGTH_SHORT)
                    .show()
            }
        }

        binding.llDelete.setOnClickListener {
            if (adapter.getSelectedMedia().isNotEmpty()) {
                showDeleteConfirmationDialog()
            } else {
                Toast.makeText(this, getString(R.string.please_select_image), Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    override fun onBackPressedDispatcher() {
        if (adapter.getSelectedMedia().isNotEmpty()) {
            adapter.clearSelection()
        } else {
            backScreenAnimation()
            finish()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.ivBack.setOnClickListener {
            if (adapter.getSelectedMedia().isNotEmpty()) {
                adapter.clearSelection()
            } else {
                backScreenAnimation()
                finish()
            }
        }
        binding.toolbar.tvToolbarTitle.text = getString(R.string.recycle_bin)
        binding.toolbar.ivMore.visibility = View.VISIBLE
        binding.toolbar.ivMore.setOnClickListener {
            showDeleteOptionDialog()
        }
    }

    private fun showDeleteOptionDialog() {
        val builder = AlertDialog.Builder(this)
        val layoutDialogBinding = DialogDeleteOptionsBinding.inflate(LayoutInflater.from(this))
        builder.setCancelable(false)

        when (deleteInDays) {
            30 -> {
                layoutDialogBinding.rb30Days.isChecked = true
                layoutDialogBinding.rb15Days.isChecked = false
                layoutDialogBinding.rb7Days.isChecked = false
            }

            15 -> {
                layoutDialogBinding.rb15Days.isChecked = true
                layoutDialogBinding.rb30Days.isChecked = false
                layoutDialogBinding.rb7Days.isChecked = false
            }

            7 -> {
                layoutDialogBinding.rb7Days.isChecked = true
                layoutDialogBinding.rb30Days.isChecked = false
                layoutDialogBinding.rb15Days.isChecked = false
            }
        }

        layoutDialogBinding.rb30Days.setOnClickListener {
            layoutDialogBinding.rb30Days.isChecked = true
            layoutDialogBinding.rb15Days.isChecked = false
            layoutDialogBinding.rb7Days.isChecked = false
            deleteInDays = 30
        }
        layoutDialogBinding.rb15Days.setOnClickListener {
            layoutDialogBinding.rb15Days.isChecked = true
            layoutDialogBinding.rb30Days.isChecked = false
            layoutDialogBinding.rb7Days.isChecked = false
            deleteInDays = 15
        }
        layoutDialogBinding.rb7Days.setOnClickListener {
            layoutDialogBinding.rb7Days.isChecked = true
            layoutDialogBinding.rb30Days.isChecked = false
            layoutDialogBinding.rb15Days.isChecked = false
            deleteInDays = 7
        }

        layoutDialogBinding.btnSave.setOnClickListener {
            val previousValue = ePreferences.getInt("deleteInDays", 30)
            if (deleteInDays != previousValue) {
                ePreferences.putInt("deleteInDays", deleteInDays)
                loadDeletedMedia()
            }
            personaliseLayoutDialog?.dismiss()
        }

        layoutDialogBinding.btnCancel.setOnClickListener {
            personaliseLayoutDialog?.dismiss()
        }

        builder.setView(layoutDialogBinding.root)
        personaliseLayoutDialog = builder.create()
        personaliseLayoutDialog?.setCanceledOnTouchOutside(true)
        personaliseLayoutDialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        personaliseLayoutDialog?.show()
    }

    private fun setupRecyclerView() {
        adapter = RecyclerBinAdapter(
            onItemClick = { media -> showItemOptionsDialog(media) },
            onSelectionModeChange = { isSelectionMode -> },
            onSelectionCountChange = { count -> updateSelectionIcon(count) })

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@RecyclerBinActivity, 3)
            adapter = this@RecyclerBinActivity.adapter
        }
    }

    private fun updateSelectionIcon(selectedCount: Int) {
        if (selectedCount > 0) {
            binding.llRestoreAndDelete.visibility = View.VISIBLE
            if (ePreferences.getBoolean("isFirstTimeRecycleRestore", true)) {
                setupTooltip(
                    this,
                    binding.llRestore,
                    getString(R.string.tap_to_restore_the_selected_media),
                    ArrowOrientation.TOP,
                    ePreferences,
                    "null"
                )
                {
                    setupTooltip(
                        this,
                        binding.llDelete,
                        getString(R.string.tap_to_delete_permanently_this_action_cannot_be_undone),
                        ArrowOrientation.TOP,
                        ePreferences,
                        "isFirstTimeRecycleRestore"
                    )
                }
            }
        } else {
            binding.llRestoreAndDelete.visibility = View.GONE
        }
        val allSelected = selectedCount == adapter.currentList.size
        binding.ivSelectImages.setImageResource(
            if (allSelected) R.drawable.ic_select_active else R.drawable.ic_select_inactive
        )
    }

    private fun restoreSelectedMedia() {
        showLoading()
        val selectedItems = adapter.getSelectedMedia()
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                var allSuccessful = true
                selectedItems.forEach { media ->
                    try {
                        restoreMedia(media)
                    } catch (e: Exception) {
                        allSuccessful = false
                        withContext(Dispatchers.Main) {
                            showToast("Error restoring ${media.name}: ${e.message}")
                        }
                    }
                }
                allSuccessful
            }

            adapter.clearSelection()
            hideLoading()
            loadDeletedMedia() // Refresh the recycler view
        }
    }

    private fun showDeleteConfirmationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_all_photos, null)

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)

        dialog.window?.apply {
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }

        val btnCancel = dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
        val btnYesDelete = dialogView.findViewById<AppCompatButton>(R.id.btnYesDelete)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnYesDelete.setOnClickListener {
            showLoading()
            val selectedItems = adapter.getSelectedMedia()
            selectedItems.forEach { media ->
                deletePermanently(media)
            }
            adapter.clearSelection()
            hideLoading()
            dialog.dismiss()
        }
        dialog.show()
    }


    private fun loadDeletedMedia() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = PhotoGalleryDatabase.getDatabase(this@RecyclerBinActivity)
            val deletedMedia = database.photoGalleryDao().getAllDeletedMedia()

            val mediaList = deletedMedia.map { entity ->
                val daysPassed =
                    ((System.currentTimeMillis() - entity.deletedAt) / (1000 * 60 * 60 * 24)).toInt()
                val remaining = deleteInDays - daysPassed

                MediaData(
                    id = entity.id,
                    name = entity.name,
                    path = entity.originalPath,
                    uri = entity.uri,
                    dateTaken = entity.dateTaken,
                    isVideo = entity.isVideo,
                    duration = entity.duration
                ).apply {
                    daysRemaining = remaining
                }
            }

            runOnUiThread {
                adapter.submitList(mediaList)
                binding.llRecyclerBinEmpty.llEmptyLayout.visibility =
                    if (mediaList.isEmpty()) View.VISIBLE else View.GONE
                binding.llMain.visibility = if (mediaList.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun restoreMedia(media: MediaData) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deletedFile = File(media.path)
                if (!deletedFile.exists()) {
                    showToast(getString(R.string.file_not_found_in_recycle_bin))
                    return@launch
                }

                val publicDir = if (media.isVideo) {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                } else {
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                }

                val restoredFile = File(publicDir, deletedFile.name)
                publicDir.mkdirs()

                deletedFile.copyTo(restoredFile, overwrite = true)
                deletedFile.delete()

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DATA, restoredFile.absolutePath)
                    put(MediaStore.MediaColumns.DISPLAY_NAME, restoredFile.name)
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        if (media.isVideo) "video/*" else "image/*"
                    )
                    put(MediaStore.MediaColumns.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }

                val contentUri = if (media.isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                contentResolver.insert(contentUri, values)

                MediaScannerConnection.scanFile(
                    this@RecyclerBinActivity,
                    arrayOf(restoredFile.absolutePath),
                    arrayOf(if (media.isVideo) "video/*" else "image/*"),
                    null
                )
                MyApplication.isVideoFetchReload = true
                MyApplication.isPhotoFetchReload = true
                val database = PhotoGalleryDatabase.getDatabase(this@RecyclerBinActivity)
                database.photoGalleryDao().deleteDeletedMediaById(media.id)
                hideLoading()
                loadDeletedMedia()
            } catch (e: Exception) {
                hideLoading()
                showToast("Error: ${e.message}")
            }
        }
    }

    private fun deletePermanently(media: MediaData) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = File(media.path)
                if (file.exists()) {
                    file.delete()
                }
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(file)
                sendBroadcast(intent)

                val database = PhotoGalleryDatabase.getDatabase(this@RecyclerBinActivity)
                database.photoGalleryDao().deleteDeletedMediaById(media.id)

                runOnUiThread {
                    loadDeletedMedia()
                }
            } catch (_: Exception) {
                runOnUiThread {
                    showToast(getString(R.string.error_deleting_media))
                }
            }
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this@RecyclerBinActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showItemOptionsDialog(media: MediaData) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_restore_delete, null)

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

        val btnRestore = dialogView.findViewById<AppCompatButton>(R.id.btnRestore)
        val btnDelete = dialogView.findViewById<AppCompatButton>(R.id.btnDelete)
        val ivClose = dialogView.findViewById<AppCompatImageView>(R.id.ivClose)

        btnRestore.setOnClickListener {
            showLoading()
            restoreMedia(media)
            dialog.dismiss()
            loadDeletedMedia()
        }

        btnDelete.setOnClickListener {
            deletePermanently(media)
            dialog.dismiss()
            loadDeletedMedia()
        }

        ivClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}