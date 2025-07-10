package com.photogallery.activity

import android.app.AlertDialog
import android.app.Dialog
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.adapter.LockedMediaAdapter
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityLockedImagesBinding
import com.photogallery.databinding.DialogLockOptionsBinding
import com.photogallery.utils.MediaDataSerializer
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LockedImagesActivity : BaseActivity<ActivityLockedImagesBinding>() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LockedMediaAdapter
    private val lockedMedia = mutableListOf<File>() // Changed to List<File>
    private val selectImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.let { data ->
                    val selectedMediaJson = data.getStringExtra("selected_media_json")
                    if (!selectedMediaJson.isNullOrEmpty()) {
                        val selectedMedia = MediaDataSerializer.deserialize(selectedMediaJson)
                        lockSelectedMedia(selectedMedia.map { it.uri })
                    }
                }
            }
        }

    override fun getViewBinding(): ActivityLockedImagesBinding {
        return ActivityLockedImagesBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        binding.progressBar.visibility = View.VISIBLE
        recyclerView = findViewById(R.id.recyclerViewLocked)
        recyclerView.layoutManager = GridLayoutManager(this, 3)

        adapter = LockedMediaAdapter(
            this@LockedImagesActivity,
            onItemClick = { media -> showItemOptionsDialog(media) }, // Updated to pass File
            onSelectionModeChange = { isSelectionMode ->
                binding.llMoveAndDelete.visibility =
                    if (isSelectionMode) View.VISIBLE else View.GONE
            },
            onSelectionCountChange = { count -> updateSelectionIcon(count) }
        )
        recyclerView.adapter = adapter

        binding.llLockedPhotoEmpty.tvTitle.text = getString(R.string.no_locked_item_yet)
        binding.llLockedPhotoEmpty.tvDescription.text =
            getString(R.string.no_items_into_locked_folder_yet_lock_your_private_moment)
        binding.llLockedPhotoEmpty.ivIllustrator.setImageResource(R.drawable.ic_lock_empty)
        binding.llLockedPhotoEmpty.btnOpen.text = getString(R.string.add)

        binding.toolbar.tvToolbarTitle.text = getString(R.string.locked)
        binding.toolbar.ivMore.visibility = View.VISIBLE
        loadLockedFiles()

        if (ePreferences.getBoolean("isFirstTimeLockViewMoreOptions", true)) {
            setupTooltip(
                this,
                binding.toolbar.ivMore,
                getString(R.string.tap_to_view_more_options),
                ArrowOrientation.TOP,
                ePreferences,
                "isFirstTimeLockViewMoreOptions"
            )
        }
    }

    override fun addListener() {
        binding.toolbar.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }

        binding.toolbar.ivMore.setOnClickListener {
            showOptionsDialog()
        }

        binding.ivSelectImages.setOnClickListener {
            if (adapter.getSelectedMedia().size == adapter.currentList.size) {
                adapter.clearSelection()
            } else {
                adapter.selectAll()
            }
        }

        binding.llMove.setOnClickListener {
            if (adapter.getSelectedMedia().isNotEmpty()) {
                showLoading()
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

        binding.fabLockMedia.setOnClickListener {
            openSelectImageActivity()
        }

        binding.llLockedPhotoEmpty.btnOpen.setOnClickListener {
            openSelectImageActivity()
        }
    }

    private fun openSelectImageActivity() {
        val intent = Intent(this, SelectImageActivity::class.java).apply {
            putExtra("isSelectionMode", true)
        }
        selectImageLauncher.launch(intent)
    }

    private fun showOptionsDialog() {
        val dialogBinding = DialogLockOptionsBinding.inflate(LayoutInflater.from(this))
        dialogBinding.switchFingerprint.isChecked =
            ePreferences.getBoolean("fingerprint_enabled", false)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.tvModifyPin.setOnClickListener {
            dialog.dismiss()
            SetPinActivity.startForModifyPin(this)
        }

        dialogBinding.tvChangeEmail.setOnClickListener {
            dialog.dismiss()
            SetPinActivity.startForChangeEmail(this)
        }

        dialogBinding.tvChangeSecurityQuestion.setOnClickListener {
            dialog.dismiss()
            SetPinActivity.startForChangeSecurityQuestion(this)
        }

        dialogBinding.switchContainer.setOnClickListener {
            dialogBinding.switchFingerprint.toggle()
        }

        dialogBinding.switchFingerprint.setOnCheckedChangeListener { _, isChecked ->
            ePreferences.putBoolean("fingerprint_enabled", isChecked)
        }

        dialog.show()
        dialog.window?.setBackgroundDrawable(
            ContextCompat.getDrawable(this, R.drawable.bg_rounded_corner_dialog)
        )
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    private fun loadLockedFiles() {
        CoroutineScope(Dispatchers.IO).launch {
            val lockedDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "LockedMedia"
            )
            val files = if (lockedDir.exists()) {
                lockedDir.listFiles { _, name -> name.endsWith(".lockimg") }?.toList()
                    ?: emptyList()
            } else {
                emptyList()
            }

            val mediaList = files.map { file ->
                file // Directly use File objects
            }

            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
                lockedMedia.clear()
                lockedMedia.addAll(mediaList)
                adapter.submitList(lockedMedia.toList())
                binding.llLockedPhotoEmpty.llEmptyLayout.visibility =
                    if (lockedMedia.isEmpty()) View.VISIBLE else View.GONE
                binding.llMain.visibility = if (lockedMedia.isEmpty()) View.GONE else View.VISIBLE
                if (lockedMedia.isNotEmpty()) {
                    if (ePreferences.getBoolean("isFirstTimeLockFAB", true)) {
                        setupTooltip(
                            this@LockedImagesActivity,
                            binding.fabLockMedia,
                            getString(R.string.click_to_lock_more_media),
                            ArrowOrientation.TOP,
                            ePreferences,
                            "isFirstTimeLockFAB"
                        )
                    }
                }
            }
        }
    }

    private fun unlockImage(encryptedFile: File) {
        val unlockedDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "UnlockedMedia"
        )
        if (!unlockedDir.exists()) unlockedDir.mkdirs()

        val originalName = encryptedFile.name.removeSuffix(".lockimg")
        val outputFile = File(unlockedDir, originalName)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                encryptedFile.renameTo(outputFile) // Simply rename and move
                MediaScannerConnection.scanFile(
                    this@LockedImagesActivity,
                    arrayOf(outputFile.absolutePath), null, null
                )

                withContext(Dispatchers.Main) {
                    hideLoading()
                    loadLockedFiles()
                }
            } catch (_: Exception) {
                hideLoading()
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@LockedImagesActivity,
                        getString(R.string.unlock_failed), Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
    }

    private fun updateSelectionIcon(selectedCount: Int) {
        if (selectedCount > 0) {
            binding.llMoveAndDelete.visibility = View.VISIBLE
            if (ePreferences.getBoolean("isFirstTimeLockMove", true)) {
                setupTooltip(
                    this,
                    binding.llMove,
                    getString(R.string.click_to_move_selected_media_from_lock),
                    ArrowOrientation.TOP,
                    ePreferences,
                    "isFirstTimeLockMove"
                )
            }
        } else {
            binding.llMoveAndDelete.visibility = View.GONE
        }
        val allSelected = selectedCount == adapter.currentList.size
        binding.ivSelectImages.setImageResource(
            if (allSelected) R.drawable.ic_select_active else R.drawable.ic_select_inactive
        )
    }

    private fun restoreSelectedMedia() {
        val selectedItems = adapter.getSelectedMedia()
        selectedItems.forEach { file ->
            unlockImage(file)
        }
        adapter.clearSelection()
        hideLoading()
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
            selectedItems.forEach { file ->
                deletePermanently(file)
            }
            adapter.clearSelection()
            hideLoading()
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun deletePermanently(file: File) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (file.exists()) {
                    file.delete()
                }
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                intent.data = Uri.fromFile(file)
                sendBroadcast(intent)

                runOnUiThread {
                    loadLockedFiles()
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
            Toast.makeText(this@LockedImagesActivity, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showItemOptionsDialog(file: File) {
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

        val tvTitle = dialogView.findViewById<AppCompatTextView>(R.id.tvTitle)
        val tvDescription = dialogView.findViewById<AppCompatTextView>(R.id.tvDescription)
        val btnMove = dialogView.findViewById<AppCompatButton>(R.id.btnRestore)
        val btnDelete = dialogView.findViewById<AppCompatButton>(R.id.btnDelete)
        val ivClose = dialogView.findViewById<AppCompatImageView>(R.id.ivClose)

        tvTitle.text = getString(R.string.move_or_delete)
        tvDescription.text = getString(R.string.please_select_any_one_operation_n_move_or_delete)
        btnMove.text = getString(R.string.move)

        btnMove.setOnClickListener {
            showLoading()
            unlockImage(file)
            dialog.dismiss()
            loadLockedFiles()
        }

        btnDelete.setOnClickListener {
            deletePermanently(file)
            dialog.dismiss()
            loadLockedFiles()
        }

        ivClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun lockSelectedMedia(selectedUris: List<Uri>) {
        if (selectedUris.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_images_selected), Toast.LENGTH_SHORT).show()
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val lockedDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "LockedMedia"
            )
            if (!lockedDir.exists()) lockedDir.mkdirs()

            var count = 0
            for (uri in selectedUris) {
                try {
                    val filePath = getFilePathFromUri(uri)
                    if (filePath != null) {
                        val originalFile = File(filePath)
                        val newFile = File(lockedDir, "${originalFile.name}.lockimg")

                        originalFile.renameTo(newFile) // Simply rename and move
                        count++

                        if (originalFile.exists()) {
                            originalFile.delete()
                            MediaScannerConnection.scanFile(
                                this@LockedImagesActivity,
                                arrayOf(originalFile.absolutePath),
                                null,
                                null
                            )
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@LockedImagesActivity,
                            getString(R.string.failed_to_lock, uri.path),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            withContext(Dispatchers.Main) {
                loadLockedFiles()
                if (count > 0) {
                    Toast.makeText(
                        this@LockedImagesActivity,
                        getString(R.string.items_locked, count),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        var filePath: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                filePath = cursor.getString(columnIndex)
            }
        }
        return filePath
    }
}