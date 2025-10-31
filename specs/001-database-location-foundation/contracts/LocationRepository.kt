package com.tiarkaerell.redlights.data.repository

import android.location.Location
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for Location tracking operations
 *
 * This interface defines the contract for accessing device location services
 * and managing location tracking lifecycle. Implementations must handle
 * permission management, location service availability, and location updates.
 *
 * Threading: All operations run on appropriate dispatchers
 * Error Handling: Throws exceptions for permission/service errors (caught by use cases)
 */
interface LocationRepository {

    // ==================== Location Service Status ====================

    /**
     * Check if location services are enabled on the device
     * @return true if GPS/location services are enabled
     */
    suspend fun isLocationEnabled(): Boolean

    /**
     * Check if the app has fine location permission
     * @return true if ACCESS_FINE_LOCATION permission is granted
     */
    suspend fun hasLocationPermission(): Boolean

    /**
     * Get the last known location from the device
     * @return Last known Location, null if unavailable
     */
    suspend fun getLastKnownLocation(): Location?

    // ==================== Location Tracking ====================

    /**
     * Start receiving location updates
     * Updates delivered via the returned Flow at ~1 second intervals
     *
     * @param minAccuracyMeters Reject updates with accuracy worse than this (default: 50m)
     * @return Flow emitting high-accuracy location updates
     * @throws SecurityException if location permission not granted
     * @throws IllegalStateException if location services disabled
     */
    fun startLocationUpdates(minAccuracyMeters: Float = 50f): Flow<Location>

    /**
     * Stop receiving location updates
     * Cleans up location callbacks and releases resources
     */
    suspend fun stopLocationUpdates()

    /**
     * Check if location updates are currently active
     * @return true if location tracking is running
     */
    fun isTrackingLocation(): Boolean

    // ==================== Location Accuracy ====================

    /**
     * Get current location accuracy estimate
     * @return Accuracy in meters, null if not tracking or unavailable
     */
    suspend fun getCurrentAccuracy(): Float?

    /**
     * Calculate distance between two locations using Haversine formula
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in meters
     */
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double

    /**
     * Calculate bearing (direction) between two locations
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Bearing in degrees (0-360, 0=North, 90=East, 180=South, 270=West)
     */
    fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double
}
