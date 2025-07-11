package com.photogallery.activity

import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.adapter.AlbumAdapter
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityAddToAlbumBinding
import com.photogallery.model.Album
import com.photogallery.model.MediaData
import com.photogallery.utils.SharedPreferenceHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AddToAlbumActivity : BaseActivity<ActivityAddToAlbumBinding>() {
    private lateinit var albumRecyclerView: RecyclerView
    private lateinit var albumAdapter: AlbumAdapter
    private val albumList = mutableListOf<Album>()
    private var selectedMedia: List<MediaData> = emptyList()

    override fun getViewBinding(): ActivityAddToAlbumBinding {
        return ActivityAddToAlbumBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        selectedMedia = MyApplication.instance.selectedMedia
        binding.toolbar.tvToolbarTitle.text = getString(R.string.add_to_album)
        albumRecyclerView = findViewById(R.id.albumRecyclerView)
        albumAdapter = AlbumAdapter(albumList, this) { album ->
            if (album.isAddAlbum) {
                showCreateAlbumDialog()
            } else {
                handleAlbumSelection(album)
            }
        }
        albumRecyclerView.layoutManager = GridLayoutManager(this, 3)
        albumRecyclerView.adapter = albumAdapter

        binding.progressBar.visibility = View.VISIBLE
        loadAlbumsAsync()
    }

    override fun addListener() {
        binding.toolbar.ivBack.setOnClickListener {
            MyApplication.instance.clearSelectedMediaAndAction()
            onBackPressedDispatcher()
        }
    }

    override fun onBackPressedDispatcher() {
        MyApplication.instance.clearSelectedMediaAndAction()
        backScreenAnimation()
        finish()
    }

    private fun loadAlbumsAsync() {
        lifecycleScope.launch {
            val albums = withContext(Dispatchers.IO) {
                // Log to debug SharedPreferences content
                val savedAlbums =
                    ePreferences.getStringSet(SharedPreferenceHelper.ALBUMS_KEY)

                // Always fetch fresh data to ensure newly created albums are included
                val deviceAlbums = getAlbumsFromStorage(this@AddToAlbumActivity)
                val allAlbums = mutableListOf<Album>().apply {
                    add(Album(name = getString(R.string.add_album), photoUris = mutableListOf(), isAddAlbum = true))
                    addAll(savedAlbums.map { albumName ->
                        Album(
                            name = albumName,
                            photoUris = getImagesForAlbum(this@AddToAlbumActivity, albumName)
                        )
                    })
                    addAll(deviceAlbums.filter { !savedAlbums.contains(it.name) })
                }
                // Update cache
                MyApplication.cachedAlbums = allAlbums
                allAlbums
            }
            albumList.clear()
            albumList.addAll(albums)
            albumAdapter.notifyDataSetChanged()
            binding.progressBar.visibility = View.GONE
        }
    }
    private fun getAlbumsFromStorage(context: Context): List<Album> {
        val albumMap = LinkedHashMap<String, MutableList<Uri>>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val bucketName = cursor.getString(bucketColumn) ?: getString(R.string.unknown)
                val imageUri =
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

                if (!albumMap.containsKey(bucketName)) {
                    albumMap[bucketName] = mutableListOf()
                }
                albumMap[bucketName]!!.add(imageUri) // Removed the size check
            }
        }

        return albumMap.map { (albumName, uriList) ->
            Album(name = albumName, photoUris = uriList)
        }
    }

    private fun getImagesForAlbum(context: Context, albumName: String): MutableList<Uri> {
        val photoUris = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection = "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(albumName)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val imageUri =
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                photoUris.add(imageUri)
            }
        }
        return photoUris
    }

    private fun showCreateAlbumDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_create_new_album)
        dialog.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        val etAlbumName = dialog.findViewById<AppCompatEditText>(R.id.edtCreateNewAlbum)
        val btnCancel = dialog.findViewById<AppCompatButton>(R.id.btnCancel)
        val btnCreate = dialog.findViewById<AppCompatButton>(R.id.btnCreateNewAlbum)

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnCreate.setOnClickListener {
            val albumName = etAlbumName.text.toString().trim()
            if (albumName.isNotEmpty()) {
                binding.progressBar.visibility = View.VISIBLE
                lifecycleScope.launch {
                    if (createNewAlbum(albumName)) {
                        val currentAlbums =
                            ePreferences.getStringSet(SharedPreferenceHelper.ALBUMS_KEY)
                                .toMutableSet()
                        currentAlbums.add(albumName)
                        ePreferences.putStringSet(SharedPreferenceHelper.ALBUMS_KEY, currentAlbums)
                        dialog.dismiss()
                        loadAlbumsAsync()
                    }
                    binding.progressBar.visibility = View.GONE
                }
            } else {
                etAlbumName.error = getString(R.string.please_enter_album_name)
            }
        }
        dialog.show()
    }

    private suspend fun createNewAlbum(albumName: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val picturesDir =
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val albumDir = File(picturesDir, albumName)
                if (!albumDir.exists()) {
                    if (albumDir.mkdirs()) {
                        // Scan the new directory and wait for completion
                        var scanCompleted = false
                        MediaScannerConnection.scanFile(
                            this@AddToAlbumActivity,
                            arrayOf(albumDir.absolutePath),
                            null
                        ) { _, _ ->
                            scanCompleted = true
                        }
                        // Wait for scan to complete (up to 5 seconds)
                        val startTime = System.currentTimeMillis()
                        while (!scanCompleted && System.currentTimeMillis() - startTime < 5000) {
                            Thread.sleep(100)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AddToAlbumActivity,
                                getString(R.string.album_created),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        true
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AddToAlbumActivity,
                                getString(R.string.failed_to_create_album),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@AddToAlbumActivity,
                            getString(R.string.album_already_exists),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@AddToAlbumActivity,
                        getString(R.string.error_creating_album, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                false
            }
        }
    }

    private fun handleAlbumSelection(album: Album) {
        if (album.isAddAlbum) {
            showCreateAlbumDialog()
            return
        }

        if (selectedMedia.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_items_selected), Toast.LENGTH_SHORT).show()
            MyApplication.instance.clearSelectedMediaAndAction()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val albumDir = File(picturesDir, album.name)
            if (!albumDir.exists()) {
                albumDir.mkdirs()
            }

            val selectedIds = selectedMedia.map { it.id }.toSet()
            val updatedMediaList = MyApplication.mediaList.map { media ->
                if (media.id in selectedIds) {
                    try {
                        val originalFile = File(media.path)
                        val newFile = File(albumDir, originalFile.name)
                        Log.d("AddToAlbumActivity", "Moving ${originalFile.absolutePath} to ${newFile.absolutePath}")
                        if (originalFile.renameTo(newFile)) {
                            val contentUri = if (media.isVideo) {
                                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                            } else {
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            }
                            val values = ContentValues().apply {
                                put(MediaStore.MediaColumns.DATA, newFile.absolutePath)
                                put(MediaStore.MediaColumns.DISPLAY_NAME, newFile.name)
                                put(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME, album.name)
                            }
                            val rowsUpdated = contentResolver.update(
                                ContentUris.withAppendedId(contentUri, media.id),
                                values,
                                null,
                                null
                            )
                            if (rowsUpdated == 0) {
                                Log.e("AddToAlbumActivity", "Failed to update MediaStore for media ID ${media.id}")
                            }

                            MediaScannerConnection.scanFile(
                                this@AddToAlbumActivity,
                                arrayOf(newFile.absolutePath),
                                arrayOf(media.isVideo.let { if (it) "video/*" else "image/*" })
                            ) { path, uri ->
                                if (uri == null) {
                                    Log.e("AddToAlbumActivity", "Media scan failed for $path")
                                } else {
                                    Log.d("AddToAlbumActivity", "Media scan completed for $path, URI: $uri")
                                }
                            }

                            // Verify new file exists
                            if (!newFile.exists()) {
                                Log.e("AddToAlbumActivity", "New file does not exist: ${newFile.absolutePath}")
                            }

                            media.copy(path = newFile.absolutePath)
                        } else {
                            throw Exception(getString(R.string.failed_to_move_file_to, newFile.absolutePath))
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@AddToAlbumActivity,
                                getString(R.string.error_processing_media, e.message),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        media
                    }
                } else {
                    media
                }
            }
            MyApplication.mediaList = updatedMediaList
            withContext(Dispatchers.Main) {
                MyApplication.isPhotoFetchReload = true
                MyApplication.isVideoFetchReload = true
                Toast.makeText(
                    this@AddToAlbumActivity,
                    getString(R.string.moved_to_album, album.name),
                    Toast.LENGTH_SHORT
                ).show()
                binding.progressBar.visibility = View.GONE
                setResult(RESULT_OK)
                MyApplication.instance.clearSelectedMediaAndAction()
                // Notify system of file change
                updatedMediaList.forEach { media ->
                    if (media.id in selectedIds) {
                        sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(media.path))))
                    }
                }
                onBackPressedDispatcher()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        albumRecyclerView.adapter = null // Prevent memory leaks
        MyApplication.instance.clearSelectedMediaAndAction()
    }
}