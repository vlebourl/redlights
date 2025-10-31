package com.tiarkaerell.redlights.data.local.database.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.tiarkaerell.redlights.domain.model.StopCluster
import org.json.JSONArray
import java.time.Instant

/**
 * Room database entity for StopCluster table
 *
 * Aggregates stops occurring at approximately the same location (within 10m)
 *
 * Clustering Algorithm: DBSCAN-inspired
 * - Clustering radius: 10 meters
 * - Centroid: Weighted average of all stop locations
 */
@Entity(
    tableName = "stop_clusters",
    indices = [
        Index(value = ["centroid_latitude", "centroid_longitude"], name = "idx_cluster_location"),
        Index(value = ["stop_count"], name = "idx_cluster_count")
    ]
)
data class StopClusterEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "centroid_latitude")
    val centroidLatitude: Double, // -90 to 90

    @ColumnInfo(name = "centroid_longitude")
    val centroidLongitude: Double, // -180 to 180

    @ColumnInfo(name = "average_duration")
    val averageDuration: Double, // seconds

    @ColumnInfo(name = "median_duration")
    val medianDuration: Long, // seconds

    @ColumnInfo(name = "stop_count")
    val stopCount: Int,

    @ColumnInfo(name = "stop_ids")
    val stopIds: String, // JSON array: "[1,5,12,23]"

    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long // Unix timestamp in milliseconds
) {
    /**
     * Convert database entity to domain model
     */
    fun toDomainModel(): StopCluster {
        val stopIdsList = parseStopIds(stopIds)
        return StopCluster(
            id = id,
            centroidLatitude = centroidLatitude,
            centroidLongitude = centroidLongitude,
            averageDuration = averageDuration,
            medianDuration = medianDuration,
            stopCount = stopCount,
            stopIds = stopIdsList,
            lastUpdated = Instant.ofEpochMilli(lastUpdated)
        )
    }

    companion object {
        /**
         * Create entity from domain model
         */
        fun fromDomainModel(cluster: StopCluster): StopClusterEntity {
            val stopIdsJson = serializeStopIds(cluster.stopIds)
            return StopClusterEntity(
                id = cluster.id,
                centroidLatitude = cluster.centroidLatitude,
                centroidLongitude = cluster.centroidLongitude,
                averageDuration = cluster.averageDuration,
                medianDuration = cluster.medianDuration,
                stopCount = cluster.stopCount,
                stopIds = stopIdsJson,
                lastUpdated = cluster.lastUpdated.toEpochMilli()
            )
        }

        /**
         * Parse JSON array string to List<Long>
         * Example: "[1,5,12,23]" → [1, 5, 12, 23]
         *
         * Public for use by StopClusterRepository
         */
        fun parseStopIds(json: String): List<Long> {
            return try {
                val jsonArray = JSONArray(json)
                List(jsonArray.length()) { i -> jsonArray.getLong(i) }
            } catch (e: Exception) {
                emptyList()
            }
        }

        /**
         * Serialize List<Long> to JSON array string
         * Example: [1, 5, 12, 23] → "[1,5,12,23]"
         *
         * Public for use by StopClusterRepository
         */
        fun serializeStopIds(stopIds: List<Long>): String {
            val jsonArray = JSONArray()
            stopIds.forEach { jsonArray.put(it) }
            return jsonArray.toString()
        }
    }
}
