package com.photogallery.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.adapter.FavoritesAdapter
import com.photogallery.databinding.ActivityFavoriteBinding
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.db.model.MediaFavoriteData
import com.photogallery.model.MediaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class FavoriteActivity : BaseActivity<ActivityFavoriteBinding>() {
    private lateinit var adapter: FavoritesAdapter

    override fun getViewBinding(): ActivityFavoriteBinding {
        return ActivityFavoriteBinding.inflate(layoutInflater)
    }

    override fun onResume() {
        super.onResume()
        loadFavoriteMedia()
    }

    override fun init(savedInstanceState: Bundle?) {
        setupToolbar()
        setupRecyclerView()

        binding.llFavoriteEmpty.tvTitle.text = getString(R.string.no_favourites_yet)
        binding.llFavoriteEmpty.tvDescription.text =
            getString(R.string.your_favourites_list_is_empty_start_saving_content_to_fill_this_space)
        binding.llFavoriteEmpty.ivIllustrator.setImageResource(R.drawable.ic_no_favorites)
        binding.llFavoriteEmpty.btnOpen.text = getString(R.string.add_to_favourites)
    }

    override fun addListener() {
        binding.llFavoriteEmpty.btnOpen.setOnClickListener {
            val intent = Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            startActivity(intent)
            nextScreenAnimation()
        }
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }

    private fun setupToolbar() {
        binding.toolbar.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }
        binding.toolbar.tvToolbarTitle.text = getString(R.string.favorites)
        binding.toolbar.ivMore.visibility = View.GONE
    }

    private fun setupRecyclerView() {
        adapter = FavoritesAdapter(
            this,
            ePreferences,
            onItemClick = { media ->
                // Filter based on media type
                MyApplication.mediaList = adapter.currentList
                    .filter { it.isVideo == media.isVideo }
                    .map { entity ->
                        MediaData(
                            id = entity.id,
                            name = entity.name,
                            path = entity.originalPath,
                            uri = entity.uri,
                            dateTaken = entity.dateTaken,
                            isVideo = entity.isVideo,
                            duration = entity.duration,
                            daysRemaining = 0,
                            isFavorite = entity.isFavorite
                        )
                    }

                // Get correct position in the filtered list
                val position = MyApplication.mediaList.indexOfFirst { it.uri == media.uri }

                if (position != -1) {
                    val intent = if (media.isVideo) {
                        Intent(this@FavoriteActivity, VideoPlayerActivity::class.java)
                    } else {
                        Intent(this@FavoriteActivity, PhotoViewActivity::class.java)
                    }
                    intent.putExtra("selected_position", position)
                    intent.putExtra("fromAlbum", true)
                    startActivity(intent)
                    nextScreenAnimation()
                }
            },
            onUnFavoriteClick = { media ->
                if (media.isFavorite == true) {
                    removeFromFavorites(media)
                }
            }
        )

        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(this@FavoriteActivity, 3)
            adapter = this@FavoriteActivity.adapter
        }
    }

    private fun loadFavoriteMedia() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = PhotoGalleryDatabase.getDatabase(this@FavoriteActivity)
            val favoriteMedia = database.photoGalleryDao().getAllFavorites()

            val validMedia = mutableListOf<MediaFavoriteData>()
            val mediaList = mutableListOf<MediaData>()

            for (entity in favoriteMedia) {
                val file = File(entity.originalPath)
                if (file.exists()) {
                    validMedia.add(entity)

                    mediaList.add(
                        MediaData(
                            id = entity.id,
                            name = entity.name,
                            path = entity.originalPath,
                            uri = entity.uri,
                            dateTaken = entity.dateTaken,
                            isVideo = entity.isVideo,
                            duration = 0L,
                            daysRemaining = 0,
                            isFavorite = entity.isFavorite
                        )
                    )
                } else {
                    database.photoGalleryDao().deleteFavorite(entity)
                }
            }

            withContext(Dispatchers.Main) {
                MyApplication.mediaList = mediaList
                adapter.submitList(validMedia)
                updateUI(validMedia)
            }
        }
    }


    private fun updateUI(favoriteMedia: List<MediaFavoriteData>) {
        if (favoriteMedia.isEmpty()) {
            binding.llFavoriteEmpty.llEmptyLayout.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
            binding.llMain.visibility = View.GONE
        } else {
            binding.llFavoriteEmpty.llEmptyLayout.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
            binding.llMain.visibility = View.VISIBLE
        }
    }

    private fun removeFromFavorites(media: MediaFavoriteData) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = PhotoGalleryDatabase.getDatabase(this@FavoriteActivity)
                database.photoGalleryDao().deleteFavorite(media)
                withContext(Dispatchers.Main) {
                    loadFavoriteMedia() // Refresh the list
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    showToast(getString(R.string.error_removing_favorite))
                }
            }
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this@FavoriteActivity, message, Toast.LENGTH_SHORT).show()
    }
}