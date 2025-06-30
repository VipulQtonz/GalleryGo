package com.photogallery.activity

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.photogallery.MyApplication
import com.photogallery.adapter.GenericGroupAdapter
import com.photogallery.databinding.ActivitySeeAllBinding
import com.photogallery.model.DocumentGroup
import com.photogallery.model.GroupedLocationPhoto
import com.photogallery.model.PeopleGroup

class SeeAllActivity : BaseActivity<ActivitySeeAllBinding>() {
    private val tempLocationList: MutableList<GroupedLocationPhoto> = mutableListOf()
    private val tempDocumentList: MutableList<DocumentGroup> = mutableListOf()
    private var albumName = "All Locations"
    private lateinit var genericGroupAdapter: GenericGroupAdapter

    private val locationObserver = Observer<List<GroupedLocationPhoto>> { photos ->
        tempLocationList.clear()
        tempLocationList.addAll(photos)
        if (albumName == "All Locations") {
            binding.progressBar.visibility = android.view.View.GONE
            genericGroupAdapter.submitList(tempLocationList.toList())
        }
    }

    private val documentObserver = Observer<List<DocumentGroup>> { documents ->
        tempDocumentList.clear()
        tempDocumentList.addAll(documents)
        if (albumName == "All Documents") {
            binding.progressBar.visibility = android.view.View.GONE
            genericGroupAdapter.submitList(tempDocumentList.toList())
        }
    }

    override fun getViewBinding(): ActivitySeeAllBinding {
        return ActivitySeeAllBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        albumName = intent.getStringExtra("albumName") ?: ""
        binding.toolbar.tvToolbarTitle.text = albumName
        binding.progressBar.visibility = android.view.View.VISIBLE

        setupAdapter()
        when (albumName) {
            "All Locations" -> {
                observeLocationData()
            }

            "All Documents" -> {
                observeDocumentData()
            }

            else -> {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun setupAdapter() {
        genericGroupAdapter = GenericGroupAdapter(
            context = this,
            onItemClick = { group ->
                MyApplication.selectedAlbumImages = when (group) {
                    is GroupedLocationPhoto -> group.allUris.toMutableList()
                    is PeopleGroup -> group.allUris.toMutableList()
                    is DocumentGroup -> group.allUris.toMutableList()
                    else -> mutableListOf()
                }
                val intent = when (group) {
                    is GroupedLocationPhoto -> Intent(
                        this,
                        LocationPhotoViewerActivity::class.java
                    ).apply {
                        putExtra("albumName", group.locationName ?: "Unknown Location")
                        putExtra("latitude", group.latitude ?: 0.0)
                        putExtra("longitude", group.longitude ?: 0.0)
                        putExtra("photoCount", group.allUris.size)
                        putExtra("isWhat", "Location")
                    }

                    else -> Intent(this, AlbumViewerActivity::class.java).apply {
                        putExtra(
                            "albumName", when (group) {
                                is PeopleGroup -> "Face Group ${group.groupId}"
                                is DocumentGroup -> group.name
                                else -> albumName
                            }
                        )
                        putExtra("isWhat", "Document")
                        putExtra("FromSearch", true)
                    }
                }
                startActivity(intent)
                nextScreenAnimation()
            }
        )

        binding.rwSeeAll.layoutManager = GridLayoutManager(this, 3)
        binding.rwSeeAll.adapter = genericGroupAdapter
        binding.rwSeeAll.setHasFixedSize(true)
    }

    private fun observeLocationData() {
        MyApplication.groupedLocationPhotosLiveData.observe(this, locationObserver)
        // Get current value if available
        MyApplication.groupedLocationPhotosLiveData.value?.let {
            locationObserver.onChanged(it)
        }
    }

    private fun observeDocumentData() {
        MyApplication.documentGroupsLiveData.observe(this, documentObserver)
        // Get current value if available
        MyApplication.documentGroupsLiveData.value?.let {
            documentObserver.onChanged(it)
        }
    }

    override fun addListener() {
        binding.toolbar.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MyApplication.groupedLocationPhotosLiveData.removeObserver(locationObserver)
        MyApplication.documentGroupsLiveData.removeObserver(documentObserver)
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }
}