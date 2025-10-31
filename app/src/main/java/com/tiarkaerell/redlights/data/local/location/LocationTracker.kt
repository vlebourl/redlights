package com.tiarkaerell.redlights.data.local.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

/**
 * Low-level interface for GPS location tracking
 *
 * This interface defines the contract for interacting with Android's
 * FusedLocationProviderClient. Implementations handle permission checking,
 * location service status, and coordinate location update callbacks.
 *
 * Note: This is an internal data layer interface. The domain layer
 * accesses location services through LocationRepository which delegates
 * to this interface.
 *
 * Threading: All operations use appropriate coroutine dispatchers
 * Error Handling: Throws exceptions for permission/service errors
 */
interface LocationTracker {

    // ==================== Permission & Service Status ====================

    /**
     * Check if the app has fine location permission
     * @return true if ACCESS_FINE_LOCATION permission is granted
     */
    fun hasLocationPermission(): Boolean

    /**
     * Check if location services are enabled on the device
     * @return true if GPS/location services are enabled
     */
    fun isLocationEnabled(): Boolean

    /**
     * Check if location tracking is currently active
     * @return true if location updates are being received
     */
    fun isTracking(): Boolean

    // ==================== Location Tracking ====================

    /**
     * Get the last known location from FusedLocationProviderClient
     * @return Last known Location, null if unavailable
     * @throws SecurityException if location permission not granted
     */
    suspend fun getLastKnownLocation(): Location?

    /**
     * Start receiving location updates from FusedLocationProviderClient
     *
     * Configuration:
     * - Update interval: 1 second (1000ms)
     * - Priority: PRIORITY_HIGH_ACCURACY
     * - Smallest displacement: 0 meters (all updates)
     *
     * Accuracy filtering:
     * - Updates with accuracy > minAccuracyMeters are rejected
     * - Default threshold: 50 meters
     *
     * @param minAccuracyMeters Reject updates with accuracy worse than this
     * @return Flow emitting location updates at ~1 Hz
     * @throws SecurityException if location permission not granted
     * @throws IllegalStateException if location services disabled
     */
    fun startLocationUpdates(minAccuracyMeters: Float = 50f): Flow<Location>

    /**
     * Stop receiving location updates
     * Removes location callbacks and releases FusedLocationProviderClient resources
     */
    suspend fun stopLocationUpdates()

    /**
     * Get current location accuracy estimate
     * @return Accuracy in meters, null if not tracking or unavailable
     */
    suspend fun getCurrentAccuracy(): Float?

    // ==================== Distance & Bearing Calculations ====================

    /**
     * Calculate distance between two points using Haversine formula
     *
     * The Haversine formula accounts for the spherical shape of the Earth,
     * providing accurate distance calculations for lat/lon coordinates.
     *
     * @param lat1 Latitude of first point in degrees
     * @param lon1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lon2 Longitude of second point in degrees
     * @return Distance in meters
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double

    /**
     * Calculate bearing (compass direction) between two points
     *
     * Bearing represents the direction from point 1 to point 2.
     * - 0° = North
     * - 90° = East
     * - 180° = South
     * - 270° = West
     *
     * @param lat1 Latitude of first point in degrees
     * @param lon1 Longitude of first point in degrees
     * @param lat2 Latitude of second point in degrees
     * @param lon2 Longitude of second point in degrees
     * @return Bearing in degrees (0-360)
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
}
