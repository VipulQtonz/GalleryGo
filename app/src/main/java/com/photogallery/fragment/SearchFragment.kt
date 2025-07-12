package com.photogallery.fragment

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat.startActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.LinearLayoutManager
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.processDuplicates
import com.photogallery.MyApplication.Companion.processFaceEmbeddings
import com.photogallery.MyApplication.Companion.processLocationPhotos
import com.photogallery.MyApplication.Companion.processMoments
import com.photogallery.MyApplication.Companion.processPhotoClassification
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.activity.AlbumViewerActivity
import com.photogallery.activity.HomeActivity
import com.photogallery.activity.LocationPhotoViewerActivity
import com.photogallery.activity.SearchActivity
import com.photogallery.activity.SeeAllActivity
import com.photogallery.adapter.DocumentAdapter
import com.photogallery.adapter.FaceGroupAdapter
import com.photogallery.adapter.LocationPhotosAdapter
import com.photogallery.base.BaseFragment
import com.photogallery.databinding.FragmentSearchBinding
import com.photogallery.utils.ConnectivityObserver
import com.photogallery.utils.isInternet
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.Job
import androidx.core.net.toUri

class SearchFragment : BaseFragment<FragmentSearchBinding>() {
    private lateinit var locationPhotosAdapter: LocationPhotosAdapter
    private lateinit var documentAdapter: DocumentAdapter
    private lateinit var faceGroupAdapter: FaceGroupAdapter
    private lateinit var connectivityObserver: ConnectivityObserver
    private val allowedDocumentCategories = listOf(
        "Documents", "Screenshot", "Selfie", "BlackAndWhite", "Animals", "Beard", "Other",
        "Portrait", "Panorama", "LongScreenshot", "WhatsApp", "Instagram", "Facebook",
        "Downloads", "Receipts", "IDCard", "QRCode", "Notes", "Whiteboard", "Scanned",
        "Text", "Collage", "Edited", "Sticker", "Messenger", "Twitter", "Telegram",
        "WeChat", "Snapchat", "Beauty", "AIAlbum", "Favorites"
    )

    private var classificationJob: Job? = null

    companion object {
        private var homeActivity: HomeActivity? = null

        fun newInstance(homeActivity: HomeActivity) = SearchFragment().apply {
            Companion.homeActivity = homeActivity
            arguments = Bundle()
        }
    }

    override fun getViewBinding(
        inflater: LayoutInflater, container: ViewGroup?
    ): FragmentSearchBinding {
        return FragmentSearchBinding.inflate(inflater, container, false)
    }

    override fun init() {
        binding.noInternetLayout.tvTitle.text = getString(R.string.no_internet_connection)
        binding.noInternetLayout.tvDescription.text =
            getString(R.string.please_make_sure_you_have_a_stable_internet_connection)
        binding.noInternetLayout.ivIllustrator.setImageResource(R.drawable.ic_no_internet)
        binding.noInternetLayout.btnOpen.text = getString(R.string.try_again)

        connectivityObserver = ConnectivityObserver(requireContext())
        setupAdapters()
        setupRecyclerViews()
        setupObservers()
        if (hasStoragePermission()) {
            binding.searchBar.visibility = View.VISIBLE
            processLocationPhotos(requireContext())
            processFaceEmbeddings(requireContext())
            if (requireContext().isInternet()) {
                processPhotoClassification(requireContext())
                processMoments(requireContext())
                processDuplicates(requireContext())
            }
            if (ePreferences!!.getBoolean(
                    "isFirstTimeSearchFragmentSearch",
                    true
                ) && binding.searchBar.isVisible
            ) {
                setupTooltip(
                    requireContext(),
                    binding.searchBar,
                    getString(R.string.click_to_open_search_screen),
                    ArrowOrientation.BOTTOM,
                    ePreferences!!,
                    "isFirstTimeSearchFragmentSearch"
                )
            }
        }
    }

