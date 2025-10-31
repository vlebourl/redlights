package com.tiarkaerell.redlights.data.repository

import com.tiarkaerell.redlights.data.local.database.dao.StopClusterDao
import com.tiarkaerell.redlights.data.local.database.dao.StopDao
import com.tiarkaerell.redlights.data.local.database.entities.StopClusterEntity
import com.tiarkaerell.redlights.data.local.location.LocationTracker
import com.tiarkaerell.redlights.domain.model.Stop
import com.tiarkaerell.redlights.domain.model.StopCluster
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject

/**
 * Implementation of StopClusterRepository with DBSCAN-inspired clustering
 *
 * This repository aggregates stops across multiple rides to identify
 * frequently visited intersections. It uses a simplified DBSCAN algorithm:
 *
 * Clustering Algorithm (T054):
 * 1. For each stop in completed ride:
 *    a. Query existing clusters within radius (10m)
 *    b. If cluster found: Add stop to cluster, recalculate centroid
 *    c. If no cluster: Create new cluster for this stop
 * 2. Update cluster statistics (count, average/median duration, last updated)
 *
 * Centroid Calculation (T055):
 * - Centroid = arithmetic mean of all stop coordinates in cluster
 * - Latitude: avg(all latitudes)
 * - Longitude: avg(all longitudes)
 * - Average duration: mean of stop durations
 * - Median duration: median of stop durations
 *
 * This approach:
 * - Handles GPS drift (stops within 10m are considered same location)
 * - Identifies problem intersections (high stop count or long waits)
 * - Updates incrementally after each ride (no full rebuild needed)
 *
 * Threading: CPU-intensive operations run on Dispatchers.Default
 * Error Handling: Throws exceptions for invalid operations
 *
 * @param stopClusterDao DAO for cluster table operations
 * @param stopDao DAO for stop table operations
 * @param locationTracker Provides distance calculations
 */
