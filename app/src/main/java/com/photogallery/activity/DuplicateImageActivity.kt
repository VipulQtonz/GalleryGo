package com.photogallery.activity

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.GridLayoutManager
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.adapter.DuplicateGroupImageAdapter
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityDuplicateImageBinding
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.db.model.MediaDataEntity
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class DuplicateImageActivity : BaseActivity<ActivityDuplicateImageBinding>() {
    override fun getViewBinding(): ActivityDuplicateImageBinding {
        return ActivityDuplicateImageBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        binding.toolbarDuplicate.tvToolbarTitle.text = getString(R.string.duplicates)
        binding.rvDuplicateImages.layoutManager = GridLayoutManager(this, 3)
        MyApplication.duplicateImageGroupsLiveData.observe(this) { duplicateGroups ->
            val adapter = DuplicateGroupImageAdapter(this, duplicateGroups) { group ->
                MyApplication.selectedAlbumImages = group.allUris
                val intent = Intent(this, AlbumViewerActivity::class.java)
                intent.putExtra("albumName", getString(R.string.duplicates))
                startActivity(intent)
                nextScreenAnimation()
            }
            binding.rvDuplicateImages.adapter = adapter
            if (duplicateGroups.isEmpty()) {
                binding.rlPhotoView.visibility = View.GONE
                binding.noDuplicateImageLayout.llEmptyLayout.visibility = View.VISIBLE
            } else {
                binding.rlPhotoView.visibility = View.VISIBLE
                binding.noDuplicateImageLayout.llEmptyLayout.visibility = View.GONE
                if (ePreferences.getBoolean("isFirstTimeDuplicateDelete", true)) {
                    setupTooltip(
                        this,
                        binding.btnRemoveAllDuplicates,
                        getString(R.string.click_to_delete_all_duplicates_it_keep_1_from_each_duplicate),
                        ArrowOrientation.TOP,
                        ePreferences,
                        "isFirstTimeDuplicateDelete"
                    )
                }
            }
        }
    }

    override fun addListener() {
        binding.toolbarDuplicate.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }

        binding.btnRemoveAllDuplicates.setOnClickListener {
            deleteAllDuplicates()
        }

        binding.noDuplicateImageLayout.btnOpen.setOnClickListener {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            if (intent.resolveActivity(this.packageManager) != null) {
                startActivity(intent)
                nextScreenAnimation()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.no_camera_app_found), Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    private fun deleteAllDuplicates() {
        val duplicateGroups = MyApplication.duplicateImageGroupsLiveData.value ?: return
        val mediaToDelete = mutableListOf<Uri>()

        for (group in duplicateGroups) {
            if (group.allUris.size > 1) {
                val itemsToDelete = group.allUris.drop(1)
                mediaToDelete.addAll(itemsToDelete)
            }
        }

        if (mediaToDelete.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_items_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_delete_all_photos, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val tvTitle = dialogView.findViewById<AppCompatTextView>(R.id.tvTitle)
        val tvDescription = dialogView.findViewById<AppCompatTextView>(R.id.tvCreateNewAlbum)
        val btnMoveToBeen = dialogView.findViewById<AppCompatButton>(R.id.btnYesDelete)
        val btnCancel = dialogView.findViewById<AppCompatButton>(R.id.btnCancel)

        tvTitle.text = getString(R.string.clear_all_duplicates)
        tvDescription.text =
            getString(R.string.are_you_sure_you_want_to_clear_all_duplicates_from_this_device)

        btnMoveToBeen.text = getString(R.string.yes_clear)
        btnMoveToBeen.setOnClickListener {
            showLoading()
            dialog.dismiss()
            CoroutineScope(Dispatchers.IO).launch {
                val database = PhotoGalleryDatabase.getDatabase(this@DuplicateImageActivity)

                mediaToDelete.forEach { uri ->
                    MyApplication.instance.notifyFileDeleted(uri)
                    val favorite = database.photoGalleryDao().getFavoriteByUri(uri.toString())
                    if (favorite != null) {
                        database.photoGalleryDao().deleteFavorite(favorite)
                    }

                    var id: Long = 0
                    var name = ""
                    var dateTaken: Long = 0
                    var originalPath: String? = null

                    val projection = arrayOf(
                        MediaStore.MediaColumns._ID,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        MediaStore.MediaColumns.DATE_TAKEN,
                        MediaStore.MediaColumns.DATA
                    )

                    contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            id =
                                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                            name =
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME))
                            dateTaken =
                                cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN))
                            originalPath =
                                cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA))
                        }
                    }

                    if (originalPath == null) return@forEach
                    val file = File(originalPath)
                    val newFile = moveToDeletedFolder(file)
                    if (newFile != null) {
                        try {
                            if (file.exists()) {
                                file.delete()
                            }

                            val mediaDataEntity = MediaDataEntity(
                                id = id,
                                name = name,
                                originalPath = newFile.path,
                                recyclePath = newFile.absolutePath,
                                uri = Uri.fromFile(newFile),
                                dateTaken = dateTaken,
                                isVideo = false,
                                duration = 0,
                                deletedAt = System.currentTimeMillis()
                            )
                            database.photoGalleryDao().insertDeletedMedia(mediaDataEntity)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    hideLoading()
                    MyApplication.isPhotoFetchReload = true
                    dialog.dismiss()
                    Toast.makeText(
                        this@DuplicateImageActivity,
                        getString(R.string.deleted_successfully),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}