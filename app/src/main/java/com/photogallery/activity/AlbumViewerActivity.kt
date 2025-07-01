package com.photogallery.activity

import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.adapter.ImageAdapter
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivityAlbumViewerBinding
import com.photogallery.model.MediaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AlbumViewerActivity : BaseActivity<ActivityAlbumViewerBinding>() {
    private var isWhat: String = ""
    override fun getViewBinding(): ActivityAlbumViewerBinding {
        return ActivityAlbumViewerBinding.inflate(layoutInflater)
    }

    override fun onResume() {
        super.onResume()
        isWhat = intent.getStringExtra("isWhat") ?: ""
        showLoadingDialog()
        populateMediaList()
    }

    override fun init(savedInstanceState: Bundle?) {
        val albumName = intent.getStringExtra("albumName") ?: "Album"
        binding.toolbar.tvToolbarTitle.text = albumName
    }

    override fun addListener() {
        binding.toolbar.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }
        binding.emptyAlbum.btnOpen.setOnClickListener {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            if (intent.resolveActivity(this.packageManager) != null) {
                startActivity(intent)
                nextScreenAnimation()
            } else {
                Toast.makeText(
                    this, getString(R.string.no_camera_app_found), Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    private fun showLoadingDialog() {
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerViewAlbum.visibility = View.GONE
        binding.emptyAlbum.llEmptyLayout.visibility = View.GONE
    }

    private fun hideLoadingBar() {
        binding.progressBar.visibility = View.GONE
    }

    private fun populateMediaList() {
        CoroutineScope(Dispatchers.IO).launch {
            val newMediaList = mutableListOf<MediaData>()
            MyApplication.selectedAlbumImages.forEach { uri ->
                contentResolver.query(
                    uri, arrayOf(
                        MediaStore.Images.Media._ID,
                        MediaStore.Images.Media.DISPLAY_NAME,
                        MediaStore.Images.Media.DATA,
                        MediaStore.Images.Media.DATE_TAKEN
                    ), null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id =
                            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val name =
                            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                        val path =
                            cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                        val dateTaken =
                            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN))

                        newMediaList.add(
                            MediaData(
                                id = id,
                                name = name,
                                path = path,
                                uri = uri,
                                dateTaken = dateTaken,
                                isVideo = false,
                                duration = 0L,
                                daysRemaining = 0,
                                isFavorite = false
                            )
                        )
                    }
                }
            }
            withContext(Dispatchers.Main) {
                MyApplication.mediaList = newMediaList
                hideLoadingBar()
                updateUI()
            }
        }
    }

    private fun updateUI() {
        val imageUris = MyApplication.selectedAlbumImages
        if (imageUris.isEmpty()) {
            binding.emptyAlbum.llEmptyLayout.visibility = View.VISIBLE
            binding.recyclerViewAlbum.visibility = View.GONE
        } else {
            binding.emptyAlbum.llEmptyLayout.visibility = View.GONE
            binding.recyclerViewAlbum.visibility = View.VISIBLE
            binding.recyclerViewAlbum.layoutManager = GridLayoutManager(this, 3)
            val adapter = ImageAdapter(imageUris) { uri ->
                val position = imageUris.indexOf(uri)
                if (position != -1) {
                    val intent = Intent(this, PhotoViewActivity::class.java).apply {
                        putExtra("selected_position", position)
                        putExtra("fromAlbum", true)
                        putExtra("FromSearch", true)
                        putExtra("isWhat", isWhat)
                    }
                    startActivity(intent)
                    nextScreenAnimation()
                }
            }
            binding.recyclerViewAlbum.adapter = adapter
        }
    }
}