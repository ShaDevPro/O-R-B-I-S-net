package com.sha.orbis.media

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.sha.orbis.R

object LocationGpsHelper {

    const val GPS_PREFIX = "[GPS:"
    const val GPS_SUFFIX = "]"

    fun hasLocationPermission(context: Context): Boolean {
        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return hasFine || hasCoarse
    }

    fun isLocationEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val gps = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
        val network = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }
        return gps || network
    }

    @SuppressLint("MissingPermission")
    fun getOfflineLocation(
        context: Context,
        onLocationResult: (latitude: Double, longitude: Double, accuracy: Float) -> Unit,
        onError: ((String) -> Unit)? = null
    ) {
        if (!hasLocationPermission(context)) {
            onError?.invoke(context.getString(R.string.media_gps_permission_denied))
            return
        }

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            onError?.invoke(context.getString(R.string.media_gps_unavailable))
            return
        }

        if (!isLocationEnabled(context)) {
            onError?.invoke(context.getString(R.string.media_gps_enable_settings_toast))
            return
        }

        // 1. Check all providers for best Last Known Location
        val lastLocations = mutableListOf<Location>()
        try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { lastLocations.add(it) } } catch (_: Exception) {}
        try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { lastLocations.add(it) } } catch (_: Exception) {}
        try { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)?.let { lastLocations.add(it) } } catch (_: Exception) {}

        val bestLast = lastLocations
            .filter { it.latitude != 0.0 && it.longitude != 0.0 }
            .minByOrNull { it.accuracy }

        val now = System.currentTimeMillis()
        // If last known location is fresh (< 5 minutes) and accurate (< 150m), use it directly
        if (bestLast != null && (now - bestLast.time) < 300_000L && bestLast.accuracy <= 150f) {
            onLocationResult(bestLast.latitude, bestLast.longitude, bestLast.accuracy)
            return
        }

        // 2. Request live single GPS / Network fix with Handler timeout safety
        var isDelivered = false
        val mainHandler = Handler(Looper.getMainLooper())

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!isDelivered && location.latitude != 0.0 && location.longitude != 0.0) {
                    isDelivered = true
                    try { lm.removeUpdates(this) } catch (_: Exception) {}
                    onLocationResult(location.latitude, location.longitude, location.accuracy)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // Timeout Runnable (8 seconds max wait for satellite/network lock)
        val timeoutRunnable = Runnable {
            if (!isDelivered) {
                isDelivered = true
                try { lm.removeUpdates(listener) } catch (_: Exception) {}
                if (bestLast != null) {
                    onLocationResult(bestLast.latitude, bestLast.longitude, bestLast.accuracy)
                } else {
                    onError?.invoke(context.getString(R.string.media_gps_unavailable))
                }
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 8000L)

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                lm.allProviders.contains(LocationManager.FUSED_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.FUSED_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
            }
        } catch (_: Exception) {
            if (!isDelivered) {
                isDelivered = true
                mainHandler.removeCallbacks(timeoutRunnable)
                if (bestLast != null) {
                    onLocationResult(bestLast.latitude, bestLast.longitude, bestLast.accuracy)
                } else {
                    onError?.invoke(context.getString(R.string.media_gps_unavailable))
                }
            }
        }
    }

    fun formatGpsPayload(lat: Double, lon: Double): String {
        return java.lang.String.format(java.util.Locale.US, "%s%.6f,%.6f%s", GPS_PREFIX, lat, lon, GPS_SUFFIX)
    }

    fun parseGpsPayload(text: String): Pair<Double, Double>? {
        if (!text.contains(GPS_PREFIX) || !text.contains(GPS_SUFFIX)) return null
        return try {
            val start = text.indexOf(GPS_PREFIX) + GPS_PREFIX.length
            val end = text.indexOf(GPS_SUFFIX, start)
            if (end <= start) return null
            val coordsStr = text.substring(start, end).trim()
            val parts = coordsStr.split(",")
            when {
                parts.size == 2 -> {
                    val lat = parts[0].trim().toDoubleOrNull()
                    val lon = parts[1].trim().toDoubleOrNull()
                    if (lat != null && lon != null) Pair(lat, lon) else null
                }
                parts.size == 4 -> {
                    // Rétrocompatibilité : gestion de l'ancien séparateur décimal à virgule (ex: "36,75250,3,04197")
                    val lat = "${parts[0].trim()}.${parts[1].trim()}".toDoubleOrNull()
                    val lon = "${parts[2].trim()}.${parts[3].trim()}".toDoubleOrNull()
                    if (lat != null && lon != null) Pair(lat, lon) else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun openInMaps(context: Context, lat: Double, lon: Double) {
        val latStr = java.lang.String.format(java.util.Locale.US, "%.6f", lat)
        val lonStr = java.lang.String.format(java.util.Locale.US, "%.6f", lon)
        try {
            val geoUri = Uri.parse("geo:0,0?q=$latStr,$lonStr(Position SOS Orbis)")
            val intent = Intent(Intent.ACTION_VIEW, geoUri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                val webUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latStr,$lonStr")
                val intent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {}
        }
    }
}
