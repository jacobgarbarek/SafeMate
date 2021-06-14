package com.example.dms_assignment4mobileclient

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.dms_assignment4mobileclient.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnMyLocationButtonClickListener
import com.google.android.gms.maps.GoogleMap.OnMyLocationClickListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL


class MapsActivity : AppCompatActivity(), OnMyLocationButtonClickListener,
    OnMyLocationClickListener, OnMapReadyCallback, OnRequestPermissionsResultCallback {

    private var liveLocations = ArrayList<com.example.dms_assignment4mobileclient.Location>()       //live locations of other users
    private lateinit var fusedLocationClient: FusedLocationProviderClient                           //provides location information
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var locationRequest: LocationRequest
    private lateinit var userLatLng: LatLng                                                         //user location co-ordinates
    private var locationCallback = LocationCallBackHandler()
    private lateinit var userName: String
    private lateinit var safeButton : Button
    private lateinit var helpButton : Button
    private var safeFlags = HashMap<String, Boolean>()                                              //maintains flags of all connected users safety status
    private var helpFlags = HashMap<String, Boolean>()                                              //maintains flags of all connected users help status
    private var safe = true
    private var help = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationRequest = LocationRequest.create().apply {
            interval = 100
            fastestInterval = 50
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            maxWaitTime= 100
        }

        val intent = intent
        userName = intent.getStringExtra("username").toString()                               //username from login activity

        safeButton = findViewById(R.id.safe_button)
        helpButton = findViewById(R.id.help_button)

        safeButton.setOnClickListener {
            if(safe) {
                defaultSnackBar("Message already sent!")
            }else{
                safe = true
                if(help)
                    help = false
                updateLocations()       //passes safety message to server
                defaultSnackBar("Message sent!")
            }
        }

        helpButton.setOnClickListener {
            if(help)
                defaultSnackBar("Message already sent!")
            else{
                help = true
                if(safe)
                    safe = false
                updateLocations()       //passes help message to server
                defaultSnackBar("Message sent!")
            }
        }
    }

    private fun defaultSnackBar(message: String) {
        val sb = Snackbar.make(findViewById(R.id.maps_view), message, Snackbar.LENGTH_SHORT)
        sb.setActionTextColor(Color.WHITE)
        val sbView = sb.view
        sbView.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.royal_blue))
        sb.show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        googleMap.setOnMyLocationButtonClickListener(this)
        googleMap.setOnMyLocationClickListener(this)
        requestPermission()  //permission requests
    }

    private fun requestPermission(){
        when{
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                //permission is granted
                locationPermissionGrantedAction()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                //Additional rational should be displayed
                locationRequestDialog()
            }
            else -> {
                //Permission has not been asked yet
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE-> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission is granted.
                    locationPermissionGrantedAction()
                } else {
                    Log.i("Permission:", "Denied")
                    locationRequestDialog()
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }

    private fun locationRequestDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Request")
            .setMessage("GPS is required to use this application.")
            .setPositiveButton("Allow") { _, _ ->
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this, LoginActivity::class.java)
                startActivity(intent)
                finish()
            }
            .create()
            .show()
    }

    @SuppressLint("MissingPermission")                                                          //method only ever called after permission has been checked
    private fun locationPermissionGrantedAction() {
        val sb = Snackbar.make(findViewById(R.id.maps_view), "Welcome back $userName!", Snackbar.LENGTH_LONG)
        sb.setActionTextColor(Color.WHITE)
        val sbView = sb.view
        sbView.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.royal_blue))
        sb.show()

        mMap.isMyLocationEnabled = true

        fusedLocationClient.lastLocation                                            //positions camera to current location of user
            .addOnSuccessListener { location: Location? ->
                val latLng = location?.let { LatLng(it.latitude, it.longitude) }
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14.9f))
            }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )       //location update listener
    }

    override fun onMyLocationButtonClick(): Boolean {
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false
    }

    override fun onMyLocationClick(location: Location) {
        Toast.makeText(this, "Current location:\n$location", Toast.LENGTH_LONG).show()
    }

    inner class LocationCallBackHandler : LocationCallback() {
        override fun onLocationResult(location: LocationResult) {       //called on location change
            super.onLocationResult(location)
            val mostRecentLocation = location.lastLocation
            userLatLng = LatLng(mostRecentLocation.latitude, mostRecentLocation.longitude)      //updates user location
            Log.println(Log.INFO, "LOCATION", "User at $userLatLng")

            updateLocations()                                                                   //sends new location to server
            getLocations()                                                                      //receives connected users locations from server

            mMap.clear()                                                                            //removes markers from map

            for(liveLocation in liveLocations){
                if(liveLocation.username != userName) {
                    val latLng = LatLng(liveLocation.latitude, liveLocation.longitude)
                    mMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title(liveLocation.username)
                            .icon(generateBitmapDescriptorFromRes(applicationContext, R.drawable.ic_baseline_person_pin_24))
                    )

                    if(safeFlags.containsKey(liveLocation.username)){                                   //user already connected to system
                        if(liveLocation.safe && safeFlags[liveLocation.username] == false){                 //newly updated status
                            val sb = Snackbar.make(findViewById(R.id.maps_view), "$userName is safe!", Snackbar.LENGTH_INDEFINITE).setAction("Dismiss"){}
                            sb.setActionTextColor(Color.WHITE)
                            val sbView = sb.view
                            sbView.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.green))
                            sb.show()
                            safeFlags[liveLocation.username] = true

                            if(helpFlags[liveLocation.username] == true)                                //user no longer requires help
                                helpFlags[liveLocation.username] = false
                        }else if(liveLocation.help && helpFlags[liveLocation.username] == false){           //newly updated status
                            val sb = Snackbar.make(findViewById(R.id.maps_view), "$userName needs help!", Snackbar.LENGTH_INDEFINITE).setAction("Dismiss"){}
                            sb.setActionTextColor(Color.WHITE)
                            val sbView = sb.view
                            sbView.setBackgroundColor(ContextCompat.getColor(applicationContext, R.color.red))
                            sb.show()
                            helpFlags[liveLocation.username] = true

                            if(safeFlags[liveLocation.username] == true)                                //user is no longer safe
                                safeFlags[liveLocation.username] = false
                        }
                    }else{                                                                          //new user connected to server
                        safeFlags[liveLocation.username] = liveLocation.safe
                        helpFlags[liveLocation.username] = liveLocation.help
                    }
                }
            }
        }
    }

    private fun getLocations() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val productUrl = URL(
                    "http://${resources.getString(R.string.ip_address)}" +
                            "/DMS_Assignment4/locationservice/location"
                )    //url of GET all connect user locations rest service

                (productUrl.openConnection() as HttpURLConnection).run {
                    readTimeout = 3000 // 3000ms
                    connectTimeout = 3000 // 3000ms
                    requestMethod = "GET"

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        val br = BufferedReader(InputStreamReader(inputStream))
                        val gson = Gson()
                        val locationType = object :
                            TypeToken<ArrayList<com.example.dms_assignment4mobileclient.Location>>() {}.type
                        val locations =
                            gson.fromJson<ArrayList<com.example.dms_assignment4mobileclient.Location>>(
                                br,
                                locationType
                            )
                        liveLocations = locations                                                  //updates locations with newly received from server
                        br.close()
                        Log.println(
                            Log.INFO,
                            "REST",
                            "SUCCESS GET ALL LOCATIONS $liveLocations"
                        )
                    } else
                        Log.println(Log.INFO, "REST", "FAIL ALL LOCATIONS")
                }
            } catch (e: MalformedURLException) {
                Log.e("REST", "Malformed URL: $e")
                e.printStackTrace()
            } catch (e: IOException) {
                Log.e("REST", "IOException: $e")
                e.printStackTrace()
            }
        }
    }

    private fun updateLocations() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val productUrl = URL(
                    "http://${resources.getString(R.string.ip_address)}" +
                            "/DMS_Assignment4/locationservice/location/$userName/${userLatLng.latitude}/${userLatLng.longitude}/$safe/$help"
                )    //url of update location rest service

                (productUrl.openConnection() as HttpURLConnection).run {
                    readTimeout = 3000 // 3000ms
                    connectTimeout = 3000 // 3000ms
                    requestMethod = "PUT"
                    setRequestProperty("Content-Type", "text/plain")

                    if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                        Log.println(
                            Log.INFO,
                            "REST",
                            "SUCCESS UPDATE LOCATION: $userName (${userLatLng.latitude}) (${userLatLng.longitude}"
                        )
                    } else {
                        Log.println(Log.INFO, "REST", "FAIL UPDATE LOCATION")
                    }
                }
            } catch (e: MalformedURLException) {
                Log.e("REST", "Malformed URL: $e")
                e.printStackTrace()
            } catch (e: IOException) {
                Log.e("REST", "IOException: $e")
                e.printStackTrace()
            }
        }
    }

    private fun generateBitmapDescriptorFromRes(context: Context, resId: Int): BitmapDescriptor {       //helper method to generate custom marker images
        val drawable = ContextCompat.getDrawable(context, resId)
        drawable?.setBounds(0,0,drawable.intrinsicWidth*2, drawable.intrinsicHeight*2)
        val bitmap = drawable?.let { Bitmap.createBitmap(it.intrinsicWidth*2,drawable.intrinsicHeight*2,Bitmap.Config.ARGB_8888) }
        val canvas = bitmap?.let { Canvas(it) }
        if (canvas != null) {
            drawable.draw(canvas)
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}
