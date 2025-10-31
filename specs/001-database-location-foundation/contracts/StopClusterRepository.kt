package com.tiarkaerell.redlights.data.repository

import com.tiarkaerell.redlights.domain.model.Stop
import com.tiarkaerell.redlights.domain.model.StopCluster
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for Stop Clustering operations
 *
 * This interface defines the contract for aggregating and analyzing stops
 * across multiple rides to identify frequently visited intersections.
 * Clustering runs as a background task after ride completion.
 *
 * Threading: All operations run on Dispatchers.Default (CPU-intensive)
 * Error Handling: Throws exceptions for database errors (caught by use cases)
 */
interface StopClusterRepository {

    // ==================== Cluster Creation & Updates ====================

    /**
     * Process stops from a completed ride and update clusters
     *
     * For each stop:
     * - Find existing clusters within clustering radius (10m)
     * - Add stop to matching cluster and recalculate centroid
     * - Create new cluster if no match found
     *
     * This is a CPU-intensive operation that should run after ride completion.
     *
     * @param rideId The completed ride to process
     * @return Number of clusters created or updated
     */
    suspend fun processRideStops(rideId: Long): Int

    /**
     * Manually create a cluster from a list of stops
     * Useful for initializing clusters from historical data
     *
     * @param stops List of stops to cluster together
     * @return The created StopCluster
     * @throws IllegalArgumentException if stops list is empty
     */
    suspend fun createCluster(stops: List<Stop>): StopCluster

    /**
     * Recalculate cluster centroid and statistics
     * Called when stops are added to or removed from a cluster
     *
     * @param clusterId The cluster to recalculate
     * @return Updated StopCluster with new centroid and stats
     * @throws IllegalStateException if cluster doesn't exist
     */
    suspend fun recalculateCluster(clusterId: Long): StopCluster

    // ==================== Cluster Queries ====================

    /**
     * Find clusters near a specific location
     * Used to check if a new stop matches existing clusters
     *
     * @param latitude Location latitude
     * @param longitude Location longitude
     * @param radiusMeters Search radius in meters (default: 10m)
     * @return List of clusters within radius, sorted by distance (closest first)
     */
    suspend fun findClustersNear(
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = 10.0
    ): List<StopCluster>

    /**
     * Get a specific cluster by ID
     * @param clusterId The cluster identifier
     * @return StopCluster if found, null otherwise
     */
    suspend fun getClusterById(clusterId: Long): StopCluster?

    /**
     * Get all clusters, sorted by stop count (most frequent first)
     * @param limit Maximum number of clusters to return (for pagination)
     * @param offset Number of clusters to skip (for pagination)
     * @return Flow emitting list of clusters (reactive - updates automatically)
     */
    fun getAllClusters(limit: Int = 50, offset: Int = 0): Flow<List<StopCluster>>

    /**
     * Get clusters with minimum number of stops
     * Useful for finding "problem intersections" with many stops
     *
     * @param minStopCount Minimum number of stops to include cluster
     * @return Flow emitting filtered clusters, sorted by stop count descending
     */
    fun getClustersWithMinStops(minStopCount: Int): Flow<List<StopCluster>>

    /**
     * Get clusters with average duration above threshold
     * Identifies intersections with long wait times
     *
     * @param minAvgDurationSeconds Minimum average duration in seconds
     * @return Flow emitting filtered clusters, sorted by avg duration descending
     */
    fun getClustersWithLongDuration(minAvgDurationSeconds: Long): Flow<List<StopCluster>>

    // ==================== Cluster Analytics ====================

    /**
     * Get total number of clusters in database
     * @return Count of all stop clusters
     */
    suspend fun getClusterCount(): Int

    /**
     * Get cluster with the most stops
     * Identifies the most problematic intersection
     *
     * @return Cluster with highest stop count, null if no clusters exist
     */
    suspend fun getMostFrequentCluster(): StopCluster?

    /**
     * Get cluster with longest average duration
     * Identifies intersection with longest wait times
     *
     * @return Cluster with highest average duration, null if no clusters exist
     */
    suspend fun getLongestWaitCluster(): StopCluster?

    /**
     * Get all stops belonging to a specific cluster
     * Useful for detailed analysis of a cluster's history
     *
     * @param clusterId The cluster identifier
     * @return List of stops in chronological order
     */
    suspend fun getClusterStops(clusterId: Long): List<Stop>

    // ==================== Cluster Maintenance ====================

    /**
     * Delete a cluster and remove references from stops
     * @param clusterId The cluster to delete
     */
    suspend fun deleteCluster(clusterId: Long)

    /**
     * Rebuild all clusters from scratch
     * Useful for fixing inconsistencies or changing clustering parameters
     * Deletes all existing clusters and recreates from all stops in database
     *
     * WARNING: This is a very expensive operation
     * @return Number of clusters created
     */
    suspend fun rebuildAllClusters(): Int

    /**
     * Remove clusters that haven't been updated recently
     * Helps clean up old clusters from areas no longer visited
     *
     * @param olderThanDays Delete clusters not updated in this many days
     * @return Number of clusters deleted
     */
    suspend fun deleteStaleclusters(olderThanDays: Int = 365): Int
}
