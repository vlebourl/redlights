package com.tiarkaerell.redlights.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tiarkaerell.redlights.data.local.database.entities.RideEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Ride table
 *
 * Provides CRUD operations for rides with Room database
 */
@Dao
interface RideDao {

    /**
     * Insert a new ride
     * @return The ID of the inserted ride
     */
    @Insert
    suspend fun insert(ride: RideEntity): Long

    /**
     * Update an existing ride
     */
    @Update
    suspend fun update(ride: RideEntity)

    /**
     * Delete a ride (cascade deletes associated points and stops)
     */
    @Delete
    suspend fun delete(ride: RideEntity)

    /**
     * Get a ride by ID
     */
    @Query("SELECT * FROM rides WHERE id = :rideId")
    suspend fun getRideById(rideId: Long): RideEntity?

    /**
     * Get the currently active ride (endTime is null)
     */
    @Query("SELECT * FROM rides WHERE end_time IS NULL LIMIT 1")
    suspend fun getActiveRide(): RideEntity?

    /**
     * Get all rides sorted by start time (newest first) with pagination
     */
    @Query("SELECT * FROM rides ORDER BY start_time DESC LIMIT :limit OFFSET :offset")
    fun getAllRides(limit: Int, offset: Int): Flow<List<RideEntity>>

    /**
     * Get all completed rides (endTime is not null)
     */
    @Query("SELECT * FROM rides WHERE end_time IS NOT NULL ORDER BY start_time DESC")
    fun getCompletedRides(): Flow<List<RideEntity>>

    /**
     * Delete all incomplete rides (endTime is null)
     * Used for crash recovery on app startup
     * @return Number of rides deleted
     */
    @Query("DELETE FROM rides WHERE end_time IS NULL")
    suspend fun deleteIncompleteRides(): Int

    /**
     * Get total count of all rides
     */
    @Query("SELECT COUNT(*) FROM rides")
    suspend fun getRideCount(): Int

    /**
     * Get total count of completed rides
     */
    @Query("SELECT COUNT(*) FROM rides WHERE end_time IS NOT NULL")
    suspend fun getCompletedRideCount(): Int

    /**
     * Check if a ride exists
     */
    @Query("SELECT EXISTS(SELECT 1 FROM rides WHERE id = :rideId)")
    suspend fun rideExists(rideId: Long): Boolean
}