    private fun setupAdapters() {
        locationPhotosAdapter = LocationPhotosAdapter(
            MyApplication.groupedLocationPhotosLiveData.value ?: emptyList(), requireContext()
        ) { groupedPhoto ->
            MyApplication.selectedAlbumImages = groupedPhoto.allUris
            val intent = Intent(requireActivity(), LocationPhotoViewerActivity::class.java).apply {
                putExtra(
                    "albumName",
                    groupedPhoto.locationName ?: getString(R.string.unknown_location)
                )
                putExtra("latitude", groupedPhoto.latitude ?: 0.0)
                putExtra("longitude", groupedPhoto.longitude ?: 0.0)
                putExtra("photoCount", groupedPhoto.allUris.size)
                putExtra("isWhat", "Location")
            }
            startActivity(requireContext(), intent, null)
            (requireContext() as Activity).nextScreenAnimation()
        }

        documentAdapter = DocumentAdapter(
            MyApplication.documentGroupsLiveData.value ?: emptyList(), requireContext()
        ) { group ->
            MyApplication.selectedAlbumImages = group.allUris
            val intent = Intent(requireActivity(), AlbumViewerActivity::class.java).apply {
                putExtra("albumName", group.name)
                putExtra("isWhat", "Document")
                putExtra("FromSearch", true)
            }
            startActivity(requireContext(), intent, null)
            (requireContext() as Activity).nextScreenAnimation()
        }

        faceGroupAdapter = FaceGroupAdapter(
            MyApplication.faceGroups.value ?: emptyList(), requireContext()
        ) { group ->
            MyApplication.selectedAlbumImages = group.uris.map { it.toUri() }
            val intent = Intent(requireActivity(), AlbumViewerActivity::class.java).apply {
                putExtra("albumName", "Face Group ${group.groupId.takeLast(8)}")
                putExtra("isWhat", "FaceGroup")
                putExtra("FromSearch", true)
                putExtra("representativeImage", group.representativeUri)
            }
            startActivity(requireContext(), intent, null)
            (requireContext() as Activity).nextScreenAnimation()
        }
    }

    private fun setupRecyclerViews() {
        binding.recyclerLocations.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerLocations.adapter = locationPhotosAdapter

        binding.recyclerDocuments.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerDocuments.adapter = documentAdapter

        binding.recyclerPeople.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.recyclerPeople.adapter = faceGroupAdapter
    }

    private fun setupObservers() {
        connectivityObserver.observe(viewLifecycleOwner, Observer { isConnected ->
            if (isConnected) {
                binding.noInternetLayout.llEmptyLayout.visibility = View.GONE
                binding.rlSearchLayout.visibility = View.VISIBLE
                if (hasStoragePermission()) {
                    processPhotoClassification(requireContext())
                    processMoments(requireContext())
                    processDuplicates(requireContext())
                    processFaceEmbeddings(requireContext())
                }
            } else {
                binding.noInternetLayout.llEmptyLayout.visibility = View.VISIBLE
                binding.rlSearchLayout.visibility = View.GONE
            }
        })

        MyApplication.groupedLocationPhotosLiveData.observe(viewLifecycleOwner, Observer { photos ->
            binding.rlLocation.visibility = if (photos.isNotEmpty()) View.VISIBLE else View.GONE
            binding.progressBar.visibility = if (photos.isNotEmpty()) View.GONE else View.VISIBLE
            locationPhotosAdapter.updateData(photos)
            locationPhotosAdapter.notifyDataSetChanged()

            if (ePreferences!!.getBoolean(
                    "isFirstTimeSearchFragmnetLocation",
                    true
                ) && binding.tvSeeAllLocation.isVisible
                && photos.isNotEmpty()
            ) {
                Handler(Looper.getMainLooper()).postDelayed({
                    setupTooltip(
                        requireContext(),
                        binding.tvSeeAllLocation,
                        getString(R.string.click_to_see_all_locations),
                        ArrowOrientation.BOTTOM,
                        ePreferences!!,
                        "isFirstTimeSearchFragmnetLocation"
                    )
                }, 2000)
            }
        })

        MyApplication.documentGroupsLiveData.observe(viewLifecycleOwner, Observer { documents ->
            val filteredDocs = documents.filter { doc ->
                doc.name in allowedDocumentCategories && doc.allUris.isNotEmpty()
            }
            binding.rlDocuments.visibility =
                if (filteredDocs.isNotEmpty()) View.VISIBLE else View.GONE
            binding.progressBar.visibility =
                if (filteredDocs.isNotEmpty()) View.GONE else View.VISIBLE
            documentAdapter.updateData(filteredDocs)
            documentAdapter.notifyDataSetChanged()

            if (ePreferences!!.getBoolean(
                    "isFirstTimeSearchFragmnetDocument",
                    true
                ) && binding.tvSeeAllDocuments.isVisible
                && documents.isNotEmpty()
            ) {
                Handler(Looper.getMainLooper()).postDelayed({
                    setupTooltip(
                        requireContext(),
                        binding.tvSeeAllDocuments,
                        getString(R.string.click_to_see_all_documents),
                        ArrowOrientation.BOTTOM,
                        ePreferences!!,
                        "isFirstTimeSearchFragmnetDocument"
                    )
                }, 2000)
            }
        })

        MyApplication.faceGroups.observe(viewLifecycleOwner, Observer { faceGroups ->
            if (faceGroups.isEmpty()) {
                Log.w(
                    "TAGG",
                    "Face groups list is empty. Check FaceEmbeddingUtils or database."
                )
                binding.rlPeople.visibility = View.GONE
            } else {
                binding.rlPeople.visibility = View.VISIBLE
                faceGroupAdapter.updateData(faceGroups)
                faceGroupAdapter.notifyDataSetChanged()
            }

            if (ePreferences!!.getBoolean(
                    "isFirstTimeSearchFragmnetPeople",
                    true
                ) && binding.tvSeeAllDocuments.isVisible
                && faceGroups.isNotEmpty()
            ) {
                Handler(Looper.getMainLooper()).postDelayed({
                    setupTooltip(
                        requireContext(),
                        binding.tvSeeAllPeople,
                        getString(R.string.click_to_see_all_people),
                        ArrowOrientation.BOTTOM,
                        ePreferences!!,
                        "isFirstTimeSearchFragmnetPeople"
                    )
                }, 2000)
            }
        })
    }

