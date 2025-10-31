package com.tiarkaerell.redlights.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tiarkaerell.redlights.domain.model.RidePoint
import java.time.Instant

/**
 * Room database entity for RidePoint table
 *
 * Stores GPS coordinates captured during a ride (smart sampled)
 *
 * Sampling Rules:
 * - Speed change >2 km/h
 * - Bearing change >15 degrees
 * - >30 seconds elapsed
 * - User in CONFIRMED_STOP state
 */
@Entity(
    tableName = "ride_points",
    foreignKeys = [
        ForeignKey(
            entity = RideEntity::class,
            parentColumns = ["id"],
            childColumns = ["ride_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ride_id"], name = "idx_ride_point_ride_id"),
        Index(value = ["timestamp"], name = "idx_ride_point_timestamp")
    ]
)
data class RidePointEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "ride_id")
    val rideId: Long,

    @ColumnInfo(name = "latitude")
    val latitude: Double, // -90 to 90

    @ColumnInfo(name = "longitude")
    val longitude: Double, // -180 to 180

    @ColumnInfo(name = "speed")
    val speed: Float, // km/h

    @ColumnInfo(name = "timestamp")
    val timestamp: Long, // Unix timestamp in milliseconds

    @ColumnInfo(name = "bearing")
    val bearing: Float, // 0-360 degrees (0 = North)

    @ColumnInfo(name = "accuracy")
    val accuracy: Float // meters (filtered to <= 50m)
) {
    /**
     * Convert database entity to domain model
     */
    fun toDomainModel(): RidePoint {
        return RidePoint(
            id = id,
            rideId = rideId,
            latitude = latitude,
            longitude = longitude,
            speed = speed,
            timestamp = Instant.ofEpochMilli(timestamp),
            bearing = bearing,
            accuracy = accuracy
        )
    }

    companion object {
        /**
         * Create entity from domain model
         */
        fun fromDomainModel(point: RidePoint): RidePointEntity {
            return RidePointEntity(
                id = point.id,
                rideId = point.rideId,
                latitude = point.latitude,
                longitude = point.longitude,
                speed = point.speed,
                timestamp = point.timestamp.toEpochMilli(),
                bearing = point.bearing,
                accuracy = point.accuracy
            )
        }
    }
}
