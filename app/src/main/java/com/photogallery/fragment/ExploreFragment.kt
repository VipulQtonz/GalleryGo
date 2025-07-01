package com.photogallery.fragment

import android.app.Activity
import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.processDuplicates
import com.photogallery.MyApplication.Companion.processLocationPhotos
import com.photogallery.MyApplication.Companion.processMoments
import com.photogallery.MyApplication.Companion.processPhotoClassification
import com.photogallery.R
import com.photogallery.activity.AlbumViewerActivity
import com.photogallery.activity.DuplicateImageActivity
import com.photogallery.activity.EnterPinActivity
import com.photogallery.activity.FavoriteActivity
import com.photogallery.activity.HomeActivity
import com.photogallery.activity.RecyclerBinActivity

import com.photogallery.activity.SetPinActivity
import com.photogallery.adapter.AlbumAdapter
import com.photogallery.base.BaseFragment
import com.photogallery.databinding.FragmentExploreBinding
import com.photogallery.model.Album
import com.photogallery.model.MediaData
import com.photogallery.utils.SharedPreferenceHelper
import com.photogallery.utils.isInternet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExploreFragment : BaseFragment<FragmentExploreBinding>() {
    private lateinit var albumRecyclerView: RecyclerView
    private lateinit var albumAdapter: AlbumAdapter
    private val albumList = mutableListOf<Album>()
    private var selectedMedia: List<MediaData> = emptyList()

    companion object {
        private var homeActivity: HomeActivity? = null
        fun newInstance(homeActivity: HomeActivity) =
            ExploreFragment().apply {
                Companion.homeActivity = homeActivity
                arguments = Bundle().apply {}
            }
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            binding.progressBar.visibility = View.VISIBLE
            loadAlbumsAsync()
            processLocationPhotos(requireContext())
            if (requireContext().isInternet()) {
                processPhotoClassification(requireContext())
                processMoments(requireContext())
                processDuplicates(requireContext())
            }
        } else {
            showPermissionDialog()
        }
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentExploreBinding {
        return FragmentExploreBinding.inflate(inflater, container, false)
    }

    override fun init() {
        selectedMedia = MyApplication.instance.selectedMedia
        albumRecyclerView = binding.albumRecyclerView
        albumAdapter = AlbumAdapter(albumList, requireActivity()) { album ->
            if (album.isAddAlbum) {
                showCreateAlbumDialog()
            } else {
                MyApplication.selectedAlbumImages = album.photoUris
                val intent = Intent(requireActivity(), AlbumViewerActivity::class.java)
                intent.putExtra("albumName", album.name)
                startActivity(intent)
                (requireContext() as Activity).nextScreenAnimation()
            }
        }
        albumRecyclerView.layoutManager = GridLayoutManager(requireActivity(), 3)
        albumRecyclerView.adapter = albumAdapter
    }

    override fun addListener() {
        binding.llFavorite.setOnClickListener {
            startActivity(Intent(requireActivity(), FavoriteActivity::class.java))
            (requireContext() as Activity).nextScreenAnimation()
        }
        binding.llLocked.setOnClickListener {
            if (ePreferences?.getBoolean("PinSeated", false) != true) {
                SetPinActivity.startForCompleteSetup(requireActivity())
            } else {
                startActivity(Intent(requireActivity(), EnterPinActivity::class.java))
                (requireContext() as Activity).nextScreenAnimation()
            }
        }
        binding.llDuplicate.setOnClickListener {
            startActivity(Intent(requireActivity(), DuplicateImageActivity::class.java))
            (requireContext() as Activity).nextScreenAnimation()
        }
        binding.llRecyclerBin.setOnClickListener {
            startActivity(Intent(requireActivity(), RecyclerBinActivity::class.java))
            (requireContext() as Activity).nextScreenAnimation()
        }
    }

    private fun loadAlbumsAsync() {
        viewLifecycleOwner.lifecycleScope.launch {
            val albums = withContext(Dispatchers.IO) {
                val savedAlbums =
                    ePreferences?.getStringSet(SharedPreferenceHelper.ALBUMS_KEY) ?: emptySet()
                val deviceAlbums = getAlbumsFromStorage(requireContext())
                val allAlbums = mutableListOf<Album>().apply {
                    add(Album(name = getString(R.string.add_album), photoUris = mutableListOf(), isAddAlbum = true))
                    addAll(savedAlbums.map { albumName ->
                        Album(
                            name = albumName,
                            photoUris = getImagesForAlbum(requireContext(), albumName)
                        )
                    })
                    addAll(deviceAlbums.filter { !savedAlbums.contains(it.name) })
                }
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
        val dialog = Dialog(requireActivity())
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
                viewLifecycleOwner.lifecycleScope.launch {
                    if (createNewAlbum(albumName)) {
                        val currentAlbums =
                            ePreferences?.getStringSet(SharedPreferenceHelper.ALBUMS_KEY)
                                ?.toMutableSet() ?: mutableSetOf()
                        currentAlbums.add(albumName)
                        ePreferences?.putStringSet(SharedPreferenceHelper.ALBUMS_KEY, currentAlbums)
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
                        var scanCompleted = false
                        MediaScannerConnection.scanFile(
                            requireContext(),
                            arrayOf(albumDir.absolutePath),
                            null
                        ) { _, _ ->
                            scanCompleted = true
                        }
                        val startTime = System.currentTimeMillis()
                        while (!scanCompleted && System.currentTimeMillis() - startTime < 5000) {
                            Thread.sleep(100)
                        }
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireActivity(),
                                getString(R.string.album_created),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        true
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                requireActivity(),
                                getString(R.string.failed_to_create_album),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireActivity(),
                            getString(R.string.album_already_exists),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        requireActivity(),
                        getString(R.string.error_creating_album, e.message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        albumRecyclerView.adapter = null // Prevent memory leaks
    }
}