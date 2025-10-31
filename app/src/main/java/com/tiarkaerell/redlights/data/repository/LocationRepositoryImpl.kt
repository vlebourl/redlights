package com.tiarkaerell.redlights.data.repository

import android.location.Location
import com.tiarkaerell.redlights.data.local.location.LocationTracker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Implementation of LocationRepository delegating to LocationTracker
 *
 * This repository acts as a simple adapter between the domain layer
 * (LocationRepository) and the data layer (LocationTracker).
 * It provides a clean separation where:
 * - LocationRepository: Domain-facing contract
 * - LocationTracker: Android-specific GPS implementation
 *
 * All location operations are delegated directly to the injected
 * LocationTracker implementation (DefaultLocationTracker).
 *
 * Threading: Operations use dispatchers from LocationTracker
 * Error Handling: Exceptions propagate from LocationTracker
 *
 * @param locationTracker The GPS tracking implementation to delegate to
 */
class LocationRepositoryImpl @Inject constructor(
    private val locationTracker: LocationTracker
) : LocationRepository {

    // ==================== Location Service Status ====================

    /**
     * Check if location services are enabled (T052)
     * Delegates to LocationTracker
     */
    override suspend fun isLocationEnabled(): Boolean {
        return locationTracker.isLocationEnabled()
    }

    /**
     * Check if location permission is granted (T052)
     * Delegates to LocationTracker
     */
    override suspend fun hasLocationPermission(): Boolean {
        return locationTracker.hasLocationPermission()
    }

    /**
     * Get last known location (T052)
     * Delegates to LocationTracker
     */
    override suspend fun getLastKnownLocation(): Location? {
        return locationTracker.getLastKnownLocation()
    }

    // ==================== Location Tracking ====================

    /**
     * Start location updates (T052)
     *
     * Delegates to LocationTracker which handles:
     * - FusedLocationProviderClient configuration
     * - 1 Hz update frequency
     * - Accuracy filtering
     * - Flow-based location streaming
     *
     * @param minAccuracyMeters Reject updates worse than this accuracy
     * @return Flow emitting location updates
     * @throws SecurityException if permission not granted
     * @throws IllegalStateException if location services disabled
     */
    override fun startLocationUpdates(minAccuracyMeters: Float): Flow<Location> {
        return locationTracker.startLocationUpdates(minAccuracyMeters)
    }

    /**
     * Stop location updates (T052)
     * Delegates to LocationTracker
     */
    override suspend fun stopLocationUpdates() {
        locationTracker.stopLocationUpdates()
    }

    /**
     * Check if currently tracking (T052)
     * Delegates to LocationTracker
     */
    override fun isTrackingLocation(): Boolean {
        return locationTracker.isTracking()
    }

    // ==================== Location Accuracy ====================

    /**
     * Get current accuracy estimate (T052)
     * Delegates to LocationTracker
     */
    override suspend fun getCurrentAccuracy(): Float? {
        return locationTracker.getCurrentAccuracy()
    }

    /**
     * Calculate distance using Haversine formula (T052)
     * Delegates to LocationTracker
     *
     * @return Distance in meters
     */
    override fun calculateDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        return locationTracker.calculateDistance(lat1, lon1, lat2, lon2)
    }

    /**
     * Calculate bearing between two points (T052)
     * Delegates to LocationTracker
     *
     * @return Bearing in degrees (0-360)
     */
    override fun calculateBearing(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        return locationTracker.calculateBearing(lat1, lon1, lat2, lon2)
    }
}
