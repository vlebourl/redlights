package com.tiarkaerell.redlights.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.tiarkaerell.redlights.data.local.location.DefaultLocationTracker
import com.tiarkaerell.redlights.data.local.location.LocationTracker
import com.tiarkaerell.redlights.data.repository.LocationRepository
import com.tiarkaerell.redlights.data.repository.LocationRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing location tracking dependencies
 *
 * This module:
 * - Provides FusedLocationProviderClient from Google Play Services
 * - Binds DefaultLocationTracker to LocationTracker interface
 * - Binds LocationRepositoryImpl to LocationRepository interface
 *
 * Scope: SingletonComponent (application-wide)
 *
 * Usage:
 * ```kotlin
 * @Inject lateinit var locationRepository: LocationRepository
 * @Inject lateinit var locationTracker: LocationTracker
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    companion object {
        /**
         * Provide FusedLocationProviderClient (T071)
         *
         * FusedLocationProviderClient is Google's recommended API for location services.
         * It automatically selects the best location provider (GPS, network, etc.)
         * and provides battery-efficient location updates.
         *
         * @param context Application context
         * @return FusedLocationProviderClient instance
         */
        @Provides
        @Singleton
        fun provideFusedLocationProviderClient(
            @ApplicationContext context: Context
        ): FusedLocationProviderClient {
            return LocationServices.getFusedLocationProviderClient(context)
        }
    }

    /**
     * Bind DefaultLocationTracker to LocationTracker interface (T072)
     *
     * Hilt will automatically inject DefaultLocationTracker whenever
     * LocationTracker is requested as a dependency.
     *
     * @param impl The concrete implementation
     * @return The interface binding
     */
    @Binds
    @Singleton
    abstract fun bindLocationTracker(
        impl: DefaultLocationTracker
    ): LocationTracker

    /**
     * Bind LocationRepositoryImpl to LocationRepository interface (T073)
     *
     * Hilt will automatically inject LocationRepositoryImpl whenever
     * LocationRepository is requested as a dependency.
     *
     * @param impl The concrete implementation
     * @return The interface binding
     */
    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl
    ): LocationRepository
}
