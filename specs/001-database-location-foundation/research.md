# Research: Database & Location Infrastructure

**Feature**: 001-database-location-foundation
**Date**: 2025-10-31
**Purpose**: Document technical decisions, alternatives considered, and rationales for implementation choices

---

## Executive Summary

This research consolidates technical decisions for building the foundational data persistence and location tracking infrastructure for Redlights. All decisions prioritize reliability, performance, and battery efficiency for a bike computer application requiring continuous GPS tracking and local data storage.

---

## Database Technology Selection

### Decision: Room Persistence Library

**Rationale**:
- **Type Safety**: Compile-time SQL verification prevents runtime database errors
- **Kotlin Integration**: Native coroutines support for non-blocking database operations
- **Migration Support**: Built-in schema versioning and migration framework
- **Android Standard**: Official Android persistence library with strong community support
- **Performance**: Thin abstraction over SQLite with minimal overhead

**Alternatives Considered**:
1. **Raw SQLite**
   - ❌ Requires manual SQL string writing (error-prone)
   - ❌ No compile-time verification
   - ❌ More boilerplate code for CRUD operations
   - ✅ Slightly better performance (negligible for our scale)

2. **Realm**
   - ❌ Proprietary technology with licensing considerations
   - ❌ Object database model doesn't fit relational needs
   - ❌ Migration complexity for schema changes
   - ✅ Good performance for certain access patterns

3. **SQLDelight**
   - ✅ Excellent type safety
   - ❌ More complex setup and learning curve
   - ❌ Less community support than Room for Android
   - ⚖️ Similar performance to Room

**Implementation Notes**:
- Use `@Entity` annotations for database schema definition
- Implement DAOs (Data Access Objects) with `suspend` functions for coroutine support
- Enable WAL (Write-Ahead Logging) mode for better concurrent access
- Configure database builder with `.fallbackToDestructiveMigration()` for v1.0 (no schema migrations needed yet)

---

## Location Services Technology

### Decision: Google Play Services Location API (FusedLocationProviderClient)

**Rationale**:
- **Battery Optimization**: Fuses multiple location sources (GPS, WiFi, cell towers) intelligently
- **Accuracy**: Provides filtered, smoothed location updates better than raw GPS
- **Standard Solution**: Most widely used location API in Android ecosystem
- **Foreground Service Support**: Works reliably with Android 12+ background restrictions
- **Simple API**: Clean, coroutine-friendly interface with Flow/Callback support

**Alternatives Considered**:
1. **Android Location Manager (Raw GPS)**
   - ❌ Higher battery drain (no fusion intelligence)
   - ❌ More noisy location data requires manual filtering
   - ❌ Lower accuracy in urban environments
   - ✅ No Google Play Services dependency
   - ✅ Works on all Android devices

2. **Android Location Manager (Network Provider)**
   - ❌ Requires internet connection
   - ❌ Lower accuracy (100-200m typical)
   - ❌ Not suitable for route tracking

**Implementation Notes**:
- Request `PRIORITY_HIGH_ACCURACY` for GPS-level precision
- Set update interval to 1000ms (1 second)
- Implement accuracy filtering: reject updates with `accuracy > 50 meters`
- Use `LocationRequest.Builder()` for API 31+ compatibility
- Handle permission requests for `ACCESS_FINE_LOCATION`

---

## Foreground Service Architecture

### Decision: Android Foreground Service with Persistent Notification

**Rationale**:
- **Required for Background Tracking**: Android 12+ strictly limits background location access
- **User Transparency**: Persistent notification shows tracking is active
- **System Priority**: Foreground services receive higher priority, less likely to be killed
- **Battery Impact**: User sees notification, aware of battery drain
- **Reliable Tracking**: Works even when app is not visible

**Alternatives Considered**:
1. **Background Service (without foreground status)**
   - ❌ Prohibited by Android 12+ for continuous location tracking
   - ❌ Gets terminated by system within minutes

2. **WorkManager Periodic Tasks**
   - ❌ Minimum interval is 15 minutes (we need 1-second updates)
   - ❌ Not designed for continuous real-time tracking
   - ✅ Better for scheduled, non-continuous tasks

**Implementation Notes**:
- Create notification channel with `IMPORTANCE_LOW` (silent, non-intrusive)
- Show ride duration in notification (updated periodically)
- Include "Stop Ride" action button in notification
- Use `START_STICKY` for service restart on kill
- Handle service lifecycle properly (onCreate, onStartCommand, onDestroy)

---

## Data Sampling Strategy

