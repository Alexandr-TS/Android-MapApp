package com.example.maplab

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.Manifest
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import io.realm.Realm
import java.util.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private val distThreshold = 200.0 // metres
    private var cooldownSecs = 120

    private var lastAlertTimeKey: String = "LAST_ALERT_TIME_KEY"

    private lateinit var mMap: GoogleMap
    private lateinit var realm: Realm
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var mGoogleApiClient: GoogleApiClient? = null
    private var mCurrentLocation: Location? = null
    private var mMarker: Marker? = null
    private var lastAlertTime: Long = -1000 * 1000 * 1000

    private lateinit var mLocationRequest: LocationRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        mLocationRequest = LocationRequest()
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        Realm.init(this)
        realm = Realm.getDefaultInstance()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(lastAlertTimeKey, lastAlertTime)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        lastAlertTime = savedInstanceState.getLong(lastAlertTimeKey)
    }

    private fun bitmapDescriptorFromVector(context: Context, vectorResId: Int): BitmapDescriptor? {
        return ContextCompat.getDrawable(context, vectorResId)?.run {
            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
            val bitmap = Bitmap.createBitmap(intrinsicWidth, intrinsicHeight, Bitmap.Config.ARGB_8888)
            draw(Canvas(bitmap))
            BitmapDescriptorFactory.fromBitmap(bitmap)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            //Location Permission already granted
            buildGoogleApiClient();
        } else {
            //Request Location Permission
            checkLocationPermission();
        }

        val markers = realm.where(EntMarker::class.java).findAll()
        for (marker in markers) {
            mMap.addMarker(MarkerOptions().position(marker.getPosition()).title("Loaded marker"))
        }

        mMap.setOnMapClickListener(object : GoogleMap.OnMapClickListener {
            override fun onMapClick(p0: LatLng) {
                realm.beginTransaction()
                val marker = realm.createObject(EntMarker::class.java)
                marker.setPosition(p0)
                realm.commitTransaction()

                mMap.addMarker(MarkerOptions().position(p0).title("New marker"))
                mMap.moveCamera(CameraUpdateFactory.newLatLng(p0))
            }
        })

        mMap.setOnMarkerClickListener { marker ->
            if (marker.title == "geopos") {
                true
            } else {
                marker.title = "Clicked"
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("marker_position", marker.position);
                startActivity(intent)
                true
            }
        }
    }

    @Synchronized
    private fun buildGoogleApiClient() {
        mGoogleApiClient = GoogleApiClient.Builder(this)
            .addApi(LocationServices.API)
            .build()
        mGoogleApiClient!!.connect()
        startLocationUpdates()
    }

    private val MY_PERMISSIONS_REQUEST_LOCATION = 99
    private fun checkLocationPermission() {
        val permissionAccessCoarseLocationApproved = ActivityCompat
            .checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

        if (!permissionAccessCoarseLocationApproved) {
              // App doesn't have access to the device's location at all. Make full request
            // for permission.
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MY_PERMISSIONS_REQUEST_LOCATION
            )
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            onLocationChanged(locationResult.lastLocation)
        }
    }

    private fun checkNearLocations(prevLocation: Location, newLocation: Location) {
        val markers = realm.where(EntMarker::class.java).findAll()
        var isNeeded = false
        for (marker in markers) {
            val markerLocation = Location("")
            markerLocation.latitude = marker.getPosition().latitude
            markerLocation.longitude = marker.getPosition().longitude
            val distPrev = prevLocation.distanceTo(markerLocation)
            val distNew = newLocation.distanceTo(markerLocation)
            if (distPrev > distThreshold && distNew <= distThreshold) {
                isNeeded = true
            }
        }
        if (isNeeded) {
            val builder = AlertDialog.Builder(this)
            with(builder)
            {
                setTitle("You're near!")
                setMessage("You are approaching one of the marked locations")
                setPositiveButton(
                    "OK",
                    null
                )
                show()
            }
            lastAlertTime = Calendar.getInstance().timeInMillis
        }
    }

    fun onLocationChanged(location: Location) {
        val latitude = location.getLatitude();
        val longitude = location.getLongitude();
        val position = LatLng(latitude, longitude);

        if (mMarker == null) {
            val opts = MarkerOptions().position(position).title("geopos").icon(
                bitmapDescriptorFromVector(this, R.drawable.ic_my_location_blue_24dp))
            mMarker = mMap.addMarker(opts);
            mMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(latitude, longitude),
                    (10.0).toFloat()
                )
            )
        } else {
            mMarker!!.position = position
        }
        val curTime = Calendar.getInstance().timeInMillis
        if (curTime > lastAlertTime + 1000 * cooldownSecs && mCurrentLocation != null) {
            checkNearLocations(mCurrentLocation!!, location)
        }
        mCurrentLocation = location
    }

    private fun startLocationUpdates() {
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        mLocationRequest.setInterval(2000)
        mLocationRequest.setFastestInterval(1000)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.requestLocationUpdates(mLocationRequest,
            locationCallback,
            Looper.getMainLooper())
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>, grantResults: IntArray
    ) {
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        if (mGoogleApiClient == null) {
                            buildGoogleApiClient()
                        }
                    }
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show()
                }
                return
            }
        }// other 'case' lines to check for other
        // permissions this app might request
    }
}
