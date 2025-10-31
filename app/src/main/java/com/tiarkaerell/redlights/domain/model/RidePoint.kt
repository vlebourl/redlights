package com.tiarkaerell.redlights.domain.model

import java.time.Instant

/**
 * Domain model representing a GPS coordinate captured during a ride
 *
 * RidePoints are stored using smart sampling:
 * - Speed change >2 km/h
 * - Bearing change >15 degrees
 * - >30 seconds elapsed
 * - User in CONFIRMED_STOP state
 *
 * @property id Unique identifier
 * @property rideId Parent ride identifier
 * @property latitude Latitude coordinate (-90 to 90)
 * @property longitude Longitude coordinate (-180 to 180)
 * @property speed Speed at this point in km/h
 * @property timestamp When this point was captured
 * @property bearing Direction of travel in degrees (0 = North, 90 = East)
 * @property accuracy GPS accuracy in meters
 */
data class RidePoint(
    val id: Long,
    val rideId: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val timestamp: Instant,
    val bearing: Float,
    val accuracy: Float
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
        require(speed >= 0f) { "Speed cannot be negative" }
        require(bearing in 0f..360f) { "Bearing must be between 0 and 360 degrees" }
        require(accuracy > 0f && accuracy <= 50f) { "Accuracy must be positive and <= 50 meters" }
    }

    /**
     * Check if speed significantly changed from another point
     * @param other The point to compare with
     * @param threshold Speed change threshold in km/h (default: 2.0)
     * @return true if speed changed by more than threshold
     */
    fun hasSignificantSpeedChange(other: RidePoint, threshold: Float = 2.0f): Boolean {
        return kotlin.math.abs(speed - other.speed) > threshold
    }

    /**
     * Check if bearing significantly changed from another point
     * @param other The point to compare with
     * @param threshold Bearing change threshold in degrees (default: 15.0)
     * @return true if bearing changed by more than threshold
     */
    fun hasSignificantBearingChange(other: RidePoint, threshold: Float = 15.0f): Boolean {
        val diff = kotlin.math.abs(bearing - other.bearing)
        // Handle wraparound (e.g., 355° to 5° is 10° change, not 350°)
        val actualDiff = if (diff > 180f) 360f - diff else diff
        return actualDiff > threshold
    }
}
