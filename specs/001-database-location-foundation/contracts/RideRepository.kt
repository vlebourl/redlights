package com.tiarkaerell.redlights.data.repository

import com.tiarkaerell.redlights.domain.model.Ride
import com.tiarkaerell.redlights.domain.model.RidePoint
import com.tiarkaerell.redlights.domain.model.Stop
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for Ride data operations
 * 
 * This interface defines the contract for accessing and manipulating ride data.
 * Implementations must handle mapping between database entities and domain models.
 * 
 * Threading: All suspend functions run on Dispatchers.IO
 * Error Handling: Throws exceptions for database errors (caught by use cases)
 */
interface RideRepository {
    
    // ==================== Ride CRUD Operations ====================
    
    /**
     * Create a new ride
     * @param startTime When the ride began
     * @return The created ride with generated ID
     */
    suspend fun createRide(startTime: Instant): Ride
    
    /**
     * Complete an active ride
     * @param rideId ID of the ride to complete
     * @param endTime When the ride ended
     * @return Updated ride with finalized metrics
     * @throws IllegalStateException if ride doesn't exist or is already completed
     */
    suspend fun completeRide(rideId: Long, endTime: Instant): Ride
    
    /**
     * Get a specific ride by ID
     * @param rideId The ride identifier
     * @return Ride if found, null otherwise
     */
    suspend fun getRideById(rideId: Long): Ride?
    
    /**
     * Get the currently active ride (endTime = null)
     * @return Active ride if exists, null if no ride is currently active
     */
    suspend fun getActiveRide(): Ride?
    
    /**
     * Get all completed rides, sorted by most recent first
     * @param limit Maximum number of rides to return (for pagination)
     * @param offset Number of rides to skip (for pagination)
     * @return Flow emitting list of rides (reactive - updates automatically)
     */
    fun getAllRides(limit: Int = 20, offset: Int = 0): Flow<List<Ride>>
    
    /**
     * Delete a ride and all associated data (cascade)
     * @param rideId ID of the ride to delete
     * @throws IllegalStateException if ride is currently active
     */
    suspend fun deleteRide(rideId: Long)
    
    /**
     * Delete all incomplete rides (endTime = null)
     * Called on app startup to clean up rides from crashes
     * @return Number of rides deleted
     */
    suspend fun deleteIncompleteRides(): Int
    
    // ==================== RidePoint Operations ====================
    
    /**
     * Add a GPS point to an active ride
     * @param rideId Parent ride ID
     * @param point The GPS point to store
     * @throws IllegalStateException if ride doesn't exist or is completed
     */
    suspend fun addRidePoint(rideId: Long, point: RidePoint)
    
    /**
     * Get all GPS points for a ride
     * @param rideId The ride identifier
     * @return List of ride points in chronological order
     */
    suspend fun getRidePoints(rideId: Long): List<RidePoint>
    
    /**
     * Get the last stored GPS point for a ride (for sampling comparison)
     * @param rideId The ride identifier
     * @return Most recent RidePoint, null if no points exist
     */
    suspend fun getLastRidePoint(rideId: Long): RidePoint?
    
    // ==================== Stop Operations ====================
    
    /**
     * Record a detected stop
     * @param rideId Parent ride ID
     * @param stop The stop to record
     * @throws IllegalStateException if ride doesn't exist or is completed
     */
    suspend fun addStop(rideId: Long, stop: Stop)
    
    /**
     * Get all stops for a ride
     * @param rideId The ride identifier
     * @return List of stops in chronological order
     */
    suspend fun getStops(rideId: Long): List<Stop>
    
    /**
     * Get the next sequence number for a stop in a ride
     * @param rideId The ride identifier
     * @return Next available sequence number (1 if no stops exist)
     */
    suspend fun getNextStopSequence(rideId: Long): Int
    
    // ==================== Ride Metrics ====================
    
    /**
     * Update ride metrics (distance, speed, stop count)
     * Called periodically during active ride
     * @param rideId The ride to update
     * @param totalDistance Cumulative distance in kilometers
     * @param averageSpeed Average moving speed in km/h
     * @param maxSpeed Maximum speed reached in km/h
     * @param stopCount Number of stops detected
     * @param totalStopTime Total time spent stopped in seconds
     */
    suspend fun updateRideMetrics(
        rideId: Long,
        totalDistance: Double,
        averageSpeed: Double,
        maxSpeed: Double,
        stopCount: Int,
        totalStopTime: Long
    )
    
    // ==================== Storage Management ====================
    
    /**
     * Get total storage space used by all ride data
     * @return Size in bytes
     */
    suspend fun getTotalStorageUsed(): Long
    
    /**
     * Get count of total rides in database
     * @return Number of rides (completed + active)
     */
    suspend fun getRideCount(): Int
}