### Decision: Smart Sampling Based on Significance

**Rationale**:
- **Storage Efficiency**: Storing 3600 GPS points per hour is wasteful (most redundant)
- **Performance**: Fewer database writes reduce battery drain and improve responsiveness
- **Accuracy Preservation**: Significant changes capture route accurately
- **Query Performance**: Fewer records to load when retrieving ride data

**Sampling Rules**:
Store a GPS point if ANY of these conditions are met:
1. Speed changed by >2 km/h from last stored point
2. Bearing changed by >15 degrees from last stored point
3. More than 30 seconds elapsed since last stored point
4. User is currently in a detected stop state

**Expected Compression**:
- Straight road at constant speed: ~1 point per 30 seconds (120 points/hour)
- Urban riding with turns: ~2-4 points per minute (120-240 points/hour)
- Stop-and-go traffic: ~5-8 points per minute (300-480 points/hour)
- **Result**: 100-500 stored points per hour vs. 3600 raw updates (80-95% reduction)

**Alternatives Considered**:
1. **Store Every GPS Update**
   - ❌ 3600 points per hour * 8 bytes per field * 7 fields = ~200KB per hour of raw data
   - ❌ Slow query performance for long rides
   - ❌ Unnecessary storage consumption
   - ✅ No risk of missing details

2. **Fixed Time Sampling (e.g., every 5 seconds)**
   - ❌ Misses rapid changes in speed/direction
   - ❌ Still stores redundant data on straight roads
   - ⚖️ Simpler logic

3. **Distance-Based Sampling (every X meters)**
   - ❌ Doesn't capture stopped periods well
   - ❌ Complex to calculate distance in real-time
   - ✅ Good for visualizing route

**Implementation Notes**:
- Keep last stored point in memory for comparison
- Calculate bearing change using Haversine formula
- All GPS updates still processed for stop detection (sampling affects storage only)

---

## Stop Detection Algorithm

### Decision: State Machine with Tolerance Window

**Rationale**:
- **Accuracy**: Detects genuine stops while filtering out traffic shuffle
- **Reliability**: State machine prevents false positives from GPS noise
- **Flexibility**: Tolerance window handles minor movements during stops

**State Transitions**:
```
MOVING → (speed < 5 km/h) → POTENTIAL_STOP
POTENTIAL_STOP → (duration > 15s AND within 10m tolerance) → CONFIRMED_STOP
POTENTIAL_STOP → (speed >= 5 km/h OR moved >10m) → MOVING
CONFIRMED_STOP → (speed >= 5 km/h) → MOVING (record stop end time)
```

**Parameters**:
- **Speed Threshold**: <5 km/h = stopped (accounts for GPS drift)
- **Duration Threshold**: >15 seconds (filters out brief pauses)
- **Tolerance Radius**: 10 meters (allows shuffling forward at lights)

**Alternatives Considered**:
1. **Simple Speed Threshold**
   - ❌ Creates stop records for every momentary slow-down
   - ❌ No duration filtering
   - ✅ Simpler implementation

2. **Machine Learning Classification**
   - ❌ Overkill for v1.0
   - ❌ Requires training data
   - ❌ More complex to maintain
   - ✅ Could adapt to user behavior

**Implementation Notes**:
- Maintain stop state in LocationTrackerService
- Record speed 5 seconds before entering POTENTIAL_STOP state
- Calculate distance using Haversine formula for tolerance check
- Store sequence number (1st stop, 2nd stop, etc.) for ride analytics

---

## Stop Clustering Algorithm

### Decision: Post-Ride DBSCAN-Inspired Clustering

