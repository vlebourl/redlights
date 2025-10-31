package com.tiarkaerell.redlights.domain.model

import java.time.Instant

/**
 * Domain model representing a complete cycling session
 *
 * Lifecycle: NEW → ACTIVE → COMPLETED → PERSISTED
 *
 * @property id Unique identifier
 * @property startTime When the ride began
 * @property endTime When the ride ended (null if still active)
 * @property totalDistance Total distance traveled in kilometers
 * @property totalStopTime Total time spent stopped in seconds
 * @property stopCount Number of stops detected during ride
 * @property averageSpeed Average speed in km/h (excluding stops)
 * @property maxSpeed Maximum speed reached in km/h
 */
data class Ride(
    val id: Long,
    val startTime: Instant,
    val endTime: Instant?,
    val totalDistance: Double,
    val totalStopTime: Long,
    val stopCount: Int,
    val averageSpeed: Double,
    val maxSpeed: Double
) {
    /**
     * Check if ride is currently active (not yet completed)
     */
    val isActive: Boolean
        get() = endTime == null

    /**
     * Calculate total ride duration in seconds
     * Returns null if ride is still active
     */
    val durationSeconds: Long?
        get() = endTime?.let {
            (it.epochSecond - startTime.epochSecond)
        }

    /**
     * Calculate moving time (duration - stopped time) in seconds
     * Returns null if ride is still active
     */
    val movingTimeSeconds: Long?
        get() = durationSeconds?.let { it - totalStopTime }

    init {
        require(totalDistance >= 0) { "Total distance cannot be negative" }
        require(totalStopTime >= 0) { "Total stop time cannot be negative" }
        require(stopCount >= 0) { "Stop count cannot be negative" }
        require(averageSpeed >= 0) { "Average speed cannot be negative" }
        require(maxSpeed >= 0) { "Max speed cannot be negative" }
        endTime?.let {
            require(it.isAfter(startTime)) { "End time must be after start time" }
        }
    }
}
