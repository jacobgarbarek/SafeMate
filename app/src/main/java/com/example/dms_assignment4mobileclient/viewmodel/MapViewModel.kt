package com.example.dms_assignment4mobileclient.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.*
import com.example.dms_assignment4mobileclient.model.Location
import com.example.dms_assignment4mobileclient.R
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

class MapViewModel(applicationContext: Application) : AndroidViewModel(applicationContext){
    private val context = getApplication<Application>().applicationContext
    val userLocation = MutableLiveData<Location>()
    var liveLocations = MutableLiveData<ArrayList<Location>>()

    fun setUserLocation(location: Location){
        userLocation.value = location
    }

    fun getLiveLocations() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val productUrl = URL(
                    "http://${context.getString(R.string.ip_address)}" +
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
                            TypeToken<ArrayList<Location>>() {}.type
                        val locations =
                            gson.fromJson<ArrayList<Location>>(
                                br,
                                locationType
                            )
                        liveLocations.postValue(locations)                                               //updates locations with newly received from server
                        br.close()
                        Log.println(
                            Log.INFO,
                            "RESTFUL SERVICE",
                            "SUCCESS GET ALL LOCATIONS ${liveLocations.value}"
                        )
                    } else
                        Log.println(Log.INFO, "REST", "FAIL ALL LOCATIONS")
                }
            } catch (e: MalformedURLException) {
                Log.e("RESTFUL SERVICE", "Malformed URL: $e")
                e.printStackTrace()
            } catch (e: IOException) {
                Log.e("RESTFUL SERVICE", "IOException: $e")
                e.printStackTrace()
            }
        }
    }

    fun updateLiveLocation(){
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val productUrl = URL(
                    "http://${context.getString(R.string.ip_address)}" +
                            "/DMS_Assignment4/locationservice/location/${userLocation.value?.username}/${userLocation.value?.latitude}/${userLocation.value?.longitude}/${userLocation.value?.safe}/${userLocation.value?.help}"
                )    //url of update location rest service

                (productUrl.openConnection() as HttpURLConnection).run {
                    readTimeout = 3000 // 3000ms
                    connectTimeout = 3000 // 3000ms
                    requestMethod = "PUT"
                    setRequestProperty("Content-Type", "text/plain")

                    if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                        Log.println(
                            Log.INFO,
                            "RESTFUL SERVICE",
                            "SUCCESS UPDATE LOCATION: ${userLocation.value?.username}/${userLocation.value?.latitude}/${userLocation.value?.latitude}"
                        )
                    } else {
                        Log.println(Log.INFO, "RESTFUL SERVICE", "FAIL UPDATE LOCATION ${userLocation.value?.username}/${userLocation.value?.latitude}/${userLocation.value?.latitude}/${userLocation.value?.safe}/${userLocation.value?.help}")
                    }
                }
            } catch (e: MalformedURLException) {
                Log.e("RESTFUL SERVICE", "Malformed URL: $e")
                e.printStackTrace()
            } catch (e: IOException) {
                Log.e("RESTFUL SERVICE", "IOException: $e")
                e.printStackTrace()
            }
        }
    }
}