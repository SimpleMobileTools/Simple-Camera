package com.simplemobiletools.camera.helpers

import android.Manifest
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.annotation.RequiresPermission
import com.simplemobiletools.camera.extensions.checkLocationPermission
import com.simplemobiletools.commons.activities.BaseSimpleActivity

class SimpleLocationManager(private val activity: BaseSimpleActivity) {

    companion object {
        private const val LOCATION_UPDATE_MIN_TIME_INTERVAL_MS = 5000L
        private const val LOCATION_UPDATE_MIN_DISTANCE_M = 10F
    }

    private val locationManager = activity.getSystemService(LocationManager::class.java)!!
    private val locationListener = LocationListener { location ->
        this@SimpleLocationManager.location = location
    }

    private var location: Location? = null

    fun getLocation(): Location? {
        if (location == null) {
            location = getLastKnownLocation()
        }

        return location
    }

    private fun getLastKnownLocation(): Location? {
        return if (activity.checkLocationPermission()) {
            var accurateLocation: Location? = null
            for (provider in locationManager.allProviders) {
                val location = locationManager.getLastKnownLocation(provider) ?: continue
                if (accurateLocation == null || location.accuracy < accurateLocation.accuracy) {
                    accurateLocation = location
                }
            }
            accurateLocation
        } else {
            null
        }
    }

    @RequiresPermission(anyOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun requestLocationUpdates() {
        locationManager.allProviders.forEach { provider ->
            locationManager.requestLocationUpdates(
                provider,
                LOCATION_UPDATE_MIN_TIME_INTERVAL_MS,
                LOCATION_UPDATE_MIN_DISTANCE_M,
                locationListener
            )
        }
    }

    fun dropLocationUpdates() {
        locationManager.removeUpdates(locationListener)
    }
}
