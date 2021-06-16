package com.example.dms_assignment4mobileclient.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.fragment.app.Fragment

import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.example.dms_assignment4mobileclient.model.Location
import com.example.dms_assignment4mobileclient.R
import com.example.dms_assignment4mobileclient.databinding.FragmentMapsBinding
import com.example.dms_assignment4mobileclient.viewmodel.MapViewModel
import com.google.android.gms.location.*

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.snackbar.Snackbar

class MapsFragment : Fragment(), GoogleMap.OnMyLocationButtonClickListener,
    GoogleMap.OnMyLocationClickListener {
    //ViewModel and binding variables
    private val viewModel: MapViewModel by activityViewModels()
    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private lateinit var liveLocationData : ArrayList<Location>
    private lateinit var liveLocation: Location
    private var safeFlags = HashMap<String, Boolean>()                                              //maintains flags of all connected users safety status
    private var helpFlags = HashMap<String, Boolean>()                                              //maintains flags of all connected users help status

    //Google maps variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient                           //provides location information
    private lateinit var mMap: GoogleMap
    private lateinit var locationRequest: LocationRequest
    private var locationCallback = LocationCallBackHandler()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        liveLocationData = ArrayList()

        liveLocation = if (savedInstanceState != null && savedInstanceState.containsKey("liveLocation"))        //captures live location if previously saved
            savedInstanceState.getParcelable("liveLocation")!!
        else
            Location()

        initMap()
        initMapPermissions()                                                                        //requests user's location
        initViewModel()
    }

    private fun initMapPermissions() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(activity)
        locationRequest = LocationRequest.create().apply {
            interval = 100
            fastestInterval = 50
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            maxWaitTime= 100
        }
    }

    private fun initViewModel() {
        viewModel.liveLocations.observe(viewLifecycleOwner, Observer {                              //Listens for changes to live locations of users connected to server
            liveLocationData.clear()
            liveLocationData.addAll(it)

            mMap.clear()                                                                            //clears map for new markers to be added

            for(peerLocation in liveLocationData){
                if(peerLocation.username != liveLocation.username) {
                    val latLng = LatLng(peerLocation.latitude, peerLocation.longitude)
                    mMap.addMarker(                                                                 //live position/location of peer user
                        MarkerOptions()
                            .position(latLng)
                            .title(peerLocation.username)
                            .icon(activity?.let { it2 -> generateBitmapDescriptorFromRes(it2.applicationContext,
                                R.drawable.ic_baseline_person_pin_24
                            ) })
                    )

                    if(safeFlags.containsKey(peerLocation.username)){                                   //user already connected to system
                        if(peerLocation.safe && safeFlags[peerLocation.username] == false){                 //newly updated status
                            createSnackBar("safe", "${peerLocation.username} is safe!")
                            safeFlags[peerLocation.username] = true

                            if(helpFlags[peerLocation.username] == true)                                //user no longer requires help
                                helpFlags[peerLocation.username] = false
                        }else if(peerLocation.help && helpFlags[peerLocation.username] == false){           //newly updated status
                            createSnackBar("help", "${peerLocation.username} needs help!")
                            helpFlags[peerLocation.username] = true

                            if(safeFlags[peerLocation.username] == true)                                //user is no longer safe
                                safeFlags[peerLocation.username] = false
                        }
                    }else{                                                                          //new user connected to server
                        safeFlags[peerLocation.username] = peerLocation.safe
                        helpFlags[peerLocation.username] = peerLocation.help
                    }
                }
            }
        })

        viewModel.userLocation.observe(viewLifecycleOwner, Observer {                               //listens to changes in user (name, location, safety flag)
            liveLocation = it
        })

        binding.safeButton.setOnClickListener {
            if(liveLocation.safe){
                createSnackBar("default", "Message already sent!")
            }else{  //Message not sent
                val updatedLocation = Location(liveLocation.username, liveLocation.latitude, liveLocation.longitude, true, liveLocation.help)
                if(liveLocation.help)
                    updatedLocation.help = false                                                    //user does not require help when safe
                viewModel.setUserLocation(updatedLocation)                                          //updates change in user locally
                viewModel.updateLiveLocation()                                                      //pushes change to server
                createSnackBar("default", "Message sent!")
            }
        }

        binding.helpButton.setOnClickListener {
            if(liveLocation.help)
                createSnackBar("default", "Message already sent!")
            else{   //Message not sent
                val updatedLocation = Location(liveLocation.username, liveLocation.latitude, liveLocation.longitude, liveLocation.safe, true)
                if(liveLocation.safe)
                    updatedLocation.safe = false                                                    //user is not sage when is requesting help
                viewModel.setUserLocation(updatedLocation)
                viewModel.updateLiveLocation()
                createSnackBar("default", "Message sent!")
            }
        }
    }

    private fun initMap() {
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(callback)
    }

    private fun createSnackBar(type: String, message: String) {
        val sb = activity?.let {
            Snackbar.make(
                it.findViewById(R.id.map),
                message,
                Snackbar.LENGTH_LONG
            )
        }

        sb?.setActionTextColor(Color.WHITE)

        when(type){
            "safe" -> {
                activity?.let { ContextCompat.getColor(it.applicationContext, R.color.green) }?.let {
                    sb?.view?.setBackgroundColor(
                        it
                    )
                }
            }
            "help" -> {
                activity?.let { ContextCompat.getColor(it.applicationContext, R.color.red) }?.let {
                    sb?.view?.setBackgroundColor(
                        it
                    )
                }
            }
            else -> {       //default snackbar
                activity?.let { ContextCompat.getColor(it.applicationContext, R.color.royal_blue) }?.let {
                    sb?.view?.setBackgroundColor(
                        it
                    )
                }
            }
        }

        sb?.show()
    }

    private fun generateBitmapDescriptorFromRes(context: Context, resId: Int): BitmapDescriptor {       //helper method to generate custom marker images
        val drawable = ContextCompat.getDrawable(context, resId)
        drawable?.setBounds(0,0,drawable.intrinsicWidth*2, drawable.intrinsicHeight*2)
        val bitmap = drawable?.let { Bitmap.createBitmap(it.intrinsicWidth*2,drawable.intrinsicHeight*2,
            Bitmap.Config.ARGB_8888) }
        val canvas = bitmap?.let { Canvas(it) }
        if (canvas != null) {
            drawable.draw(canvas)
        }
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private val callback = OnMapReadyCallback { googleMap ->
        mMap = googleMap
        googleMap.setOnMyLocationButtonClickListener(this)
        googleMap.setOnMyLocationClickListener(this)
        requestPermission()  //permission requests
    }

    private fun requestPermission() {
        when{
            context?.let {
                ContextCompat.checkSelfPermission(
                    it,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            } == PackageManager.PERMISSION_GRANTED -> {
                //permission is granted
                locationPermissionGrantedAction()
            }
            activity?.let {
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
            } == true -> {
                //Additional rational should be displayed
                locationRequestDialog()
            }
            else -> {
                //Permission has not been asked yet
                requestPermissions(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Log.i("Permission:", "Granted")
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
        AlertDialog.Builder(activity)
            .setTitle("Permission Request")
            .setMessage("GPS is required to use this application.")
            .setPositiveButton("Allow") { _, _ ->
                    requestPermissions(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                        LOCATION_PERMISSION_REQUEST_CODE
                    )
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                val action = MapsFragmentDirections.actionLoginFragment()
                findNavController().navigate(action)
            }
            .create()
            .show()
    }

    @SuppressLint("MissingPermission")                                                          //method only ever called after permission has been checked
    private fun locationPermissionGrantedAction() {
        createSnackBar("standard", "Welcome back ${liveLocation.username}!")

        mMap.isMyLocationEnabled = true

        fusedLocationClient.lastLocation                                            //positions camera to current location of user
            .addOnSuccessListener { location: android.location.Location? ->
                val latLng = location?.let { LatLng(it.latitude, it.longitude) }
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14.9f))
            }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )                                                                               //location update listener
    }

    override fun onMyLocationButtonClick(): Boolean {
        // Return false so that we don't consume the event and the default behavior still occurs
        // (the camera animates to the user's current position).
        return false
    }

    override fun onMyLocationClick(location: android.location.Location) {
        Toast.makeText(activity?.applicationContext, "Current location:\n$location", Toast.LENGTH_LONG).show()
    }

    inner class LocationCallBackHandler : LocationCallback(){
        override fun onLocationResult(location: LocationResult) {       //called on location change
            super.onLocationResult(location)
            val mostRecentLocation = location.lastLocation
            val updatedLocation = Location(liveLocation.username, mostRecentLocation.latitude, mostRecentLocation.longitude, liveLocation.safe, liveLocation.help)

            viewModel.setUserLocation(updatedLocation)
            viewModel.updateLiveLocation()                                                                  //sends new location to server
            viewModel.getLiveLocations()                                                                     //receives connected users locations from server
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {                                            //saves user details
        super.onSaveInstanceState(outState)
        outState.putParcelable("liveLocation", liveLocation)
    }

    companion object{
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}