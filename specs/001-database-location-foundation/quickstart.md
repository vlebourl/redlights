# Quickstart Guide: Database & Location Infrastructure

**Feature**: 001-database-location-foundation
**Branch**: 001-database-location-foundation
**Last Updated**: 2025-10-31

---

## Overview

This guide helps developers get started with the database and location tracking infrastructure for Redlights. By following this guide, you'll understand the architecture, set up your development environment, and learn the key workflows.

---

## Prerequisites

### Required Software

- **Android Studio**: Ladybug | 2024.2.1 or newer
- **JDK**: Version 11 or higher
- **Android SDK**: API 31+ (Android 12)
- **Git**: For version control
- **Device/Emulator**: Android 12+ with GPS capabilities

### Required Knowledge

- Kotlin fundamentals
- Android app development basics
- Coroutines and Flow
- Clean Architecture principles (helpful but not required)

---

## Project Setup

### 1. Clone Repository

```bash
git clone git@github.com:vlebourl/redlights.git
cd redlights
```

### 2. Checkout Feature Branch

```bash
git checkout 001-database-location-foundation
```

### 3. Open in Android Studio

1. Open Android Studio
2. Select "Open" and navigate to the project directory
3. Wait for Gradle sync to complete
4. Let Android Studio download all dependencies

### 4. Configure Signing (Optional for Debug)

For release builds, you'll need a keystore:

```bash
# Copy template
cp keystore.properties.template keystore.properties

# Edit keystore.properties with your values
# Or use environment variables (see template)
```

### 5. Build and Run

```bash
# Debug build
./gradlew assembleDebug
./gradlew installDebug

# Or use Android Studio's Run button (Shift+F10)
```

---

## Architecture Overview

This feature implements **Clean Architecture** with 3 layers:

```
┌─────────────────────────────────────────────┐
│         Presentation Layer (Future)         │
│    ViewModels, Composables, UI State        │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│            Domain Layer (Pure)              │
│    Models, Use Cases, Business Logic        │
└─────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────┐
│              Data Layer                     │
│  Room DB, DAOs, Location Service, Repos     │
└─────────────────────────────────────────────┘
```

### Key Technologies

- **Room 2.6+**: Database abstraction layer (SQLite)
- **Hilt 2.48+**: Dependency injection framework
- **Google Play Services Location 21.0+**: GPS tracking (FusedLocationProviderClient)
- **Kotlin Coroutines 1.7+**: Asynchronous operations
- **Jetpack Compose**: UI framework (used in other features)

---

## Project Structure

```
app/src/main/java/com/tiarkaerell/redlights/
│
├── data/                          # Data Layer
│   ├── local/
│   │   ├── database/              # Room database
│   │   │   ├── RedlightsDatabase.kt
│   │   │   ├── entities/          # Database entities
│   │   │   │   ├── RideEntity.kt
│   │   │   │   ├── RidePointEntity.kt
│   │   │   │   ├── StopEntity.kt
│   │   │   │   └── StopClusterEntity.kt
│   │   │   └── dao/               # Data Access Objects
│   │   │       ├── RideDao.kt
│   │   │       ├── RidePointDao.kt
│   │   │       ├── StopDao.kt
│   │   │       └── StopClusterDao.kt
│   │   └── location/              # Location tracking
│   │       ├── LocationTracker.kt
│   │       └── LocationTrackerService.kt  # Foreground service
│   └── repository/                # Repository implementations
│       ├── RideRepositoryImpl.kt
│       ├── LocationRepositoryImpl.kt
│       └── StopClusterRepositoryImpl.kt
│
├── domain/                        # Domain Layer
│   ├── model/                     # Domain models
│   │   ├── Ride.kt
│   │   ├── RidePoint.kt
│   │   ├── Stop.kt
│   │   └── StopCluster.kt
│   └── usecase/                   # Use cases
│       ├── StartRideUseCase.kt
│       ├── StopRideUseCase.kt
│       ├── GetRideHistoryUseCase.kt
│       └── clustering/
│           └── ClusterStopsUseCase.kt
│
└── di/                            # Dependency Injection
    ├── DatabaseModule.kt          # Room + DAOs
    └── LocationModule.kt          # Location services
```

---

## Core Concepts

### 1. Data Model

**Four Main Entities:**

1. **Ride**: A cycling session from start to finish
   - Stores metrics: distance, duration, stops, speeds
   - One active ride at a time

2. **RidePoint**: GPS coordinate captured during a ride
   - Smart sampling: Only stores significant position changes
   - Typical ride: 100-500 points (vs 3600 raw GPS updates)

