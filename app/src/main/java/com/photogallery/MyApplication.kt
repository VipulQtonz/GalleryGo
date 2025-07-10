package com.photogallery

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.multidex.MultiDexApplication
import com.google.firebase.FirebaseApp
import com.photogallery.db.PhotoGalleryDatabase
import com.photogallery.model.Album
import com.photogallery.model.DocumentGroup
import com.photogallery.model.DuplicateImageGroup
import com.photogallery.model.GroupedLocationPhoto
import com.photogallery.model.MediaData
import com.photogallery.model.Moment
import com.photogallery.model.MomentGroup
import com.photogallery.process.ClassificationUtils
import com.photogallery.process.FaceEmbeddingUtils
import com.photogallery.process.FaceGroupingUtils
import com.photogallery.process.LocationUtils
import com.photogallery.process.MomentsUtils
import com.photogallery.utils.SharedPreferenceHelper
import com.photogallery.utils.SharedPreferenceHelper.Companion.PREF_THEME
import com.photogallery.utils.SharedPreferenceHelper.Companion.THEME_DARK
import com.photogallery.utils.SharedPreferenceHelper.Companion.THEME_LIGHT
import com.photogallery.utils.SharedPreferenceHelper.Companion.THEME_SYSTEM_DEFAULT
import com.photogallery.utils.UriUtils
import com.skydoves.balloon.ArrowOrientation
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonSizeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

class MyApplication : MultiDexApplication() {
    lateinit var pref: SharedPreferenceHelper
    var checkParm = false

    var selectedMedia: List<MediaData> = emptyList()
        private set

    var action: String? = null
        private set

