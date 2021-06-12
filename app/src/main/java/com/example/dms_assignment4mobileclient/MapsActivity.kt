package com.example.dms_assignment4mobileclient

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.TextView
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.Task
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

    private var liveLocations = ArrayList<com.example.dms_assignment4mobileclient.Location>()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private lateinit var locationRequest: LocationRequest
    private var permissionDenied = false
    private lateinit var userLatLng: LatLng
    private var locationCallback = LocationCallBackHandler()
    private lateinit var userName: String


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
        userName = intent.getStringExtra("username").toString()
        Toast.makeText(applicationContext, "Welcome $userName", Toast.LENGTH_SHORT).show()
//        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)
//        val client: SettingsClient = LocationServices.getSettingsClient(this)
//        val task: Task<LocationSettingsResponse> = client.checkLocationSettings(builder.build())
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap ?: return
        googleMap.setOnMyLocationButtonClickListener(this)
        googleMap.setOnMyLocationClickListener(this)
        enableMyLocation()
    }

    private fun enableMyLocation() {
        if (!::mMap.isInitialized) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            mMap.isMyLocationEnabled = true

            fusedLocationClient.lastLocation                                            //positions camera to current location of user
                .addOnSuccessListener { location : Location? ->
                    val latLng = location?.let { LatLng(it.latitude,it.longitude) }
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            // Permission to access the location is missing. Show rationale and request permission
            requestPermission(
                this, LOCATION_PERMISSION_REQUEST_CODE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }


    private fun requestPermission(activity: MapsActivity, requestId: Int, permission: String) {
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            // Display a dialog with rationale.
            AlertDialog.Builder(this)
                .setTitle("Permission Request")
                .setMessage("Need access to GPS")
                .setPositiveButton("Allow"){dialog, which ->
                    ActivityCompat.requestPermissions(
                        activity!!,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        requestId
                    )
                }
                .create()
                .show()
        } else {
            // Location permission has not been granted yet, request it.
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(permission),
                requestId
            )
        }
    }

    override fun onMyLocationButtonClick(): Boolean {
        Toast.makeText(this, "MyLocation button clicked", Toast.LENGTH_SHORT).show()
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false
    }

    override fun onMyLocationClick(location: Location) {
        Toast.makeText(this, "Current location:\n$location", Toast.LENGTH_LONG).show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return
        }
        if (isPermissionGranted(permissions, grantResults, Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Enable the my location layer if the permission has been granted.
            enableMyLocation()
        } else {
            // Permission was denied. Display an error message
            // Display the missing permission error dialog when the fragments resume
            permissionDenied = true
        }
    }

    private fun isPermissionGranted(
        grantPermissions: Array<String>, grantResults: IntArray,
        permission: String
    ): Boolean {
        for (i in grantPermissions.indices) {
            if (permission == grantPermissions[i]) {
                return grantResults[i] == PackageManager.PERMISSION_GRANTED
            }
        }
        return false
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        if (permissionDenied) {
            // Permission was not granted, display error dialog.
            permissionDenied = false
            AlertDialog.Builder(this)
                .setTitle("Permission Request")
                .setMessage("Need access to GPS")
                .setPositiveButton("Allow"){dialog, which ->
                    ActivityCompat.requestPermissions(
                        this!!,
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
                }
                .create()
                .show()
        }
    }

    inner class LocationCallBackHandler : LocationCallback() {
        override fun onLocationResult(location: LocationResult) {
            super.onLocationResult(location)
            if(location == null){
                Log.println(Log.ERROR, "LOCATION", "Null location result")
                return
            }
            val mostRecentLocation = location.lastLocation
            if(mMap != null){
                userLatLng = LatLng(mostRecentLocation.latitude, mostRecentLocation.longitude)
                Log.println(Log.INFO, "LOCATION", "User at $userLatLng")
                //Toast.makeText(applicationContext, "User at $userLatLng", Toast.LENGTH_SHORT).show()

                lifecycleScope.launch(Dispatchers.IO){
                    try{
                        val productUrl = URL("http://${resources.getString(R.string.ip_address)}" +
                                "/DMS_Assignment4/locationservice/location/$userName/${userLatLng.latitude}/${userLatLng.longitude}")    //url of rest service

                        (productUrl.openConnection() as HttpURLConnection).run {
                            readTimeout = 3000 // 3000ms
                            connectTimeout = 3000 // 3000ms
                            requestMethod = "PUT"
                            setRequestProperty("Content-Type", "text/plain")

                            if(responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                                Log.println(Log.INFO, "REST", "SUCCESS UPDATE LOCATION: $userName (${userLatLng.latitude}) (${userLatLng.longitude}")
                            }else {
                                Log.println(Log.INFO, "REST", "FAIL UPDATE LOCATION")
                            }
                        }
                    }catch (e: MalformedURLException)
                    {
                        Log.e("REST", "Malformed URL: $e")
                        e.printStackTrace()
                    }
                    catch (e: IOException)
                    {
                        Log.e("REST", "IOException: $e")
                        e.printStackTrace()
                    }
                }

                lifecycleScope.launch(Dispatchers.IO){
                    try{
                        val productUrl = URL("http://${resources.getString(R.string.ip_address)}" +
                                "/DMS_Assignment4/locationservice/location")    //url of rest service

                        (productUrl.openConnection() as HttpURLConnection).run {
                            readTimeout = 3000 // 3000ms
                            connectTimeout = 3000 // 3000ms
                            requestMethod = "GET"

                            if(responseCode == HttpURLConnection.HTTP_OK){
                                val br = BufferedReader(InputStreamReader(inputStream))
                                val gson = Gson()
                                val locationType = object : TypeToken<ArrayList<com.example.dms_assignment4mobileclient.Location>>() {}.type
                                val locations = gson.fromJson<ArrayList<com.example.dms_assignment4mobileclient.Location>>(br, locationType)
                                liveLocations = locations;
                                br.close()
                                Log.println(Log.INFO, "REST", "SUCCESS GET ALL LOCATIONS ${liveLocations.toString()}")
                            }else
                                Log.println(Log.INFO, "REST", "FAIL ALL LOCATIONS")
                        }
                    }catch (e: MalformedURLException)
                    {
                        Log.e("REST", "Malformed URL: $e")
                        e.printStackTrace()
                    }
                    catch (e: IOException)
                    {
                        Log.e("REST", "IOException: $e")
                        e.printStackTrace()
                    }
                }
            } else {
                Log.println(Log.ERROR, "LOCATION", "Receiving locations but maps not yet available")
            }

            mMap.clear()

            for(location in liveLocations){
                if(location.username != userName) {
                    var latLng = LatLng(location.latitude, location.longitude)
                    mMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title(location.username)
                    )
                }
            }
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1;
    }
}