3. **Stop**: Detected stationary period during a ride
   - Captured when speed <5 km/h for >15 seconds
   - Tracks duration, location, sequence number

4. **StopCluster**: Aggregation of stops at same location
   - Groups stops within 10m radius across all rides
   - Identifies problematic intersections

### 2. Location Tracking

**Tracking Pipeline:**

```
GPS Hardware → FusedLocationProvider → LocationTrackerService
    ↓
Filtering (accuracy >50m rejected)
    ↓
Stop Detection State Machine
    ↓
Smart Sampling (significance-based)
    ↓
Room Database Storage
```

**Stop Detection States:**

```
MOVING → (speed <5 km/h) → POTENTIAL_STOP
    ↓                              ↓
    ↓                    (15s elapsed, within 10m tolerance)
    ↓                              ↓
    ↓                       CONFIRMED_STOP
    ↓                              ↓
    ←─────────────────────────────┘
         (speed ≥5 km/h)
```

### 3. Smart Sampling

**GPS points are stored ONLY if:**
- Speed changed by >2 km/h from last stored point, OR
- Bearing changed by >15 degrees from last stored point, OR
- More than 30 seconds elapsed since last stored point, OR
- User is currently in a detected stop state

**Result**: 80-95% storage reduction with no accuracy loss

### 4. Clustering Algorithm

**Post-Ride Processing:**

```kotlin
For each stop in completed ride:
  1. Find existing clusters within 10m
  2. If match found:
     - Add stop to cluster
     - Recalculate centroid (weighted average)
     - Update statistics (avg/median duration, count)
  3. Else:
     - Create new cluster with this stop
```

---

## Development Workflows

### Creating a New Use Case

```kotlin
// 1. Define in domain/usecase/
class StartRideUseCase @Inject constructor(
    private val rideRepository: RideRepository,
    private val locationRepository: LocationRepository
) {
    suspend operator fun invoke(): Result<Ride> {
        return try {
            // Check permissions
            if (!locationRepository.hasLocationPermission()) {
                return Result.failure(Exception("Location permission required"))
            }

            // Create ride
            val ride = rideRepository.createRide(startTime = Instant.now())

            // Start location tracking
            locationRepository.startLocationUpdates()

            Result.success(ride)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// 2. Inject into ViewModel (future)
// 3. Call from UI (future)
```

### Adding a Database Entity Field

```kotlin
// 1. Update Entity class
@Entity(tableName = "rides")
data class RideEntity(
    // ... existing fields ...
    @ColumnInfo(name = "new_field")
    val newField: String? = null
)

// 2. Update DAO queries if needed
@Query("SELECT * FROM rides WHERE new_field = :value")
suspend fun findByNewField(value: String): List<RideEntity>

// 3. Bump database version
@Database(
    entities = [...],
    version = 2,  // Increment
    exportSchema = true
)

// 4. Add migration
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE rides ADD COLUMN new_field TEXT")
    }
}

// 5. Register migration in DatabaseModule
```

### Implementing a Repository

```kotlin
// 1. Repository interface in data/repository/
interface RideRepository {
    suspend fun createRide(startTime: Instant): Ride
    // ... other methods ...
}

// 2. Implementation with Entity↔Model mapping
class RideRepositoryImpl @Inject constructor(
    private val rideDao: RideDao
) : RideRepository {

    override suspend fun createRide(startTime: Instant): Ride {
        val entity = RideEntity(
            startTime = startTime.toEpochMilli(),
            // ... initialize fields ...
        )
        val id = rideDao.insert(entity)
        return entity.toDomainModel(id)
    }

    // Mapping functions
    private fun RideEntity.toDomainModel(id: Long) = Ride(
        id = id,
        startTime = Instant.ofEpochMilli(startTime),
        // ... map all fields ...
    )
}

// 3. Bind in DatabaseModule
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideRideRepository(
        rideDao: RideDao
    ): RideRepository = RideRepositoryImpl(rideDao)
}
```

---

## Testing Strategy

### Manual Testing (v1.0)

**Test Scenarios:**

1. **Short Ride (5 minutes)**
   - Start ride → ride for 5 min → stop ride
   - Verify: Ride saved, metrics calculated, points captured

2. **Long Ride (1 hour)**
   - Start ride → ride for 1 hour → stop ride
   - Verify: Storage <5MB, query performance <2s

3. **Multiple Stops**
   - Start ride → stop at 3-5 red lights → complete ride
   - Verify: All stops detected, clustering works

4. **GPS Loss**
   - Start ride → enter tunnel (GPS loss) → exit tunnel
   - Verify: Tracking resumes, no crashes

