package com.example.dms_assignment4mobileclient.model

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class Location(
    var username: String = "", var latitude: Double = 0.0, var longitude: Double = 0.0, var safe: Boolean = true, var help: Boolean = false
): Parcelable