    override fun addListener() {
        binding.tvSeeAllLocation.setOnClickListener {
            val allLocationPhotos =
                MyApplication.groupedLocationPhotosLiveData.value?.flatMap { it.allUris }
                    ?.distinct() ?: emptyList()
            MyApplication.selectedAlbumImages = allLocationPhotos.toMutableList()
            val intent = Intent(requireActivity(), SeeAllActivity::class.java).apply {
                putExtra("albumName", "All Locations")
                putExtra("isWhat", "Location")
                putExtra("FromSearch", true)
            }
            startActivity(requireContext(), intent, null)
            (requireContext() as Activity).nextScreenAnimation()
        }

        binding.tvSeeAllDocuments.setOnClickListener {
            val allDocumentPhotos =
                MyApplication.documentGroupsLiveData.value?.flatMap { it.allUris }?.distinct()
                    ?: emptyList()
            MyApplication.selectedAlbumImages = allDocumentPhotos.toMutableList()
            val intent = Intent(requireActivity(), SeeAllActivity::class.java).apply {
                putExtra("albumName", "All Documents")
                putExtra("isWhat", "Document")
                putExtra("FromSearch", true)
            }
            startActivity(requireContext(), intent, null)
            (requireContext() as Activity).nextScreenAnimation()
        }

        binding.tvSeeAllPeople.setOnClickListener {
            val allFaceGroupPhotos =
                MyApplication.faceGroups.value?.flatMap {
                    it.uris.map { uri ->
                        Uri.parse(
                            uri
                        )
                    }
                }?.distinct() ?: emptyList()
            MyApplication.selectedAlbumImages = allFaceGroupPhotos.toMutableList()
            val intent = Intent(requireActivity(), SeeAllActivity::class.java).apply {
                putExtra("albumName", "People")
                putExtra("isWhat", "FaceGroup")
                putExtra("FromSearch", true)
            }
            startActivity(requireContext(), intent, null)
            (requireContext() as Activity).nextScreenAnimation()
        }

        binding.searchBar.setOnClickListener {
            startActivity(
                requireContext(),
                Intent(requireActivity(), SearchActivity::class.java),
                null
            )
            (requireContext() as Activity).nextScreenAnimation()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasStoragePermission()) {
            locationPhotosAdapter.notifyDataSetChanged()
            documentAdapter.notifyDataSetChanged()
            faceGroupAdapter.notifyDataSetChanged()
        } else {
            showPermissionDialog()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        classificationJob?.cancel()
    }
}