5. **App Force Close**
   - Start ride → force close app → reopen app
   - Verify: Incomplete ride discarded, no crashes

6. **Low Storage**
   - Fill device storage to <100MB → start ride
   - Verify: Warning shown, graceful handling

### Unit Tests (v1.1+)

```kotlin
// Example test structure
class RideRepositoryTest {
    @Test
    fun `createRide should store ride with correct timestamp`() = runTest {
        // Arrange
        val repository = RideRepositoryImpl(fakeDao)
        val startTime = Instant.now()

        // Act
        val ride = repository.createRide(startTime)

        // Assert
        assertEquals(startTime, ride.startTime)
        assertNull(ride.endTime)
    }
}
```

---

## Performance Guidelines

### Database Performance

- **Indexes**: Already defined on foreign keys and frequently queried columns
- **Queries**: Use `@Transaction` for multi-table operations
- **Pagination**: Load rides in batches of 20
- **Flow**: Use for reactive queries (auto-update UI)

### Location Tracking

- **Update Interval**: 1 second (1 Hz)
- **Accuracy Filter**: Reject updates >50m accuracy
- **Sampling**: ~200-400 points stored per hour (vs 3600 raw)
- **Battery Target**: <10% per hour of tracking

### Storage Estimates

- **Per Ride**: 1-4 MB (depends on duration and route complexity)
- **500 Rides**: ~500 MB total storage
- **Cleanup**: Manual deletion by user (no automatic expiry)

---

## Debugging Tips

### Location Not Working

```kotlin
// Check permissions
if (!locationRepository.hasLocationPermission()) {
    // Request permission
}

// Check location services
if (!locationRepository.isLocationEnabled()) {
    // Prompt user to enable GPS
}

// Check foreground service
// Verify notification is visible when tracking
```

### Database Queries Slow

```kotlin
// Use EXPLAIN QUERY PLAN in DAO
@Query("EXPLAIN QUERY PLAN SELECT * FROM rides WHERE startTime > :time")
suspend fun explainQuery(time: Long): List<String>

// Check indexes are being used
// Add indexes for frequently queried columns
```

### Crashes on Startup

```kotlin
// Check for incomplete rides
// These should be automatically deleted
rideRepository.deleteIncompleteRides()

// Check database migrations
// Ensure all migrations are registered
```

---

## Common Pitfalls

### ❌ Don't: Store every GPS update

```kotlin
// BAD: Stores 3600 points per hour
locationUpdates.collect { location ->
    rideRepository.addRidePoint(rideId, location.toRidePoint())
}
```

### ✅ Do: Use smart sampling

```kotlin
// GOOD: Stores 200-400 points per hour
locationUpdates.collect { location ->
    if (shouldStorePoint(location, lastStoredPoint)) {
        rideRepository.addRidePoint(rideId, location.toRidePoint())
        lastStoredPoint = location
    }
}
```

### ❌ Don't: Block main thread

```kotlin
// BAD: Blocks UI
fun loadRides() {
    val rides = runBlocking { rideRepository.getAllRides() }
}
```

### ✅ Do: Use coroutines properly

```kotlin
// GOOD: Non-blocking
viewModelScope.launch {
    rideRepository.getAllRides().collect { rides ->
        _uiState.value = UiState.Success(rides)
    }
}
```

### ❌ Don't: Ignore incomplete rides

```kotlin
// BAD: Can lead to corrupted data
// Just ignore them
```

### ✅ Do: Clean up on startup

```kotlin
// GOOD: Discard incomplete rides from crashes
class RedlightsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        lifecycleScope.launch {
            rideRepository.deleteIncompleteRides()
        }
    }
}
```

---

## Next Steps

After understanding this infrastructure:

1. **Review Contracts**: See `specs/001-database-location-foundation/contracts/`
2. **Read Data Model**: See `specs/001-database-location-foundation/data-model.md`
3. **Read Research**: See `specs/001-database-location-foundation/research.md`
4. **Implement Use Cases**: Follow task breakdown in `tasks.md` (when created)
5. **Test Thoroughly**: Follow manual test scenarios above

---

## Support & Resources

- **Specification**: `specs/001-database-location-foundation/spec.md`
- **Research**: `specs/001-database-location-foundation/research.md`
- **Data Model**: `specs/001-database-location-foundation/data-model.md`
- **Repository Contracts**: `specs/001-database-location-foundation/contracts/`

For questions or issues, refer to the comprehensive documentation in the specs directory.

---

**Last Updated**: 2025-10-31
**Feature Branch**: 001-database-location-foundation
