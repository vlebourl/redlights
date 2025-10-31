package com.tiarkaerell.redlights.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tiarkaerell.redlights.data.local.database.entities.RidePointEntity

/**
 * Data Access Object for RidePoint table
 *
 * Provides operations for GPS points associated with rides
 */
@Dao
interface RidePointDao {

    /**
     * Insert a new ride point
     * @return The ID of the inserted point
     */
    @Insert
    suspend fun insert(point: RidePointEntity): Long

    /**
     * Insert multiple ride points (batch operation)
     */
    @Insert
    suspend fun insertAll(points: List<RidePointEntity>)

    /**
     * Get all points for a specific ride, ordered chronologically
     */
    @Query("SELECT * FROM ride_points WHERE ride_id = :rideId ORDER BY timestamp ASC")
    suspend fun getPointsForRide(rideId: Long): List<RidePointEntity>

    /**
     * Get the last (most recent) point for a ride
     * Used for smart sampling comparison
     */
    @Query("SELECT * FROM ride_points WHERE ride_id = :rideId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastPoint(rideId: Long): RidePointEntity?

    /**
     * Get count of points for a ride
     */
    @Query("SELECT COUNT(*) FROM ride_points WHERE ride_id = :rideId")
    suspend fun getPointCount(rideId: Long): Int

    /**
     * Get points within a time range for a ride
     */
    @Query("""
        SELECT * FROM ride_points
        WHERE ride_id = :rideId
        AND timestamp BETWEEN :startTime AND :endTime
        ORDER BY timestamp ASC
    """)
    suspend fun getPointsInTimeRange(
        rideId: Long,
        startTime: Long,
        endTime: Long
    ): List<RidePointEntity>

    /**
     * Delete all points for a specific ride
     * Note: Usually not needed due to CASCADE delete on foreign key
     */
    @Query("DELETE FROM ride_points WHERE ride_id = :rideId")
    suspend fun deletePointsForRide(rideId: Long): Int

    /**
     * Get total count of all ride points across all rides
     */
    @Query("SELECT COUNT(*) FROM ride_points")
    suspend fun getTotalPointCount(): Int
}
