package com.tiarkaerell.redlights.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tiarkaerell.redlights.domain.model.Stop
import java.time.Instant

/**
 * Room database entity for Stop table
 *
 * Records periods when cyclist was stationary for >15 seconds
 *
 * State Machine:
 * MOVING → (speed <5 km/h) → POTENTIAL_STOP → (>15s, <10m) → CONFIRMED_STOP
 */
@Entity(
    tableName = "stops",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["ride_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ride_id"], name = "idx_stop_ride_id"),
        Index(value = ["latitude", "longitude"], name = "idx_stop_location")
    ]
)
data class StopEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "ride_id")
    val rideId: Long,

    @ColumnInfo(name = "latitude")
    val latitude: Double, // -90 to 90

    @ColumnInfo(name = "longitude")
    val longitude: Double, // -180 to 180

    @ColumnInfo(name = "duration")
    val duration: Long, // seconds (minimum 15)

    @ColumnInfo(name = "timestamp")
    val timestamp: Long, // Unix timestamp in milliseconds

    @ColumnInfo(name = "speed_before_stop")
    val speedBeforeStop: Float, // km/h

    @ColumnInfo(name = "sequence_number")
    val sequenceNumber: Int, // 1st, 2nd, 3rd stop in this ride

    @ColumnInfo(name = "bearing")
    val bearing: Float // 0-360 degrees
) {
    /**
     * Convert database entity to domain model
     */
    fun toDomainModel(): Stop {
        return Stop(
            id = id,
            rideId = rideId,
            latitude = latitude,
            longitude = longitude,
            duration = duration,
            timestamp = Instant.ofEpochMilli(timestamp),
            speedBeforeStop = speedBeforeStop,
            sequenceNumber = sequenceNumber,
            bearing = bearing
        )
    }

    companion object {
        /**
         * Create entity from domain model
         */
        fun fromDomainModel(stop: Stop): StopEntity {
            return StopEntity(
                id = stop.id,
                rideId = stop.rideId,
                latitude = stop.latitude,
                longitude = stop.longitude,
                duration = stop.duration,
                timestamp = stop.timestamp.toEpochMilli(),
                speedBeforeStop = stop.speedBeforeStop,
                sequenceNumber = stop.sequenceNumber,
                bearing = stop.bearing
            )
        }
    }
}
