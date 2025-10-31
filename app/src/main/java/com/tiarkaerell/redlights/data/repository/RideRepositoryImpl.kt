package com.tiarkaerell.redlights.data.repository

import com.tiarkaerell.redlights.data.local.database.dao.RideDao
import com.tiarkaerell.redlights.data.local.database.dao.RidePointDao
import com.tiarkaerell.redlights.data.local.database.dao.StopDao
import com.tiarkaerell.redlights.data.local.database.entities.RideEntity
import com.tiarkaerell.redlights.data.local.database.entities.RidePointEntity
import com.tiarkaerell.redlights.data.local.database.entities.StopEntity
import com.tiarkaerell.redlights.domain.model.Ride
import com.tiarkaerell.redlights.domain.model.RidePoint
import com.tiarkaerell.redlights.domain.model.Stop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * Implementation of RideRepository bridging Room DAOs and domain models
 *
 * This repository:
 * - Converts between database entities and domain models
 * - Manages ride lifecycle (create, update, complete, delete)
 * - Stores ride points with smart sampling
 * - Records stops during rides
 * - Calculates and updates ride metrics
 * - Provides reactive data access via Flow
 *
 * Threading: All operations run on Dispatchers.IO via Room
 * Error Handling: Throws exceptions for invalid operations (caught by use cases)
 *
 * @param rideDao DAO for ride table operations
 * @param ridePointDao DAO for ride point table operations
 * @param stopDao DAO for stop table operations
 */