**Rationale**:
- **Accuracy**: Groups stops at same intersection reliably
- **Performance**: Runs after ride completion (doesn't impact tracking)
- **Simplicity**: Straightforward distance-based grouping
- **Incremental Updates**: Existing clusters updated with new stops

**Clustering Logic**:
```
For each stop in completed ride:
  1. Find all existing clusters within 10m of stop location
  2. If matching cluster found:
     - Add stop to cluster
     - Recalculate cluster centroid (weighted average)
     - Update avg/median duration and stop count
  3. Else:
     - Create new cluster with this stop as seed
```

**Parameters**:
- **Clustering Radius**: 10 meters (typical intersection size)
- **Centroid Calculation**: Weighted average of all stop locations in cluster
- **Metrics**: Average duration, median duration, stop count

**Alternatives Considered**:
1. **Real-Time Clustering**
   - ❌ Adds complexity during ride tracking
   - ❌ Partial ride data leads to inaccurate clusters
   - ✅ Could show "this is a known problem intersection" during ride

2. **Grid-Based Clustering**
   - ❌ Arbitrary grid boundaries split natural clusters
   - ❌ Doesn't adapt to actual intersection locations
   - ✅ Faster for very large datasets

3. **Hierarchical Clustering**
   - ❌ Overkill for this use case
   - ❌ More complex implementation
   - ✅ Better for multi-level analysis

**Implementation Notes**:
- Run clustering as background job after ride completes
- Use SQLite's geospatial functions if available, else Haversine formula
- Store cluster ID references in Stop table for quick lookup
- Recompute affected clusters when stops are deleted

---

## Dependency Injection Architecture

### Decision: Hilt (Dagger Wrapper for Android)

**Rationale**:
- **Android Standard**: Official recommendation for Android DI
- **Compile-Time Safety**: Errors caught at build time, not runtime
- **Lifecycle Aware**: Integrates with Android components (Activity, Service, ViewModel)
- **Scoping**: Clear scopes (Singleton, Activity, Service) prevent memory leaks
- **Reduced Boilerplate**: Hilt annotations simpler than raw Dagger

**Alternatives Considered**:
1. **Dagger 2 (Raw)**
   - ✅ More control and flexibility
   - ❌ Significantly more boilerplate
   - ❌ Steeper learning curve
   - ⚖️ Same performance as Hilt (Hilt is Dagger underneath)

2. **Koin**
   - ✅ Simple, idiomatic Kotlin DSL
   - ❌ Runtime resolution (errors found at runtime)
   - ❌ Slightly worse performance (negligible for our scale)
   - ✅ Easier for small projects

3. **Manual Dependency Injection**
   - ❌ Doesn't scale well
   - ❌ Boilerplate code in every class
   - ❌ Hard to test
   - ✅ No additional dependencies

**Implementation Notes**:
- Use `@HiltAndroidApp` annotation on Application class
- Create `DatabaseModule` for Room database and DAOs
- Create `LocationModule` for FusedLocationProviderClient
- Inject repositories into use cases
- Use `@Singleton` scope for database and location client

---

## Coroutines & Threading Strategy

### Decision: Kotlin Coroutines with Structured Concurrency

**Rationale**:
- **Non-Blocking**: Database and location operations don't block main thread
- **Cancellation**: Properly handles cancellation when rides are stopped
- **Lifecycle Integration**: Works seamlessly with Android lifecycle
- **Readability**: Sequential async code is easier to understand than callbacks
- **Error Handling**: Try-catch works naturally with suspend functions

**Threading Model**:
- **Main Thread**: UI updates only (not used in this feature, but important for future)
- **IO Dispatcher**: Database operations (Room DAOs)
- **Default Dispatcher**: CPU-intensive work (clustering calculations)
- **LocationCallback**: Runs on background thread provided by FusedLocationProviderClient

**Alternatives Considered**:
1. **RxJava**
   - ❌ Steeper learning curve
   - ❌ More verbose for simple async operations
   - ✅ Powerful operators for complex streams
   - ⚖️ Similar capabilities for our needs

2. **AsyncTask (Deprecated)**
   - ❌ Deprecated in Android API 30
   - ❌ Less flexible than coroutines
   - ❌ Lifecycle issues

3. **Java Executors**
   - ❌ More boilerplate
   - ❌ Harder to cancel operations
   - ❌ No structured concurrency
   - ✅ No additional dependencies

**Implementation Notes**:
- Use `viewModelScope` for future UI layer
- Use `GlobalScope.launch` sparingly (only for true app-lifetime operations)
- Always specify dispatcher explicitly: `withContext(Dispatchers.IO) { ... }`
- Use `Flow` for observing database changes (Room has built-in Flow support)

---

## Error Handling Strategy

### Decision: Crash Isolation with Incomplete Ride Discard

**Rationale**:
- **Data Integrity**: Better to lose one ride than corrupt entire database
- **Simplicity**: No complex recovery logic for v1.0
- **User Experience**: Clean slate after crash is better than partial/corrupted data
- **Implementation**: Mark ride with null endTime; cleanup on next app start

**Error Scenarios**:
1. **App Force-Closed**: Incomplete ride discarded
2. **Battery Death**: Incomplete ride discarded
3. **GPS Permission Revoked**: Stop tracking, notify user, discard ride
4. **Storage Full**: Warn before starting ride, discard if occurs mid-ride
5. **Database Corruption**: Log error, attempt recovery, worst case: reset database

**Alternatives Considered**:
1. **Attempt Recovery**
   - ❌ Complex logic for partial ride reconstruction
   - ❌ Risk of corrupted data affecting analysis
   - ✅ Saves some user data

2. **Checkpoint System**
   - ❌ Overhead of frequent checkpointing
   - ❌ Still can't guarantee perfect recovery
   - ✅ Could save partial ride data

**Implementation Notes**:
- Check for rides with null endTime on app startup → delete them
- Log all errors to Logcat for debugging
- Show user-friendly error messages (not technical details)
- Implement proper try-catch in all database and location operations

---

## Performance Optimizations

### Database Indexing Strategy

**Indexes to Create**:
1. **RidePoint.rideId** - Foreign key lookup for loading ride details
2. **Stop.rideId** - Foreign key lookup for loading stops
3. **Stop.lat + Stop.lon** - Geospatial queries for clustering
4. **Ride.startTime** - Chronological sorting of ride list

**Rationale**: These indexes cover all common query patterns while minimizing write overhead.

### Query Optimization

**Best Practices**:
- Use `@Transaction` annotation for multi-table queries
- Fetch only required columns with `@Query` projections
- Implement pagination for ride list (Load 20 at a time)
- Use `Flow` for reactive queries (auto-update UI when data changes)

### Battery Optimization

**Strategies**:
- Request location updates only when ride is active
- Stop location updates immediately when ride ends
- Use lowest acceptable location update priority (HIGH_ACCURACY needed for cycling)
- Batch database writes when possible

---

## Security & Privacy Considerations

### Data Privacy

**Approach**:
- All data stored locally on device (no cloud sync in v1.0)
- No PII (Personally Identifiable Information) collected beyond GPS coordinates
- User in full control of their data (manual deletion)
- No analytics or telemetry in v1.0

### Permissions

**Required**:
- `ACCESS_FINE_LOCATION`: Required for GPS tracking
- `FOREGROUND_SERVICE`: Required for background location tracking (API 28+)
- `POST_NOTIFICATIONS`: Required for foreground service notification (API 33+)

**Runtime Permission Handling**:
- Request location permission before starting first ride
- Show rationale dialog explaining why permission is needed
- Handle "Don't ask again" scenario with Settings redirect

---

## Migration & Versioning Strategy

### v1.0 Approach

**Decision**: No migrations in initial version

**Rationale**:
- First version has no existing user data to migrate
- Schema is stable based on well-defined requirements
- `fallbackToDestructiveMigration()` acceptable for early development

### Future Migration Plan (v1.1+)

**When Needed**:
- Adding new columns to existing tables
- Changing data types
- Adding new relationships

**Strategy**:
- Implement proper Room migrations with `Migration` objects
- Test migrations thoroughly on copy of production database
- Provide fallback: export data before migration, import after if failed

---

## Testing Strategy (v1.1+)

**Deferred to v1.1, but documented for future reference**

### Unit Tests

**Coverage**:
- Domain models: Data validation, state transitions
- Use cases: Business logic, clustering algorithm
- Repository: Data transformation (Entity ↔ Model)

### Instrumentation Tests

**Coverage**:
- Database DAOs: CRUD operations, query accuracy
- Migrations: Schema version transitions
- Stop clustering: Algorithm accuracy with real GPS data

### Manual Testing (v1.0)

**Test Scenarios**:
- Short ride (5 min) with 2-3 stops
- Long ride (1 hour) with 10+ stops
- GPS loss in tunnel
- App force-close during ride
- Low storage scenario

---

## Summary of Key Decisions

| Decision Area | Choice | Primary Rationale |
|---------------|--------|-------------------|
| Database | Room | Type safety, coroutines, Android standard |
| Location | Play Services Fused | Battery optimized, accurate, reliable |
| Background Tracking | Foreground Service | Required for Android 12+, reliable |
| Data Sampling | Smart (significance-based) | 80-95% storage reduction, preserves accuracy |
| Stop Detection | State machine + tolerance | Filters false positives, handles GPS noise |
| Stop Clustering | Post-ride DBSCAN | Simple, accurate, no runtime overhead |
| Dependency Injection | Hilt | Android standard, compile-time safety |
| Async Operations | Kotlin Coroutines | Clean syntax, lifecycle aware, cancellable |
| Error Handling | Discard incomplete rides | Data integrity over recovery complexity |

---

**Research Complete**: All technical decisions documented and justified. Ready for Phase 1 (Design & Contracts).