class StopClusterRepositoryImpl @Inject constructor(
    private val stopClusterDao: StopClusterDao,
    private val stopDao: StopDao,
    private val locationTracker: LocationTracker
) : StopClusterRepository {

    companion object {
        /**
         * Clustering radius in meters (T054)
         * Stops within this distance are considered at the same intersection
         */
        private const val CLUSTERING_RADIUS_METERS = 10.0

        /**
         * Delta for geospatial bounding box queries
         * ~0.00009 degrees ≈ 10 meters
         */
        private const val GEOSPATIAL_DELTA = 0.00009
    }

    // ==================== Cluster Creation & Updates ====================

    /**
     * Process stops from a completed ride and update clusters (T054)
     *
     * DBSCAN-Inspired Algorithm:
     * - For each stop in the ride:
     *   1. Find existing clusters within CLUSTERING_RADIUS (10m)
     *   2. If match found: Add stop to cluster and recalculate
     *   3. If no match: Create new cluster for this stop
     * - Returns count of clusters created or updated
     *
     * This is CPU-intensive and should run on Dispatchers.Default
     *
     * @param rideId The completed ride to process
     * @return Number of clusters created or updated
     */
    override suspend fun processRideStops(rideId: Long): Int {
        val stops = stopDao.getStopsForRide(rideId)
        if (stops.isEmpty()) return 0

        var clustersAffected = 0

        for (stopEntity in stops) {
            val stop = stopEntity.toDomainModel()

            // Find existing clusters near this stop
            val nearbyClusters = findClustersNearEntity(
                stopEntity.latitude,
                stopEntity.longitude,
                CLUSTERING_RADIUS_METERS
            )

            if (nearbyClusters.isNotEmpty()) {
                // Add stop to existing cluster (use closest)
                val closestCluster = nearbyClusters.first()
                val updatedStopIds = closestCluster.stopIds + stop.id

                // Update cluster with new stop
                val updatedEntity = stopClusterDao.getClusterById(closestCluster.id)!!.copy(
                    stopIds = StopClusterEntity.serializeStopIds(updatedStopIds),
                    stopCount = updatedStopIds.size,
                    lastUpdated = System.currentTimeMillis()
                )
                stopClusterDao.update(updatedEntity)

                // Recalculate centroid and statistics
                recalculateCluster(closestCluster.id)
                clustersAffected++
            } else {
                // Create new cluster for this stop
                createCluster(listOf(stop))
                clustersAffected++
            }
        }

        return clustersAffected
    }

    /**
     * Create a cluster from a list of stops (T055)
     *
     * Calculates:
     * - Centroid: Mean of all stop coordinates
     * - Average duration: Mean of stop durations
     * - Median duration: Median of stop durations
     * - Stop count and IDs
     *
     * @param stops List of stops to cluster together
     * @return The created StopCluster
     * @throws IllegalArgumentException if stops list is empty
     */
    override suspend fun createCluster(stops: List<Stop>): StopCluster {
        require(stops.isNotEmpty()) { "Cannot create cluster from empty stop list" }

        // Calculate centroid
        val centroidLat = stops.map { it.latitude }.average()
        val centroidLon = stops.map { it.longitude }.average()

        // Calculate duration statistics
        val durations = stops.map { it.duration }.sorted()
        val avgDuration = durations.average()
        val medianDuration = if (durations.size % 2 == 0) {
            (durations[durations.size / 2 - 1] + durations[durations.size / 2]) / 2
        } else {
            durations[durations.size / 2]
        }

        val stopIds = stops.map { it.id }
        val entity = StopClusterEntity(
            id = 0, // Auto-generated
            centroidLatitude = centroidLat,
            centroidLongitude = centroidLon,
            averageDuration = avgDuration,
            medianDuration = medianDuration,
            stopCount = stops.size,
            stopIds = StopClusterEntity.serializeStopIds(stopIds),
            lastUpdated = System.currentTimeMillis()
        )

        val clusterId = stopClusterDao.insert(entity)
        return entity.copy(id = clusterId).toDomainModel()
    }

    /**
     * Recalculate cluster centroid and statistics (T055)
     *
     * Called when stops are added to or removed from a cluster.
     * Recalculates:
     * - Centroid from all stop coordinates
     * - Average and median durations
     * - Updates last_updated timestamp
     *
     * @param clusterId The cluster to recalculate
     * @return Updated StopCluster with new centroid and stats
     * @throws IllegalStateException if cluster doesn't exist
     */
    override suspend fun recalculateCluster(clusterId: Long): StopCluster {
        val entity = stopClusterDao.getClusterById(clusterId)
            ?: throw IllegalStateException("Cluster $clusterId does not exist")

        val stopIds = StopClusterEntity.parseStopIds(entity.stopIds)
        if (stopIds.isEmpty()) {
            // Empty cluster - delete it
            stopClusterDao.delete(entity)
            throw IllegalStateException("Cluster $clusterId has no stops and was deleted")
        }

        // Get all stops in cluster
        val stops = stopDao.getStopsByIds(stopIds).map { it.toDomainModel() }

        // Recalculate centroid
        val centroidLat = stops.map { it.latitude }.average()
        val centroidLon = stops.map { it.longitude }.average()

        // Recalculate duration statistics
        val durations = stops.map { it.duration }.sorted()
        val avgDuration = durations.average()
        val medianDuration = if (durations.size % 2 == 0) {
            (durations[durations.size / 2 - 1] + durations[durations.size / 2]) / 2
        } else {
            durations[durations.size / 2]
        }

        // Update entity
        val updatedEntity = entity.copy(
            centroidLatitude = centroidLat,
            centroidLongitude = centroidLon,
            averageDuration = avgDuration,
            medianDuration = medianDuration,
            stopCount = stops.size,
            lastUpdated = System.currentTimeMillis()
        )

        stopClusterDao.update(updatedEntity)
        return updatedEntity.toDomainModel()
    }

    // ==================== Cluster Queries ====================

    /**
     * Find clusters near a specific location (T056)
     *
     * Uses two-step process:
     * 1. Bounding box query to get candidates (fast, database-indexed)
     * 2. Haversine distance calculation to filter exact radius (accurate)
     *
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param radiusMeters Search radius in meters
     * @return List of clusters within radius, sorted by distance
     */
    override suspend fun findClustersNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): List<StopCluster> {
        val entities = findClustersNearEntity(latitude, longitude, radiusMeters)
        return entities.map { it.toDomainModel() }
    }

    /**
     * Internal helper to find nearby clusters (returns entities)
     */
    private suspend fun findClustersNearEntity(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double
    ): List<StopClusterEntity> {
        // Step 1: Bounding box query (fast, indexed)
        val candidates = stopClusterDao.getClustersNearLocation(
            latitude,
            longitude,
            GEOSPATIAL_DELTA,
            GEOSPATIAL_DELTA
        )

        // Step 2: Filter by exact distance using Haversine
        val clustersWithDistance = candidates.map { entity ->
            val distance = locationTracker.calculateDistance(
                latitude,
                longitude,
                entity.centroidLatitude,
                entity.centroidLongitude
            )
            entity to distance
        }

        // Return only clusters within radius, sorted by distance
        return clustersWithDistance
            .filter { (_, distance) -> distance <= radiusMeters }
            .sortedBy { (_, distance) -> distance }
            .map { (entity, _) -> entity }
    }

    /**
     * Get a specific cluster by ID (T057)
     */
    override suspend fun getClusterById(clusterId: Long): StopCluster? {
        return stopClusterDao.getClusterById(clusterId)?.toDomainModel()
    }

    /**
     * Get all clusters with reactive updates (T057)
     *
     * @param limit Maximum number of clusters to return
     * @param offset Number of clusters to skip
     * @return Flow emitting list of clusters, sorted by stop count
     */
    override fun getAllClusters(limit: Int, offset: Int): Flow<List<StopCluster>> {
        return stopClusterDao.getClusters(limit, offset)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    /**
     * Get clusters with minimum number of stops (T058)
     *
     * Identifies "problem intersections" with many stops.
     *
     * @return Flow emitting filtered clusters
     */
    override fun getClustersWithMinStops(minStopCount: Int): Flow<List<StopCluster>> {
        return stopClusterDao.getClustersWithMinStops(minStopCount)
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    /**
     * Get clusters with long average duration (T058)
     *
     * Identifies intersections with long wait times.
     *
     * @return Flow emitting filtered clusters
     */
    override fun getClustersWithLongDuration(minAvgDurationSeconds: Long): Flow<List<StopCluster>> {
        return stopClusterDao.getClustersWithLongDuration(minAvgDurationSeconds.toDouble())
            .map { entities -> entities.map { it.toDomainModel() } }
    }

    // ==================== Cluster Analytics ====================

    /**
     * Get total number of clusters (T059)
     */
    override suspend fun getClusterCount(): Int {
        return stopClusterDao.getClusterCount()
    }

    /**
     * Get cluster with most stops (T059)
     *
     * Identifies the most problematic intersection.
     */
    override suspend fun getMostFrequentCluster(): StopCluster? {
        return stopClusterDao.getMostFrequentCluster()?.toDomainModel()
    }

    /**
     * Get cluster with longest average duration (T059)
     *
     * Identifies intersection with longest wait times.
     */
    override suspend fun getLongestWaitCluster(): StopCluster? {
        return stopClusterDao.getLongestWaitCluster()?.toDomainModel()
    }

    /**
     * Get all stops belonging to a cluster (T060)
     *
     * @return List of stops in chronological order
     */
    override suspend fun getClusterStops(clusterId: Long): List<Stop> {
        val entity = stopClusterDao.getClusterById(clusterId)
            ?: throw IllegalStateException("Cluster $clusterId does not exist")

        val stopIds = StopClusterEntity.parseStopIds(entity.stopIds)
        return stopDao.getStopsByIds(stopIds).map { it.toDomainModel() }
    }

    // ==================== Cluster Maintenance ====================

    /**
     * Delete a cluster (T061)
     *
     * Note: This does NOT delete the stops themselves, only the cluster.
     * Stops remain in the database for their respective rides.
     */
    override suspend fun deleteCluster(clusterId: Long) {
        val entity = stopClusterDao.getClusterById(clusterId)
            ?: throw IllegalStateException("Cluster $clusterId does not exist")

        stopClusterDao.delete(entity)
    }

    /**
     * Rebuild all clusters from scratch (T061)
     *
     * WARNING: Very expensive operation!
     * - Deletes all existing clusters
     * - Processes all stops in database
     * - Recreates clusters from scratch
     *
     * Use cases:
     * - Fixing cluster inconsistencies
     * - Changing clustering parameters
     * - Database maintenance
     *
     * @return Number of clusters created
     */
    override suspend fun rebuildAllClusters(): Int {
        // Delete all existing clusters
        stopClusterDao.deleteAllClusters()

        // Get all stops from database
        val allStops = stopDao.getAllStops()
        if (allStops.isEmpty()) return 0

        var clustersCreated = 0
        val processedStopIds = mutableSetOf<Long>()

        // Process each stop
        for (stopEntity in allStops) {
            if (stopEntity.id in processedStopIds) continue

            val stop = stopEntity.toDomainModel()

            // Find other stops within clustering radius
            val nearbyStops = stopDao.getStopsNearLocation(
                stopEntity.latitude,
                stopEntity.longitude,
                GEOSPATIAL_DELTA,
                GEOSPATIAL_DELTA
            ).map { it.toDomainModel() }

            // Filter by exact distance
            val clusterStops = nearbyStops.filter { nearbyStop ->
                val distance = locationTracker.calculateDistance(
                    stop.latitude,
                    stop.longitude,
                    nearbyStop.latitude,
                    nearbyStop.longitude
                )
                distance <= CLUSTERING_RADIUS_METERS
            }

            // Create cluster from these stops
            if (clusterStops.isNotEmpty()) {
                createCluster(clusterStops)
                processedStopIds.addAll(clusterStops.map { it.id })
                clustersCreated++
            }
        }

        return clustersCreated
    }

    /**
     * Delete stale clusters (T061)
     *
     * Removes clusters not updated recently to clean up
     * old intersections that are no longer visited.
     *
     * @param olderThanDays Delete clusters not updated in this many days
     * @return Number of clusters deleted
     */
    override suspend fun deleteStaleClusters(olderThanDays: Int): Int {
        val cutoffTime = Instant.now().minusSeconds(olderThanDays * 24L * 60 * 60).toEpochMilli()
        return stopClusterDao.deleteStaleClusters(cutoffTime)
    }
}