    companion object {
        lateinit var instance: MyApplication
        var mediaList: List<MediaData> = mutableListOf()
        var selectedAlbumImages: List<Uri> = emptyList()
        var cachedAlbums: List<Album> = emptyList()
        private var hasProcessedLocationPhotos = false
        private var hasProcessedClassification = false
        private var hasProcessedDuplicates = false
        private var hasProcessedMoments = false
        private var hasProcessedFaceEmbeddings = false
        private val allImageUris: MutableList<Uri> = mutableListOf()
        val momentsLiveData: MutableLiveData<List<MomentGroup>> = MutableLiveData(emptyList())
        private var selectedMoment: Moment? = null
        var isPhotoFetchReload: Boolean? = true
        var isVideoFetchReload: Boolean? = true

        fun setSelectedMoment(moment: Moment?) {
            selectedMoment = moment
        }

        fun getSelectedMoment(): Moment? {
            return selectedMoment
        }

        val groupedLocationPhotosLiveData: MutableLiveData<List<GroupedLocationPhoto>> =
            MutableLiveData(emptyList())
        val documentGroupsLiveData: MutableLiveData<List<DocumentGroup>> =
            MutableLiveData(emptyList())
        val duplicateImageGroupsLiveData: MutableLiveData<List<DuplicateImageGroup>> =
            MutableLiveData(emptyList())

        private val _faceGroups = MutableLiveData<List<FaceGroupingUtils.FaceGroup>>()
        val faceGroups: LiveData<List<FaceGroupingUtils.FaceGroup>> get() = _faceGroups

        var PRIVACY_POLICY_LINK: String = "https://www.google.com/"
        var TERMS_OF_SERVICES_LINK: String = "https://www.google.com/"

        private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        private val databaseDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

        fun triggerFaceGrouping(context: Context) {
            applicationScope.launch {
                try {
                    val groups = FaceGroupingUtils.groupSimilarFaces(context)
                    _faceGroups.postValue(groups)
                } catch (_: Exception) {
                    _faceGroups.postValue(emptyList())
                }
            }
        }

        fun updateAllImageUris(uris: List<Uri>) {
            synchronized(allImageUris) {
                allImageUris.clear()
                allImageUris.addAll(uris)
            }
        }

        fun getAllImageUris(): List<Uri> {
            synchronized(allImageUris) {
                return allImageUris.toList()
            }
        }

        fun getLocationPhotosByName(locationName: String): LiveData<GroupedLocationPhoto?> {
            val result = MediatorLiveData<GroupedLocationPhoto?>()
            result.addSource(groupedLocationPhotosLiveData) { groups ->
                result.value = groups.find { it.locationName == locationName }
            }
            return result
        }

        fun getDocumentGroupByName(name: String): LiveData<DocumentGroup?> {
            val result = MediatorLiveData<DocumentGroup?>()
            result.addSource(documentGroupsLiveData) { groups ->
                result.value = groups.find { it.name == name }
            }
            return result
        }

        fun deleteLocationGroupByUri(uriToDelete: Uri) {
            val currentGroups = groupedLocationPhotosLiveData.value?.toList() ?: emptyList()
            val updatedGroups = synchronized(this) {
                currentGroups.mapNotNull { group ->
                    val urisCopy = group.allUris.toList()
                    val updatedUris = urisCopy.filter { it != uriToDelete }
                    if (updatedUris.isNotEmpty()) {
                        group.copy(allUris = updatedUris.toMutableList())
                    } else {
                        null
                    }
                }
            }
            groupedLocationPhotosLiveData.postValue(updatedGroups)
        }

        fun deleteDocumentGroupByUri(uriToDelete: Uri) {
            val currentGroups = documentGroupsLiveData.value?.toList() ?: emptyList()
            val updatedGroups = synchronized(this) {
                currentGroups.mapNotNull { group ->
                    val urisCopy = group.allUris.toList()
                    val updatedUris = urisCopy.filter { it != uriToDelete }
                    if (updatedUris.isNotEmpty()) {
                        group.copy(allUris = updatedUris.toMutableList())
                    } else {
                        null
                    }
                }
            }
            documentGroupsLiveData.postValue(updatedGroups)
        }

        fun deleteDuplicateGroupByUri(uriToDelete: Uri) {
            val currentGroups = duplicateImageGroupsLiveData.value?.toList() ?: emptyList()
            val updatedGroups = synchronized(this) {
                currentGroups.mapNotNull { group ->
                    val urisCopy = group.allUris.toList()
                    val updatedUris = urisCopy.filter { it != uriToDelete }
                    if (updatedUris.size >= 2) {
                        group.copy(allUris = updatedUris.toMutableList())
                    } else {
                        null
                    }
                }
            }
            duplicateImageGroupsLiveData.postValue(updatedGroups)
        }

        fun processFaceEmbeddings(context: Context) {
            synchronized(this) {
                if (hasProcessedFaceEmbeddings) return
                hasProcessedFaceEmbeddings = true
            }
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    FaceEmbeddingUtils.processImagesForFaceEmbeddings(instance)
                    triggerFaceGrouping(context)
                } catch (_: Exception) {
                    synchronized(this@launch) { hasProcessedFaceEmbeddings = false }
                }
            }
        }

        fun processDuplicates(context: Context) {
            synchronized(this) {
                if (hasProcessedDuplicates) return
                hasProcessedDuplicates = true
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val photos = getAllImageUris().ifEmpty {
                        LocationUtils.getPhotosFromGallery(context)
                    }.filter { UriUtils.isUriValid(context, it) }

                    ClassificationUtils.clearImageHashMap()
                    photos.chunked(50).forEach { batch ->
                        coroutineScope {
                            batch.map { uri ->
                                async {
                                    try {
                                        ClassificationUtils.checkAndStoreDuplicate(context, uri)
                                    } catch (e: Exception) {
                                        Log.e("DuplicateDetection", "Error processing URI $uri", e)
                                    }
                                }
                            }.awaitAll()
                        }
                    }
                    // Compute duplicate groups after all hashes are calculated
                    ClassificationUtils.updateDuplicateGroupsLiveData()
                } catch (e: Exception) {
                    Log.e("MyApplication", "Duplicate processing failed", e)
                    hasProcessedDuplicates = false // Allow retry on failure
                }
            }
        }

        fun processLocationPhotos(context: Context) {
            synchronized(this) {
                if (hasProcessedLocationPhotos) return
                hasProcessedLocationPhotos = true
            }
            CoroutineScope(Dispatchers.Default).launch {
                val photos = LocationUtils.getPhotosFromGallery(context)
                val locationMap = mutableMapOf<String?, GroupedLocationPhoto>()

                photos.forEach { uri ->
                    val location = LocationUtils.getImageLocationFromUri(context, uri)
                    if (location != null) {
                        val locationName =
                            LocationUtils.getLocationName(context, location.first, location.second)
                        val key = locationName ?: "Unknown Location"
                        synchronized(locationMap) {
                            if (locationMap.containsKey(key)) {
                                locationMap[key]!!.allUris.add(uri)
                            } else {
                                locationMap[key] = GroupedLocationPhoto(
                                    locationName = key,
                                    representativeUri = uri,
                                    allUris = mutableListOf(uri),
                                    latitude = location.first,
                                    longitude = location.second
                                )
                            }
                        }
                    }
                }
                groupedLocationPhotosLiveData.postValue(locationMap.values.toList())
            }
        }

        fun processPhotoClassification(context: Context) {
            synchronized(this) {
                if (hasProcessedClassification) {
                    return
                }
                hasProcessedClassification = true
            }
            CoroutineScope(Dispatchers.Default).launch {
                val photos = getAllImageUris().ifEmpty {
                    LocationUtils.getPhotosFromGallery(context)
                }.filter { UriUtils.isUriValid(context, it) }
                val tempDocumentGroups =
                    ClassificationUtils.initializeDocumentCategories().toMutableMap()
                val tempDocumentGroupsList = mutableListOf<DocumentGroup>()
                ClassificationUtils.clearImageHashMap()

                photos.forEachIndexed { index, uri ->
                    try {
                        ClassificationUtils.checkAndStoreDuplicate(context, uri)
                        ClassificationUtils.classifyPhoto(context, uri) { category ->
                            synchronized(tempDocumentGroups) {
                                if (!tempDocumentGroups.containsKey(category)) {
                                    tempDocumentGroups[category] = DocumentGroup(name = category)
                                }
                                val documentGroup =
                                    tempDocumentGroups[category] ?: return@synchronized
                                if (!documentGroup.allUris.contains(uri)) {
                                    documentGroup.allUris.add(uri)
                                }
                                tempDocumentGroupsList.clear()
                                tempDocumentGroupsList.addAll(tempDocumentGroups.values.filter { it.allUris.isNotEmpty() })
                                documentGroupsLiveData.postValue(tempDocumentGroupsList)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MyApplication", "Photo classification failed for URI $uri", e)
                    }
                }
                documentGroupsLiveData.postValue(tempDocumentGroups.values.filter { it.allUris.isNotEmpty() })
            }
        }

        fun processMoments(context: Context) {
            synchronized(this) {
                if (hasProcessedMoments) return
                hasProcessedMoments = true
            }
            CoroutineScope(Dispatchers.Default).launch {
                val photos = getAllImageUris().ifEmpty {
                    LocationUtils.getPhotosFromGallery(context)
                }.filter { UriUtils.isUriValid(context, it) }
                val moments = MomentsUtils.groupPhotosIntoMoments(context, photos)
                momentsLiveData.postValue(moments)
            }
        }

        fun setupTooltip(
            context: Context,
            view: View,
            text: String,
            orientation: ArrowOrientation,
            ePreferences: SharedPreferenceHelper,
            prefKey: String,
            onDismissCallback: (() -> Unit)? = null
        ) {
            val activity = context as? Activity
            if (activity == null || activity.isFinishing || activity.isDestroyed) return

            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.colorApp, typedValue, true)
            val colorPrimary = typedValue.data

            val balloon = Balloon.Builder(context).setWidthRatio(0f).setHeight(BalloonSizeSpec.WRAP)
                .setText(text).setTextColorResource(R.color.white).setTextSize(15f)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR).setArrowSize(10)
                .setPadding(10).setArrowPosition(0.5f).setCornerRadius(8f)
                .setArrowOrientation(orientation).setBackgroundColor(colorPrimary)
                .setLifecycleOwner(activity as LifecycleOwner?).setDismissWhenLifecycleOnPause(true)
                .setOnBalloonDismissListener {
                    ePreferences.putBoolean(prefKey, false)
                    onDismissCallback?.invoke()
                }.build()

            view.post {
                if (view.isAttachedToWindow && view.windowToken != null && activity.window?.decorView?.windowToken != null) {
                    try {
                        balloon.showAlignBottom(view)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun notifyFileDeleted(uriToDelete: Uri) {
        synchronized(this) {
            deleteDuplicateGroupByUri(uriToDelete)
            deleteLocationGroupByUri(uriToDelete)
            deleteDocumentGroupByUri(uriToDelete)

            applicationScope.launch {
                try {
                    withContext(databaseDispatcher) {
                        val database = PhotoGalleryDatabase.getDatabase(this@MyApplication)
                        database.photoGalleryDao().deleteEmbeddingsByUri(uriToDelete.toString())
                        triggerFaceGrouping(this@MyApplication)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        FirebaseApp.initializeApp(this)
        pref = SharedPreferenceHelper.getInstance(this)
        performAutoDeletion()
        setupNightMode()

        if (hasStoragePermission()) {
            triggerFaceGrouping(this)
            processLocationPhotos(this)
            processPhotoClassification(this)
            processMoments(this)
            processDuplicates(this)
            processFaceEmbeddings(this)
        }
    }

    private fun performAutoDeletion() {
        CoroutineScope(Dispatchers.IO).launch {
            val database = PhotoGalleryDatabase.getDatabase(this@MyApplication)
            val allDeletedMedia = database.photoGalleryDao().getAllDeletedMedia()
            val deleteInDays = pref.getInt("deleteInDays", 30)

            allDeletedMedia.forEach { entity ->
                val daysPassed =
                    ((System.currentTimeMillis() - entity.deletedAt) / (1000 * 60 * 60 * 24)).toInt()
                if (daysPassed >= deleteInDays) {
                    val file = File(entity.originalPath)
                    if (file.exists()) {
                        file.delete()
                        val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                            data = Uri.fromFile(file)
                        }
                        sendBroadcast(intent)
                    }
                    database.photoGalleryDao().deleteDeletedMediaById(entity.id)
                }
            }
        }
    }

    fun setSelectedMediaAndAction(media: List<MediaData>, actionType: String?) {
        selectedMedia = media
        action = actionType
    }

    fun clearSelectedMediaAndAction() {
        selectedMedia = emptyList()
        action = null
    }

    fun preventTwoClick(view: View) {
        view.isEnabled = false
        view.postDelayed({ view.isEnabled = true }, 800)
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun setupNightMode() {
        when (pref.getInt(PREF_THEME, THEME_SYSTEM_DEFAULT)) {
            THEME_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    internal fun hasStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Environment.isExternalStorageManager().also {
                    Log.d("Permissions", "External Storage Manager: $it")
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_MEDIA_VIDEO
                ) == PackageManager.PERMISSION_GRANTED).also {
                    Log.d("Permissions", "READ_MEDIA_IMAGES/VIDEO: $it")
                }
            }

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED.also {
                    Log.d("Permissions", "READ_EXTERNAL_STORAGE: ${0}")
                }
            }

            else -> {
                (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(
                    this, Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED).also {
                    Log.d("Permissions", "READ/WRITE_EXTERNAL_STORAGE: $it")
                }
            }
        }
    }
}