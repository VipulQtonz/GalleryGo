package com.photogallery.activity

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.scale
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.photogallery.MyApplication
import com.photogallery.MyApplication.Companion.setupTooltip
import com.photogallery.R
import com.photogallery.adapter.ImageAdapter
import com.photogallery.databinding.ActivityLocationPhotoViewerBinding
import com.photogallery.databinding.DialogPersonaliseGridBinding
import com.photogallery.model.MediaData
import com.skydoves.balloon.ArrowOrientation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LocationPhotoViewerActivity : BaseActivity<ActivityLocationPhotoViewerBinding>(),
    OnMapReadyCallback {
    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    var personaliseLayoutDialog: AlertDialog? = null
    var googleMapType = GoogleMap.MAP_TYPE_NORMAL
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var albumName = ""
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>
    private var isWhat: String = ""

    override fun getViewBinding(): ActivityLocationPhotoViewerBinding {
        return ActivityLocationPhotoViewerBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheet)
        bottomSheetBehavior.isHideable = false // Prevent fully collapsing
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED // Start at 50% expanded
        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        })
    }

    override fun init(savedInstanceState: Bundle?) {
        isWhat = intent.getStringExtra("isWhat") ?: ""
        albumName = intent.getStringExtra("albumName") ?: "Album"
        latitude = intent.getDoubleExtra("latitude", 0.0)
        longitude = intent.getDoubleExtra("longitude", 0.0)
        val photoCount = intent.getIntExtra("photoCount", 0)
        binding.toolbar.tvToolbarTitle.text = albumName
        binding.tvBottomSheetTitle.text = getString(R.string.photos_, photoCount)

        if (ePreferences.getBoolean("isFirstTimeLocationPhotoViewer", true)) {
            setupTooltip(
                this,
                binding.flMap,
                getString(R.string.click_to_choose_map_type),
                ArrowOrientation.BOTTOM,
                ePreferences,
                "null"
            )
            {
                setupTooltip(
                    this,
                    binding.dragHandle,
                    getString(R.string.drag_handle_to_move),
                    ArrowOrientation.BOTTOM,
                    ePreferences,
                    "isFirstTimeLocationPhotoViewer"
                )
            }
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        val location = LatLng(latitude, longitude)

        val markerOptions = MarkerOptions()
            .position(location)
            .title(albumName)
            .anchor(0.5f, 1.0f)
        if (MyApplication.selectedAlbumImages.isNotEmpty()) {
            val bitmap = createCircularBitmapFromUri(MyApplication.selectedAlbumImages[0])
            if (bitmap != null) {
                markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap))
            }
        }
        googleMap?.addMarker(markerOptions)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            googleMap?.isMyLocationEnabled = true
            addCurrentLocationMarker()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                100
            )
        }
    }

    private fun addCurrentLocationMarker() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLocation = LatLng(location.latitude, location.longitude)
                    googleMap?.addMarker(
                        MarkerOptions()
                            .position(currentLocation)
                            .title(getString(R.string.my_location))
                    )
                }
            }.addOnFailureListener { e ->
                e.printStackTrace()
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            googleMap?.isMyLocationEnabled = true
            addCurrentLocationMarker()
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        populateMediaList()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun addListener() {
        binding.toolbar.ivBack.setOnClickListener {
            onBackPressedDispatcher()
        }

        binding.flMap.setOnClickListener {
            showPersonaliseLayoutDialog()
        }
    }

    override fun onBackPressedDispatcher() {
        backScreenAnimation()
        finish()
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
                updateUI()
            }
        }
    }

    private fun updateUI() {
        val imageUris = MyApplication.selectedAlbumImages
        if (imageUris.isEmpty()) {
            binding.llEmptyBottomSheet.llEmptyLayout.visibility = View.VISIBLE
            binding.rvBottomSheetImages.visibility = View.GONE
        } else {
            binding.llEmptyBottomSheet.llEmptyLayout.visibility = View.GONE
            binding.rvBottomSheetImages.visibility = View.VISIBLE
            binding.rvBottomSheetImages.layoutManager = GridLayoutManager(this, 3)
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
            binding.rvBottomSheetImages.adapter = adapter
        }
    }

    private fun showPersonaliseLayoutDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setCancelable(false)
        val layoutDialogBinding = DialogPersonaliseGridBinding.inflate(LayoutInflater.from(this))
        builder.setCancelable(false)

        layoutDialogBinding.rlComfort.visibility = View.VISIBLE
        layoutDialogBinding.divider.visibility = View.VISIBLE
        layoutDialogBinding.tvTitle.text = getString(R.string.select_map_type)
        layoutDialogBinding.ivComfort.setImageResource(R.drawable.ic_default_map)
        layoutDialogBinding.tvComfortable.text = getString(R.string.default_)

        layoutDialogBinding.ivDay.setImageResource(R.drawable.ic_satellite_map)
        layoutDialogBinding.tvDay.text = getString(R.string.satellite)

        layoutDialogBinding.ivMonth.setImageResource(R.drawable.ic_terrain_map)
        layoutDialogBinding.tvMonth.text = getString(R.string.terrain)

        when (googleMapType) {
            GoogleMap.MAP_TYPE_NORMAL -> {
                layoutDialogBinding.rbComfort.isChecked = true
                layoutDialogBinding.rbDay.isChecked = false
                layoutDialogBinding.rbMonth.isChecked = false
            }

            GoogleMap.MAP_TYPE_SATELLITE -> {
                layoutDialogBinding.rbDay.isChecked = true
                layoutDialogBinding.rbComfort.isChecked = false
                layoutDialogBinding.rbMonth.isChecked = false
            }

            GoogleMap.MAP_TYPE_TERRAIN -> {
                layoutDialogBinding.rbMonth.isChecked = true
                layoutDialogBinding.rbComfort.isChecked = false
                layoutDialogBinding.rbDay.isChecked = false
            }
        }

        layoutDialogBinding.rbComfort.setOnClickListener {
            layoutDialogBinding.rbComfort.isChecked = true
            layoutDialogBinding.rbDay.isChecked = false
            layoutDialogBinding.rbMonth.isChecked = false
            googleMapType = GoogleMap.MAP_TYPE_NORMAL
        }
        layoutDialogBinding.rbDay.setOnClickListener {
            layoutDialogBinding.rbDay.isChecked = true
            layoutDialogBinding.rbComfort.isChecked = false
            layoutDialogBinding.rbMonth.isChecked = false
            googleMapType = GoogleMap.MAP_TYPE_SATELLITE
        }
        layoutDialogBinding.rbMonth.setOnClickListener {
            layoutDialogBinding.rbMonth.isChecked = true
            layoutDialogBinding.rbComfort.isChecked = false
            layoutDialogBinding.rbDay.isChecked = false
            googleMapType = GoogleMap.MAP_TYPE_TERRAIN
        }

        layoutDialogBinding.btnApply.setOnClickListener {
            googleMap?.mapType = googleMapType
            personaliseLayoutDialog?.dismiss()
        }

        layoutDialogBinding.ivClose.setOnClickListener {
            personaliseLayoutDialog?.dismiss()
        }

        builder.setView(layoutDialogBinding.root)
        personaliseLayoutDialog = builder.create()
        personaliseLayoutDialog?.setCanceledOnTouchOutside(true)
        personaliseLayoutDialog?.window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        personaliseLayoutDialog?.show()
    }

    private fun createCircularBitmapFromUri(uri: android.net.Uri): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            // Define dimensions
            val markerSize = 120 // Total width/height of the marker (circle)
            val photoSize = 100 // Size of the photo inside the marker
            val dotRadius = 5f // Radius of the black dot
            val pointerHeight = 20f // Height of the pointer tip
            val totalHeight = markerSize + pointerHeight.toInt() // Total height including pointer

            // Scale the bitmap to fit inside the circle
            val scaledBitmap = originalBitmap.scale(photoSize, photoSize, false)

            // Create a bitmap for the marker
            val output = createBitmap(markerSize, totalHeight)
            val canvas = Canvas(output)
            val paint = Paint()

            // Draw the white circular background (border)
            paint.isAntiAlias = true
            paint.color = Color.WHITE
            canvas.drawCircle(markerSize / 2f, markerSize / 2f, markerSize / 2f, paint)

            val photoBitmap = createBitmap(photoSize, photoSize)
            val photoCanvas = Canvas(photoBitmap)
            photoCanvas.drawCircle(photoSize / 2f, photoSize / 2f, photoSize / 2f, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            photoCanvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
            paint.xfermode = null
            canvas.drawBitmap(
                photoBitmap,
                (markerSize - photoSize) / 2f,
                (markerSize - photoSize) / 2f,
                paint
            )

            // Draw the black dot at the bottom of the circle
            paint.color = Color.BLACK
            val dotCenterX = markerSize / 2f
            val dotCenterY = markerSize - dotRadius
            canvas.drawCircle(dotCenterX, dotCenterY, dotRadius, paint)

            // Draw the pointer tip (triangle) below the dot
            val path = Path()
            path.moveTo(dotCenterX, markerSize.toFloat()) // Top of the triangle (at the dot)
            path.lineTo(dotCenterX - dotRadius * 2, markerSize + pointerHeight) // Bottom left
            path.lineTo(dotCenterX + dotRadius * 2, markerSize + pointerHeight) // Bottom right
            path.close()
            canvas.drawPath(path, paint)

            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}