package com.tiarkaerell.redlights.domain.model

import java.time.Instant

/**
 * Domain model representing an aggregation of stops at approximately the same location
 *
 * Clustering Algorithm: DBSCAN-inspired
 * - Clustering radius: 10 meters
 * - Centroid: Weighted average of all stop locations
 * - Updated incrementally as new stops are added
 *
 * @property id Unique identifier
 * @property centroidLatitude Center point latitude (-90 to 90)
 * @property centroidLongitude Center point longitude (-180 to 180)
 * @property averageDuration Average stop duration in seconds
 * @property medianDuration Median stop duration in seconds
 * @property stopCount Number of stops in this cluster
 * @property stopIds List of stop IDs belonging to this cluster
 * @property lastUpdated Timestamp of last update
 */
data class StopCluster(
    val id: Long,
    val centroidLatitude: Double,
    val centroidLongitude: Double,
    val averageDuration: Double,
    val medianDuration: Long,
    val stopCount: Int,
    val stopIds: List<Long>,
    val lastUpdated: Instant
) {
    init {
        require(centroidLatitude in -90.0..90.0) { "Centroid latitude must be between -90 and 90 degrees" }
        require(centroidLongitude in -180.0..180.0) { "Centroid longitude must be between -180 and 180 degrees" }
        require(averageDuration >= 0.0) { "Average duration cannot be negative" }
        require(medianDuration >= 0) { "Median duration cannot be negative" }
        require(stopCount >= 1) { "Stop count must be at least 1" }
        require(stopIds.isNotEmpty()) { "Stop IDs list cannot be empty" }
        require(stopCount == stopIds.size) { "Stop count must match number of stop IDs" }
    }

    /**
     * Check if this cluster is a "problem intersection" (many stops or long waits)
     * @param minStops Minimum stops to be considered problematic (default: 5)
     * @param minAvgDuration Minimum average duration in seconds (default: 60)
     * @return true if cluster meets problem criteria
     */
    fun isProblemIntersection(minStops: Int = 5, minAvgDuration: Double = 60.0): Boolean {
        return stopCount >= minStops || averageDuration >= minAvgDuration
    }

    /**
     * Format average duration as human-readable string
     * Examples: "45s", "2m 15s"
     */
    fun formatAverageDuration(): String {
        val avgSeconds = averageDuration.toLong()
        return when {
            avgSeconds < 60 -> "${avgSeconds}s"
            avgSeconds < 3600 -> {
                val minutes = avgSeconds / 60
                val seconds = avgSeconds % 60
                if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
            }
            else -> {
                val hours = avgSeconds / 3600
                val minutes = (avgSeconds % 3600) / 60
                if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
            }
        }
    }

    /**
     * Format median duration as human-readable string
     */
    fun formatMedianDuration(): String {
        return when {
            medianDuration < 60 -> "${medianDuration}s"
            medianDuration < 3600 -> {
                val minutes = medianDuration / 60
                val seconds = medianDuration % 60
                if (seconds == 0L) "${minutes}m" else "${minutes}m ${seconds}s"
            }
            else -> {
                val hours = medianDuration / 3600
                val minutes = (medianDuration % 3600) / 60
                if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
            }
        }
    }
}
