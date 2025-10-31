package com.tiarkaerell.redlights.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    // Room database providers will be added here in Phase 7
    // when implementing User Story 5 - Database Schema Ready
}
