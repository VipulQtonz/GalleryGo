package com.photogallery.activity

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.photogallery.MyApplication
import com.photogallery.R
import com.photogallery.adapter.ImageAdapter
import com.photogallery.adapter.SearchSuggestionAdapter
import com.photogallery.databinding.ActivitySearchBinding
import com.photogallery.model.DocumentGroup
import com.photogallery.model.GroupedLocationPhoto
import com.photogallery.model.MediaData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class SearchActivity : BaseActivity<ActivitySearchBinding>() {
    private lateinit var locationAdapter: SearchSuggestionAdapter
    private lateinit var documentAdapter: SearchSuggestionAdapter
    private lateinit var imageAdapter: ImageAdapter
    private val tempLocationNames: MutableList<String> = mutableListOf()
    private val tempDocumentNames: MutableList<String> = mutableListOf()
    private val uriList: MutableList<Uri> = Collections.synchronizedList(mutableListOf<Uri>())
    private val selectedGroup = MutableLiveData<String?>(null)

    private val locationObserver = Observer<List<GroupedLocationPhoto>> { photos ->
        tempLocationNames.clear()
        tempLocationNames.addAll(photos.mapNotNull { it.locationName })
        locationAdapter.updateItems(tempLocationNames)
    }

    private val documentObserver = Observer<List<DocumentGroup>> { documents ->
        tempDocumentNames.clear()
        tempDocumentNames.addAll(documents.map { it.name })
        documentAdapter.updateItems(tempDocumentNames)
    }

    private val locationGroupObserver = Observer<GroupedLocationPhoto?> { group ->
        synchronized(uriList) {
            uriList.clear()
            if (group != null && selectedGroup.value == group.locationName) {
                uriList.addAll(group.allUris)
                imageAdapter.updateUris(group.allUris.toList()) // Snapshot for adapter
                binding.nswSuggestionLayout.visibility = View.GONE
                binding.rlImageRecyclerView.visibility = View.VISIBLE
                populateMediaList()
            } else if (selectedGroup.value == null) {
                imageAdapter.updateUris(emptyList())
                binding.nswSuggestionLayout.visibility = View.VISIBLE
                binding.rlImageRecyclerView.visibility = View.GONE
            }
        }
    }

    private val documentGroupObserver = Observer<DocumentGroup?> { group ->
        synchronized(uriList) {
            uriList.clear()
            if (group != null && selectedGroup.value == group.name) {
                uriList.addAll(group.allUris)
                imageAdapter.updateUris(group.allUris.toList())
                binding.nswSuggestionLayout.visibility = View.GONE
                binding.rlImageRecyclerView.visibility = View.VISIBLE
                populateMediaList()
            } else if (selectedGroup.value == null) {
                imageAdapter.updateUris(emptyList())
                binding.nswSuggestionLayout.visibility = View.VISIBLE
                binding.rlImageRecyclerView.visibility = View.GONE
            }
        }
    }

    private var currentLocationLiveData: LiveData<GroupedLocationPhoto>? = null
    private var currentDocumentLiveData: LiveData<DocumentGroup>? = null

    override fun getViewBinding(): ActivitySearchBinding {
        return ActivitySearchBinding.inflate(layoutInflater)
    }

    override fun init(savedInstanceState: Bundle?) {
        imageAdapter = ImageAdapter(emptyList()) { uri ->
            synchronized(uriList) {
                val position = uriList.indexOf(uri)
                if (position != -1) {
                    val intent = Intent(this, PhotoViewActivity::class.java).apply {
                        putExtra("selected_position", position)
                        putExtra("fromAlbum", true)
                        putExtra("FromSearch", true)
                        putExtra("isWhat", "Document")
                    }
                    startActivity(intent)
                    nextScreenAnimation()
                }
            }
        }

        locationAdapter = SearchSuggestionAdapter(emptyList()) { locationName ->
            binding.searchBar.setText(locationName)
            selectedGroup.value = locationName
            currentLocationLiveData?.removeObserver(locationGroupObserver)
            currentLocationLiveData =
                MyApplication.getLocationPhotosByName(locationName) as LiveData<GroupedLocationPhoto>?
            currentLocationLiveData?.observe(this, locationGroupObserver)
        }

        documentAdapter = SearchSuggestionAdapter(emptyList()) { documentName ->
            binding.searchBar.setText(documentName)
            selectedGroup.value = documentName
            currentDocumentLiveData?.removeObserver(documentGroupObserver)
            currentDocumentLiveData =
                MyApplication.getDocumentGroupByName(documentName) as LiveData<DocumentGroup>?
            currentDocumentLiveData?.observe(this, documentGroupObserver)
        }

        binding.recyclerLocations.apply {
            val layoutManagerNew = FlexboxLayoutManager(this@SearchActivity)
            layoutManagerNew.flexDirection = FlexDirection.ROW
            layoutManagerNew.justifyContent = JustifyContent.FLEX_START
            layoutManagerNew.flexWrap = FlexWrap.WRAP
            layoutManager = layoutManagerNew
            adapter = locationAdapter
            setHasFixedSize(true)
        }

        binding.recyclerDocuments.apply {
            val layoutManagerNew = FlexboxLayoutManager(this@SearchActivity)
            layoutManagerNew.flexDirection = FlexDirection.ROW
            layoutManagerNew.justifyContent = JustifyContent.FLEX_START
            layoutManagerNew.flexWrap = FlexWrap.WRAP
            layoutManager = layoutManagerNew
            adapter = documentAdapter
            setHasFixedSize(true)
        }

        binding.rlImageRecyclerView.apply {
            layoutManager = GridLayoutManager(this@SearchActivity, 3)
            adapter = imageAdapter
            setHasFixedSize(true)
        }

        binding.searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                filterSearchResults(query)
                if (binding.rlImageRecyclerView.isVisible) {
                    selectedGroup.value = null
                    currentLocationLiveData?.removeObserver(locationGroupObserver)
                    currentDocumentLiveData?.removeObserver(documentGroupObserver)
                    currentLocationLiveData = null
                    currentDocumentLiveData = null
                    synchronized(uriList) {
                        uriList.clear()
                        imageAdapter.updateUris(emptyList())
                    }
                    binding.rlImageRecyclerView.visibility = View.GONE
                    binding.nswSuggestionLayout.visibility = View.VISIBLE
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        observeLocationData()
        observeDocumentData()

        binding.noSearchResultFound.btnOpen.setOnClickListener {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            if (intent.resolveActivity(this.packageManager) != null) {
                startActivity(intent)
                (this as Activity).nextScreenAnimation()
            } else {
                Toast.makeText(
                    this, getString(R.string.no_camera_app_found), Toast.LENGTH_SHORT
                ).show()
            }
        }
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

    override fun addListener() {
        binding.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }
    }

    override fun onBackPressedDispatcher() {
        if (binding.rlImageRecyclerView.isVisible) {
            selectedGroup.value = null
            currentLocationLiveData?.removeObserver(locationGroupObserver)
            currentDocumentLiveData?.removeObserver(documentGroupObserver)
            currentLocationLiveData = null
            currentDocumentLiveData = null
            synchronized(uriList) {
                uriList.clear()
                imageAdapter.updateUris(emptyList())
            }
            binding.rlImageRecyclerView.visibility = View.GONE
            binding.nswSuggestionLayout.visibility = View.VISIBLE
        } else {
            backScreenAnimation()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        MyApplication.groupedLocationPhotosLiveData.removeObserver(locationObserver)
        MyApplication.documentGroupsLiveData.removeObserver(documentObserver)
        currentLocationLiveData?.removeObserver(locationGroupObserver)
        currentDocumentLiveData?.removeObserver(documentGroupObserver)
    }

    private fun populateMediaList() {
        CoroutineScope(Dispatchers.IO).launch {
            val newMediaList = mutableListOf<MediaData>()
            // Create a snapshot of uriList to avoid ConcurrentModificationException
            val uriSnapshot = synchronized(uriList) { uriList.toList() }
            uriSnapshot.forEach { uri ->
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
                        val path = uri.toString() // Use uri.toString() instead of deprecated DATA
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
            }
        }
    }

    private fun filterSearchResults(query: String) {
        val filteredLocations = if (query.isEmpty()) {
            tempLocationNames
        } else {
            tempLocationNames.filter { it.contains(query, ignoreCase = true) }
        }

        val filteredDocuments = if (query.isEmpty()) {
            tempDocumentNames
        } else {
            tempDocumentNames.filter { it.contains(query, ignoreCase = true) }
        }

        locationAdapter.updateItems(filteredLocations)
        documentAdapter.updateItems(filteredDocuments)

        binding.noSearchResultFound.llEmptyLayout.visibility =
            if (filteredLocations.isEmpty() && filteredDocuments.isEmpty()) View.VISIBLE else View.GONE

        binding.recyclerLocations.visibility =
            if (filteredLocations.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvLocation.visibility =
            if (filteredLocations.isNotEmpty()) View.VISIBLE else View.GONE

        binding.recyclerDocuments.visibility =
            if (filteredDocuments.isNotEmpty()) View.VISIBLE else View.GONE
        binding.tvDocument.visibility =
            if (filteredDocuments.isNotEmpty()) View.VISIBLE else View.GONE
    }
}
