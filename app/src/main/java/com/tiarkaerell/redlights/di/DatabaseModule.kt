package com.tiarkaerell.redlights.di

import android.content.Context
import com.tiarkaerell.redlights.data.local.database.RedlightsDatabase
import com.tiarkaerell.redlights.data.local.database.dao.RideDao
import com.tiarkaerell.redlights.data.local.database.dao.RidePointDao
import com.tiarkaerell.redlights.data.local.database.dao.StopClusterDao
import com.tiarkaerell.redlights.data.local.database.dao.StopDao
import com.tiarkaerell.redlights.data.repository.RideRepository
import com.tiarkaerell.redlights.data.repository.RideRepositoryImpl
import com.tiarkaerell.redlights.data.repository.StopClusterRepository
import com.tiarkaerell.redlights.data.repository.StopClusterRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing database and repository dependencies
 *
 * This module:
 * - Provides RedlightsDatabase instance (singleton)
 * - Provides all DAO instances from database
 * - Binds repository implementations to interfaces
 *
 * Scope: SingletonComponent (application-wide)
 *
 * Usage:
 * ```kotlin
 * @Inject lateinit var rideRepository: RideRepository
 * @Inject lateinit var rideDao: RideDao
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    companion object {
        /**
         * Provide RedlightsDatabase instance (T063)
         *
         * Creates a singleton database instance with:
         * - WAL mode for concurrent access
         * - Destructive migration for v1.0
         * - Multi-instance invalidation
         * - Foreign key enforcement
         *
         * @param context Application context for database creation
         * @return Singleton database instance
         */
        @Provides
        @Singleton
        fun provideRedlightsDatabase(
            @ApplicationContext context: Context
        ): RedlightsDatabase {
            return RedlightsDatabase.create(context)
        }

        /**
         * Provide RideDao from database (T064)
         *
         * @param database The RedlightsDatabase instance
         * @return RideDao for ride table operations
         */
        @Provides
        fun provideRideDao(database: RedlightsDatabase): RideDao {
            return database.rideDao()
        }

        /**
         * Provide RidePointDao from database (T065)
         *
         * @param database The RedlightsDatabase instance
         * @return RidePointDao for ride point table operations
         */
        @Provides
        fun provideRidePointDao(database: RedlightsDatabase): RidePointDao {
            return database.ridePointDao()
        }

        /**
         * Provide StopDao from database (T066)
         *
         * @param database The RedlightsDatabase instance
         * @return StopDao for stop table operations
         */
        @Provides
        fun provideStopDao(database: RedlightsDatabase): StopDao {
            return database.stopDao()
        }

        /**
         * Provide StopClusterDao from database (T067)
         *
         * @param database The RedlightsDatabase instance
         * @return StopClusterDao for cluster table operations
         */
        @Provides
        fun provideStopClusterDao(database: RedlightsDatabase): StopClusterDao {
            return database.stopClusterDao()
        }
    }

    /**
     * Bind RideRepositoryImpl to RideRepository interface (T068)
     *
     * Hilt will automatically inject RideRepositoryImpl whenever
     * RideRepository is requested as a dependency.
     *
     * @param impl The concrete implementation
     * @return The interface binding
     */
    @Binds
    @Singleton
    abstract fun bindRideRepository(
        impl: RideRepositoryImpl
    ): RideRepository

    /**
     * Bind StopClusterRepositoryImpl to StopClusterRepository interface (T069)
     *
     * Hilt will automatically inject StopClusterRepositoryImpl whenever
     * StopClusterRepository is requested as a dependency.
     *
     * @param impl The concrete implementation
     * @return The interface binding
     */
    @Binds
    @Singleton
    abstract fun bindStopClusterRepository(
        impl: StopClusterRepositoryImpl
    ): StopClusterRepository
}
