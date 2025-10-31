package com.tiarkaerell.redlights.data.local.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tiarkaerell.redlights.data.local.database.entities.StopClusterEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for StopCluster table
 *
 * Provides operations for stop cluster analytics and aggregation
 */
@Dao
interface StopClusterDao {

    /**
     * Insert a new cluster
     * @return The ID of the inserted cluster
     */
    @Insert
    suspend fun insert(cluster: StopClusterEntity): Long

    /**
     * Update an existing cluster
     */
    @Update
    suspend fun update(cluster: StopClusterEntity)

    /**
     * Delete a cluster
     */
    @Delete
    suspend fun delete(cluster: StopClusterEntity)

    /**
     * Get a cluster by ID
     */
    @Query("SELECT * FROM stop_clusters WHERE id = :clusterId")
    suspend fun getClusterById(clusterId: Long): StopClusterEntity?

    /**
     * Get all clusters sorted by stop count (most frequent first)
     */
    @Query("SELECT * FROM stop_clusters ORDER BY stop_count DESC")
    fun getAllClusters(): Flow<List<StopClusterEntity>>

    /**
     * Get all clusters with pagination
     */
    @Query("SELECT * FROM stop_clusters ORDER BY stop_count DESC LIMIT :limit OFFSET :offset")
    fun getClusters(limit: Int, offset: Int): Flow<List<StopClusterEntity>>

    /**
     * Find clusters near a specific location (for finding matching cluster during ride)
     * Uses simple bounding box for initial filtering
     *
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param latDelta Latitude range (~0.00009 degrees ≈ 10 meters)
     * @param lonDelta Longitude range (~0.00009 degrees ≈ 10 meters at equator)
     */
    @Query("""
        SELECT * FROM stop_clusters
        WHERE centroid_latitude BETWEEN :latitude - :latDelta AND :latitude + :latDelta
        AND centroid_longitude BETWEEN :longitude - :lonDelta AND :longitude + :lonDelta
    """)
    suspend fun getClustersNearLocation(
        latitude: Double,
        longitude: Double,
        latDelta: Double = 0.00009,
        lonDelta: Double = 0.00009
    ): List<StopClusterEntity>

    /**
     * Get clusters with minimum number of stops (problem intersections)
     */
    @Query("SELECT * FROM stop_clusters WHERE stop_count >= :minStops ORDER BY stop_count DESC")
    fun getClustersWithMinStops(minStops: Int): Flow<List<StopClusterEntity>>

    /**
     * Get clusters with average duration above threshold (long wait times)
     */
    @Query("SELECT * FROM stop_clusters WHERE average_duration >= :minAvgDuration ORDER BY average_duration DESC")
    fun getClustersWithLongDuration(minAvgDuration: Double): Flow<List<StopClusterEntity>>

    /**
     * Get the cluster with the most stops
     */
    @Query("SELECT * FROM stop_clusters ORDER BY stop_count DESC LIMIT 1")
    suspend fun getMostFrequentCluster(): StopClusterEntity?

    /**
     * Get the cluster with longest average wait time
     */
    @Query("SELECT * FROM stop_clusters ORDER BY average_duration DESC LIMIT 1")
    suspend fun getLongestWaitCluster(): StopClusterEntity?

    /**
     * Get total count of all clusters
     */
    @Query("SELECT COUNT(*) FROM stop_clusters")
    suspend fun getClusterCount(): Int

    /**
     * Delete clusters not updated recently (stale clusters)
     * @param cutoffTime Unix timestamp - delete clusters older than this
     * @return Number of clusters deleted
     */
    @Query("DELETE FROM stop_clusters WHERE last_updated < :cutoffTime")
    suspend fun deleteStaleClusters(cutoffTime: Long): Int

    /**
     * Delete all clusters (for rebuild operation)
     * @return Number of clusters deleted
     */
    @Query("DELETE FROM stop_clusters")
    suspend fun deleteAllClusters(): Int
}
