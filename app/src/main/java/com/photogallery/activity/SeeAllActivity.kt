package com.photogallery.activity

import android.content.Intent
import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.adapter.GenericGroupAdapter
import com.photogallery.base.BaseActivity
import com.photogallery.databinding.ActivitySeeAllBinding
import com.photogallery.model.DocumentGroup
import com.photogallery.model.GroupedLocationPhoto
import com.photogallery.process.FaceGroupingUtils

class SeeAllActivity : BaseActivity<ActivitySeeAllBinding>() {
    private val tempLocationList: MutableList<GroupedLocationPhoto> = mutableListOf()
    private val tempDocumentList: MutableList<DocumentGroup> = mutableListOf()
    private val tempFaceGroupList: MutableList<FaceGroupingUtils.FaceGroup> = mutableListOf()
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

    private val faceGroupObserver = Observer<List<FaceGroupingUtils.FaceGroup>> { faceGroups ->
        tempFaceGroupList.clear()
        tempFaceGroupList.addAll(faceGroups)
        if (albumName == "People") {
            binding.progressBar.visibility = android.view.View.GONE
            genericGroupAdapter.submitList(tempFaceGroupList.toList())
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

            "People" -> {
                observeFaceGroupData()
            }

            else -> {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }

    private fun setupAdapter() {
        genericGroupAdapter = GenericGroupAdapter(
            context = this, onItemClick = { group ->
                MyApplication.selectedAlbumImages = when (group) {
                    is GroupedLocationPhoto -> group.allUris.toMutableList()
                    is DocumentGroup -> group.allUris.toMutableList()
                    is FaceGroupingUtils.FaceGroup -> group.uris.map { it.toUri() }.toMutableList()

                    else -> mutableListOf()
                }
                val intent = when (group) {
                    is GroupedLocationPhoto -> Intent(
                        this, LocationPhotoViewerActivity::class.java
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
                                is FaceGroupingUtils.FaceGroup -> getString(R.string.people)
                                is DocumentGroup -> group.name
                                else -> albumName
                            }
                        )
                        putExtra(
                            "isWhat",
                            if (group is FaceGroupingUtils.FaceGroup) "FaceGroup" else "Document"
                        )
                        putExtra("FromSearch", true)
                        putExtra(
                            "representativeImage",
                            if (group is FaceGroupingUtils.FaceGroup) group.representativeUri else null
                        )
                    }
                }
                startActivity(intent)
                nextScreenAnimation()
            })

        binding.rwSeeAll.layoutManager = GridLayoutManager(this, 3)
        binding.rwSeeAll.adapter = genericGroupAdapter
        binding.rwSeeAll.setHasFixedSize(true)
    }

    private fun observeLocationData() {
        MyApplication.groupedLocationPhotosLiveData.observe(this, locationObserver)
        MyApplication.groupedLocationPhotosLiveData.value?.let {
            locationObserver.onChanged(it)
        }
    }

    private fun observeDocumentData() {
        MyApplication.documentGroupsLiveData.observe(this, documentObserver)
        MyApplication.documentGroupsLiveData.value?.let {
            documentObserver.onChanged(it)
        }
    }

    private fun observeFaceGroupData() {
        MyApplication.faceGroups.observe(this, faceGroupObserver)
        MyApplication.faceGroups.value?.let {
            faceGroupObserver.onChanged(it)
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
        MyApplication.faceGroups.removeObserver(faceGroupObserver)
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
    }
}