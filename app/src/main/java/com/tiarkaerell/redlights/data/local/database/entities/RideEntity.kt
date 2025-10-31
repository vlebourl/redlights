package com.tiarkaerell.redlights.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tiarkaerell.redlights.domain.model.Ride
import java.time.Instant

/**
 * Room database entity for Ride table
 *
 * Represents a complete cycling session with summary metrics
 *
 * Lifecycle: NEW → ACTIVE → COMPLETED → PERSISTED
 * Business Rule: Only ONE ride can have endTime = null at any time
 */
@Entity(
    tableName = "rides",
    indices = [
        Index(value = ["start_time"], name = "idx_ride_start_time")
    ]
)
data class RideEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "start_time")
    val startTime: Long, // Unix timestamp in milliseconds

    @ColumnInfo(name = "end_time")
    val endTime: Long?, // null if ride still active

    @ColumnInfo(name = "total_distance")
    val totalDistance: Double = 0.0, // kilometers

    @ColumnInfo(name = "total_stop_time")
    val totalStopTime: Long = 0, // seconds

    @ColumnInfo(name = "stop_count")
    val stopCount: Int = 0,

    @ColumnInfo(name = "average_speed")
    val averageSpeed: Double = 0.0, // km/h

    @ColumnInfo(name = "max_speed")
    val maxSpeed: Double = 0.0 // km/h
) {
    /**
     * Convert database entity to domain model
     */
    fun toDomainModel(): Ride {
        return Ride(
            id = id,
            startTime = Instant.ofEpochMilli(startTime),
            endTime = endTime?.let { Instant.ofEpochMilli(it) },
            totalDistance = totalDistance,
            totalStopTime = totalStopTime,
            stopCount = stopCount,
            averageSpeed = averageSpeed,
            maxSpeed = maxSpeed
        )
    }

    companion object {
        /**
         * Create entity from domain model
         */
        fun fromDomainModel(ride: Ride): RideEntity {
            return RideEntity(
                id = ride.id,
                startTime = ride.startTime.toEpochMilli(),
                endTime = ride.endTime?.toEpochMilli(),
                totalDistance = ride.totalDistance,
                totalStopTime = ride.totalStopTime,
                stopCount = ride.stopCount,
                averageSpeed = ride.averageSpeed,
                maxSpeed = ride.maxSpeed
            )
        }
    }
}
