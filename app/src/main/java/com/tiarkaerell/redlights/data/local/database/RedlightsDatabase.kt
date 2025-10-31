package com.tiarkaerell.redlights.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.tiarkaerell.redlights.data.local.database.dao.RideDao
import com.tiarkaerell.redlights.data.local.database.dao.RidePointDao
import com.tiarkaerell.redlights.data.local.database.dao.StopClusterDao
import com.tiarkaerell.redlights.data.local.database.dao.StopDao
import com.tiarkaerell.redlights.data.local.database.entities.RideEntity
import com.tiarkaerell.redlights.data.local.database.entities.RidePointEntity
import com.tiarkaerell.redlights.data.local.database.entities.StopClusterEntity
import com.tiarkaerell.redlights.data.local.database.entities.StopEntity

/**
 * Room database for Redlights app
 *
 * Contains 4 tables:
 * - rides: Complete cycling sessions
 * - ride_points: GPS coordinates (smart sampled)
 * - stops: Stationary periods >15 seconds
 * - stop_clusters: Aggregated stops at same location
 *
 * Database Features:
 * - WAL (Write-Ahead Logging) mode for better concurrent access
 * - Foreign key constraints with CASCADE delete
 * - Indexes for performance optimization
 * - Fallback to destructive migration for v1.0 (no user data migration needed)
 */
@Database(
    entities = [
        RideEntity::class,
        RidePointEntity::class,
        StopEntity::class,
        StopClusterEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class RedlightsDatabase : RoomDatabase() {

    /**
     * DAO for Ride operations
     */
    abstract fun rideDao(): RideDao

    /**
     * DAO for RidePoint operations
     */
    abstract fun ridePointDao(): RidePointDao

    /**
     * DAO for Stop operations
     */
    abstract fun stopDao(): StopDao

    /**
     * DAO for StopCluster operations
     */
    abstract fun stopClusterDao(): StopClusterDao

    companion object {
        private const val DATABASE_NAME = "redlights.db"

        /**
         * Volatile singleton instance
         * Not actually used here (Hilt provides instance), but pattern kept for clarity
         */
        @Volatile
        private var INSTANCE: RedlightsDatabase? = null

        /**
         * Create database instance with configuration
         * Called by Hilt's DatabaseModule
         *
         * @param context Application context
         * @return Configured database instance
         */
        fun create(context: Context): RedlightsDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                RedlightsDatabase::class.java,
                DATABASE_NAME
            )
                // Enable WAL mode for better concurrent access
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)

                // For v1.0: Destroy and recreate on schema changes (no user data yet)
                // Future versions: Replace with proper migrations
                .fallbackToDestructiveMigration()

                // Enable multi-instance invalidation for consistency
                .enableMultiInstanceInvalidation()

                // Add callback for database initialization
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Database created - could initialize data here if needed
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        // Ensure foreign keys are enforced
                        db.execSQL("PRAGMA foreign_keys=ON")
                    }
                })

                .build()
        }

        /**
         * Get database instance (for testing or non-Hilt scenarios)
         */
        fun getInstance(context: Context): RedlightsDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: create(context).also { INSTANCE = it }
            }
        }
    }
}
