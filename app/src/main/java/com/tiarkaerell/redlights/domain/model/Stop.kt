package com.tiarkaerell.redlights.domain.model

import java.time.Instant

/**
 * Domain model representing a period when cyclist was stationary for >15 seconds
 *
 * Stop Detection State Machine:
 * MOVING → (speed <5 km/h) → POTENTIAL_STOP → (>15s, <10m tolerance) → CONFIRMED_STOP
 *
 * @property id Unique identifier
 * @property rideId Parent ride identifier
 * @property latitude Latitude of stop location (-90 to 90)
 * @property longitude Longitude of stop location (-180 to 180)
 * @property duration Duration of stop in seconds (minimum 15)
 * @property timestamp When the stop began
 * @property speedBeforeStop Speed 5 seconds before stopping (km/h)
 * @property sequenceNumber Stop number within this ride (1st, 2nd, 3rd...)
 * @property bearing Direction cyclist was traveling before stop (0-360 degrees)
 */
data class Stop(
    val id: Long,
    val rideId: Long,
    val latitude: Double,
    val longitude: Double,
    val duration: Long,
    val timestamp: Instant,
    val speedBeforeStop: Float,
    val sequenceNumber: Int,
    val bearing: Float
) {
    init {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
        require(duration >= 15) { "Duration must be at least 15 seconds (detection threshold)" }
        require(speedBeforeStop >= 0f) { "Speed before stop cannot be negative" }
        require(sequenceNumber >= 1) { "Sequence number must start at 1" }
        require(bearing in 0f..360f) { "Bearing must be between 0 and 360 degrees" }
    }

    /**
     * Format duration as human-readable string
     * Examples: "45s", "2m 15s", "5m 30s"
     */
    fun formatDuration(): String {
        return when {
            duration < 60 -> "${duration}s"
            duration < 3600 -> {
                val minutes = duration / 60
                val seconds = duration % 60
                if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
            }
            else -> {
                val hours = duration / 3600
                val minutes = (duration % 3600) / 60
                if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
            }
        }
    }
}
