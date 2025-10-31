package com.tiarkaerell.redlights.data.local.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Default implementation of LocationTracker using FusedLocationProviderClient
 *
 * This class provides high-accuracy GPS tracking with:
 * - 1-second update intervals (1 Hz)
 * - PRIORITY_HIGH_ACCURACY for best GPS performance
 * - Accuracy filtering to reject poor-quality locations (>50m by default)
 * - Haversine distance calculations for accurate measurements
 * - Bearing calculations for direction tracking
 *
 * Usage:
 * ```
 * val tracker: LocationTracker = DefaultLocationTracker(context, fusedClient)
 * if (tracker.hasLocationPermission() && tracker.isLocationEnabled()) {
 *     tracker.startLocationUpdates().collect { location ->
 *         // Process location update
 *     }
 * }
 * ```
 *
 * @param context Application context for permission and service checks
 * @param fusedLocationClient FusedLocationProviderClient for location updates
 */
class DefaultLocationTracker @Inject constructor(
    private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) : LocationTracker {

    /**
     * Current location callback - null when not tracking
     */
    private var locationCallback: LocationCallback? = null

    /**
     * Last received location - used for accuracy tracking
     */
    private var lastLocation: Location? = null

    /**
     * Tracking state flag
     */
    private var isCurrentlyTracking = false

    // ==================== Permission & Service Status ====================

    /**
     * Check if fine location permission is granted (T025)
     */
    override fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if location services are enabled (T025)
     */
    override fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Check if tracking is active
     */
    override fun isTracking(): Boolean {
        return isCurrentlyTracking
    }

    // ==================== Location Tracking ====================

    /**
     * Get last known location from FusedLocationProviderClient
     *
     * @return Last known location, null if unavailable or no permission
     * @throws SecurityException if location permission not granted
     */
    override suspend fun getLastKnownLocation(): Location? = suspendCancellableCoroutine { continuation ->
        if (!hasLocationPermission()) {
            throw SecurityException("Location permission not granted")
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                continuation.resume(location)
            }
            .addOnFailureListener { exception ->
                // If we can't get last location, just return null
                continuation.resume(null)
            }
    }

    /**
     * Start receiving location updates (T026, T027)
     *
     * Configures FusedLocationProviderClient with:
     * - Update interval: 1000ms (1 Hz)
     * - Priority: PRIORITY_HIGH_ACCURACY (best GPS accuracy)
     * - Min update interval: 500ms (fastest possible updates)
     * - Max wait time: 2000ms (batch updates if needed)
     *
     * Accuracy filtering (T027):
     * - Locations with accuracy > minAccuracyMeters are rejected
     * - Default: 50 meters
     *
     * @param minAccuracyMeters Reject updates worse than this accuracy
     * @return Flow emitting filtered location updates
     * @throws SecurityException if location permission not granted
     * @throws IllegalStateException if location services disabled
     */
    override fun startLocationUpdates(minAccuracyMeters: Float): Flow<Location> = callbackFlow {
        // Validate prerequisites
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        if (!isLocationEnabled()) {
            close(IllegalStateException("Location services are disabled"))
            return@callbackFlow
        }

        // Create location request with 1 Hz updates (T026)
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L // 1 second interval
        ).apply {
            setMinUpdateIntervalMillis(500L) // Fastest: 500ms
            setMaxUpdateDelayMillis(2000L) // Max wait: 2s
            setMinUpdateDistanceMeters(0f) // All updates (no distance filter)
            setWaitForAccurateLocation(false) // Don't wait for high accuracy
        }.build()

        // Create callback to receive location updates
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    // Accuracy filtering (T027): Reject poor-quality locations
                    if (location.hasAccuracy() && location.accuracy <= minAccuracyMeters) {
                        lastLocation = location
                        trySend(location)
                    }
                    // Note: Poor accuracy locations are silently rejected
                }
            }
        }

        // Store callback for cleanup
        locationCallback = callback
        isCurrentlyTracking = true

        // Request location updates
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            close(e)
            return@callbackFlow
        }

        // Cleanup when Flow is cancelled (T028)
        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
            isCurrentlyTracking = false
            lastLocation = null
        }
    }

    /**
     * Stop location updates and clean up resources (T028)
     */
    override suspend fun stopLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
            locationCallback = null
        }
        isCurrentlyTracking = false
        lastLocation = null
    }

    /**
     * Get current location accuracy
     */
    override suspend fun getCurrentAccuracy(): Float? {
        return lastLocation?.takeIf { it.hasAccuracy() }?.accuracy
    }

    // ==================== Distance & Bearing Calculations ====================

    /**
     * Calculate distance between two points using Haversine formula (T029)
     *
     * The Haversine formula calculates the great-circle distance between
     * two points on a sphere given their longitudes and latitudes.
     *
     * Formula:
     * a = sin²(Δlat/2) + cos(lat1) * cos(lat2) * sin²(Δlon/2)
     * c = 2 * atan2(√a, √(1−a))
     * d = R * c
     *
     * Where:
     * - R = Earth's radius (6371 km = 6,371,000 m)
     * - Δlat = lat2 - lat1
     * - Δlon = lon2 - lon1
     *
     * @param lat1 Latitude of first point in degrees
     * @param lon1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lon2 Longitude of second point in degrees
     * @return Distance in meters
     */
    override fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadiusMeters = 6371000.0 // Earth's radius in meters

        // Convert degrees to radians
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLatRad = Math.toRadians(lat2 - lat1)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        // Haversine formula
        val a = sin(deltaLatRad / 2).pow(2) +
                cos(lat1Rad) * cos(lat2Rad) *
                sin(deltaLonRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusMeters * c
    }

    /**
     * Calculate bearing (compass direction) between two points (T030)
     *
     * Bearing represents the forward azimuth from point 1 to point 2.
     * The result is normalized to 0-360 degrees where:
     * - 0° = North
     * - 90° = East
     * - 180° = South
     * - 270° = West
     *
     * Formula:
     * θ = atan2(sin(Δlon) * cos(lat2),
     *           cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(Δlon))
     *
     * @param lat1 Latitude of first point in degrees
     * @param lon1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lon2 Longitude of second point in degrees
     * @return Bearing in degrees (0-360)
     */
    override fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        // Convert degrees to radians
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLonRad = Math.toRadians(lon2 - lon1)

        // Calculate bearing
        val y = sin(deltaLonRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) -
                sin(lat1Rad) * cos(lat2Rad) * cos(deltaLonRad)
        val bearingRad = atan2(y, x)

        // Convert to degrees and normalize to 0-360
        val bearingDeg = Math.toDegrees(bearingRad)
        return (bearingDeg + 360) % 360
    }
}
