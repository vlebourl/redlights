package com.tiarkaerell.redlights.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tiarkaerell.redlights.data.local.database.entities.StopEntity

/**
 * Data Access Object for Stop table
 *
 * Provides operations for stop events associated with rides
 */
@Dao
interface StopDao {

    /**
     * Insert a new stop
     * @return The ID of the inserted stop
     */
    @Insert
    suspend fun insert(stop: StopEntity): Long

    /**
     * Insert multiple stops (batch operation)
     */
    @Insert
    suspend fun insertAll(stops: List<StopEntity>)

    /**
     * Get all stops for a specific ride, ordered by sequence number
     */
    @Query("SELECT * FROM stops WHERE ride_id = :rideId ORDER BY sequence_number ASC")
    suspend fun getStopsForRide(rideId: Long): List<StopEntity>

    /**
     * Get a specific stop by ID
     */
    @Query("SELECT * FROM stops WHERE id = :stopId")
    suspend fun getStopById(stopId: Long): StopEntity?

    /**
     * Get the next sequence number for a ride
     * Used when creating a new stop
     */
    @Query("SELECT COALESCE(MAX(sequence_number), 0) + 1 FROM stops WHERE ride_id = :rideId")
    suspend fun getNextSequenceNumber(rideId: Long): Int

    /**
     * Get count of stops for a ride
     */
    @Query("SELECT COUNT(*) FROM stops WHERE ride_id = :rideId")
    suspend fun getStopCount(rideId: Long): Int

    /**
     * Find stops near a specific location (for clustering)
     * Uses simple bounding box for initial filtering
     *
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param latDelta Latitude range (~0.0001 degrees ≈ 11 meters)
     * @param lonDelta Longitude range (~0.0001 degrees ≈ 11 meters at equator)
     */
    @Query("""
        SELECT * FROM stops
        WHERE latitude BETWEEN :latitude - :latDelta AND :latitude + :latDelta
        AND longitude BETWEEN :longitude - :lonDelta AND :longitude + :lonDelta
    """)
    suspend fun getStopsNearLocation(
        latitude: Double,
        longitude: Double,
        latDelta: Double = 0.0001,
        lonDelta: Double = 0.0001
    ): List<StopEntity>

    /**
     * Get all stops (for clustering all stops at once)
     */
    @Query("SELECT * FROM stops ORDER BY timestamp ASC")
    suspend fun getAllStops(): List<StopEntity>

    /**
     * Get stops by multiple IDs (for cluster analysis)
     */
    @Query("SELECT * FROM stops WHERE id IN (:stopIds)")
    suspend fun getStopsByIds(stopIds: List<Long>): List<StopEntity>

    /**
     * Delete all stops for a specific ride
     * Note: Usually not needed due to CASCADE delete on foreign key
     */
    @Query("DELETE FROM stops WHERE ride_id = :rideId")
    suspend fun deleteStopsForRide(rideId: Long): Int

    /**
     * Get total count of all stops across all rides
     */
    @Query("SELECT COUNT(*) FROM stops")
    suspend fun getTotalStopCount(): Int
}