class RideRepositoryImpl @Inject constructor(
    private val rideDao: RideDao,
    private val ridePointDao: RidePointDao,
    private val stopDao: StopDao
) : RideRepository {

    // ==================== Ride CRUD Operations ====================

    /**
     * Create a new ride (T041)
     *
     * Creates a new active ride in the database with initial values.
     * The ride starts with zero metrics (distance, stops, etc.) which
     * will be updated as GPS points are collected.
     *
     * @param startTime When the ride began
     * @return The created ride with generated ID
     */
    override suspend fun createRide(startTime: Instant): Ride {
        val entity = RideEntity(
            id = 0, // Auto-generated
            startTime = startTime.toEpochMilli(),
            endTime = null, // Active ride
            totalDistance = 0.0,
            totalStopTime = 0,
            stopCount = 0,
            averageSpeed = 0.0,
            maxSpeed = 0.0
        )

        val rideId = rideDao.insert(entity)
        return entity.copy(id = rideId).toDomainModel()
    }

    /**
     * Complete an active ride (T042)
     *
     * Finalizes a ride by setting end time and ensuring metrics are correct.
     * After completion, no more points or stops can be added.
     *
     * @param rideId ID of the ride to complete
     * @param endTime When the ride ended
     * @return Updated ride with finalized metrics
     * @throws IllegalStateException if ride doesn't exist or is already completed
     */
    override suspend fun completeRide(rideId: Long, endTime: Instant): Ride {
        val entity = rideDao.getRideById(rideId)
            ?: throw IllegalStateException("Ride $rideId does not exist")

        if (entity.endTime != null) {
            throw IllegalStateException("Ride $rideId is already completed")
        }

        // Update ride with end time
        val completedEntity = entity.copy(endTime = endTime.toEpochMilli())
        rideDao.update(completedEntity)

        return completedEntity.toDomainModel()
    }

    /**
     * Get a specific ride by ID (T043)
     */
    override suspend fun getRideById(rideId: Long): Ride? {
        return rideDao.getRideById(rideId)?.toDomainModel()
    }

    /**
     * Get the currently active ride (T043)
     *
     * @return Active ride if exists, null if no ride is currently active
     */
    override suspend fun getActiveRide(): Ride? {
        return rideDao.getActiveRide()?.toDomainModel()
    }

    /**
     * Get all completed rides with reactive updates (T044)
     *
     * Returns a Flow that emits updated lists whenever the database changes.
     * Results are sorted by start time (most recent first) and support pagination.
     *
     * @param limit Maximum number of rides to return
     * @param offset Number of rides to skip
     * @return Flow emitting list of rides
     */
    override fun getAllRides(limit: Int, offset: Int): Flow<List<Ride>> {
        return rideDao.getAllRides(limit, offset)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    /**
     * Delete a ride and all associated data (T045)
     *
     * Cascade deletion automatically removes:
     * - All ride points (foreign key constraint)
     * - All stops (foreign key constraint)
     *
     * @param rideId ID of the ride to delete
     * @throws IllegalStateException if ride is currently active
     */
    override suspend fun deleteRide(rideId: Long) {
        val entity = rideDao.getRideById(rideId)
            ?: throw IllegalStateException("Ride $rideId does not exist")

        if (entity.endTime == null) {
            throw IllegalStateException("Cannot delete active ride $rideId")
        }

        rideDao.delete(entity)
        // Note: Cascade delete automatically removes ride points and stops
    }

    /**
     * Delete all incomplete rides for crash recovery (T046)
     *
     * Called on app startup to clean up rides that weren't properly
     * completed due to app crash or force close.
     *
     * @return Number of rides deleted
     */
    override suspend fun deleteIncompleteRides(): Int {
        return rideDao.deleteIncompleteRides()
    }

    // ==================== RidePoint Operations ====================

    /**
     * Add a GPS point to an active ride (T047)
     *
     * Stores a sampled GPS point. Smart sampling logic in LocationTrackerService
     * ensures only significant points are stored (80-95% reduction).
     *
     * @param rideId Parent ride ID
     * @param point The GPS point to store
     * @throws IllegalStateException if ride doesn't exist or is completed
     */
    override suspend fun addRidePoint(rideId: Long, point: RidePoint) {
        // Verify ride exists and is active
        val ride = rideDao.getRideById(rideId)
            ?: throw IllegalStateException("Ride $rideId does not exist")

        if (ride.endTime != null) {
            throw IllegalStateException("Cannot add point to completed ride $rideId")
        }

        // Convert and store
        val entity = RidePointEntity.fromDomainModel(point.copy(rideId = rideId))
        ridePointDao.insert(entity)
    }

    /**
     * Get all GPS points for a ride (T047)
     *
     * @return List of ride points in chronological order
     */
    override suspend fun getRidePoints(rideId: Long): List<RidePoint> {
        return ridePointDao.getPointsForRide(rideId)
            .map { it.toDomainModel() }
    }

    /**
     * Get the last stored GPS point (T047)
     *
     * Used by LocationTrackerService for smart sampling comparison.
     *
     * @return Most recent RidePoint, null if no points exist
     */
    override suspend fun getLastRidePoint(rideId: Long): RidePoint? {
        return ridePointDao.getLastPoint(rideId)?.toDomainModel()
    }

    // ==================== Stop Operations ====================

    /**
     * Record a detected stop (T048)
     *
     * Stores a stop detected by the LocationTrackerService state machine.
     * Stops are tracked with sequence numbers for chronological ordering.
     *
     * @param rideId Parent ride ID
     * @param stop The stop to record
     * @throws IllegalStateException if ride doesn't exist or is completed
     */
    override suspend fun addStop(rideId: Long, stop: Stop) {
        // Verify ride exists and is active
        val ride = rideDao.getRideById(rideId)
            ?: throw IllegalStateException("Ride $rideId does not exist")

        if (ride.endTime != null) {
            throw IllegalStateException("Cannot add stop to completed ride $rideId")
        }

        // Convert and store
        val entity = StopEntity.fromDomainModel(stop.copy(rideId = rideId))
        stopDao.insert(entity)
    }

    /**
     * Get all stops for a ride (T048)
     *
     * @return List of stops in chronological order (by sequence number)
     */
    override suspend fun getStops(rideId: Long): List<Stop> {
        return stopDao.getStopsForRide(rideId)
            .map { it.toDomainModel() }
    }

    /**
     * Get the next sequence number for a stop (T048)
     *
     * Sequence numbers ensure stops are ordered correctly even if
     * timestamps are similar or identical.
     *
     * @return Next available sequence number (1 if no stops exist)
     */
    override suspend fun getNextStopSequence(rideId: Long): Int {
        return stopDao.getNextSequenceNumber(rideId)
    }

    // ==================== Ride Metrics ====================

    /**
     * Update ride metrics during active tracking (T049)
     *
     * Called by LocationTrackerService to update:
     * - Total distance traveled
     * - Average and maximum speed
     * - Stop count and total stop time
     *
     * Metrics accumulate over the ride duration and are finalized
     * when the ride is completed.
     *
     * @param rideId The ride to update
     * @param totalDistance Cumulative distance in kilometers
     * @param averageSpeed Average moving speed in km/h
     * @param maxSpeed Maximum speed reached in km/h
     * @param stopCount Number of stops detected
     * @param totalStopTime Total time spent stopped in seconds
     */
    override suspend fun updateRideMetrics(
        rideId: Long,
        totalDistance: Double,
        averageSpeed: Double,
        maxSpeed: Double,
        stopCount: Int,
        totalStopTime: Long
    ) {
        val entity = rideDao.getRideById(rideId)
            ?: throw IllegalStateException("Ride $rideId does not exist")

        val updatedEntity = entity.copy(
            totalDistance = totalDistance,
            averageSpeed = averageSpeed,
            maxSpeed = maxSpeed,
            stopCount = stopCount,
            totalStopTime = totalStopTime
        )

        rideDao.update(updatedEntity)
    }

    // ==================== Storage Management ====================

    /**
     * Get total storage space used by all ride data (T050)
     *
     * Estimates storage based on:
     * - Number of ride records
     * - Number of ride points
     * - Number of stops
     * - Database overhead
     *
     * This is an approximation for the low storage warning feature.
     *
     * @return Size in bytes (estimated)
     */
    override suspend fun getTotalStorageUsed(): Long {
        val rideCount = rideDao.getRideCount()
        val pointCount = ridePointDao.getTotalPointCount()
        val stopCount = stopDao.getTotalStopCount()

        // Rough estimates (actual size varies with indexes, metadata, etc.)
        val bytesPerRide = 128L // RideEntity size
        val bytesPerPoint = 64L // RidePointEntity size
        val bytesPerStop = 96L // StopEntity size
        val databaseOverhead = 1024L * 100 // 100 KB overhead

        return (rideCount * bytesPerRide) +
                (pointCount * bytesPerPoint) +
                (stopCount * bytesPerStop) +
                databaseOverhead
    }

    /**
     * Get count of total rides in database (T050)
     *
     * @return Number of rides (completed + active)
     */
    override suspend fun getRideCount(): Int {
        return rideDao.getRideCount()
    }
}
