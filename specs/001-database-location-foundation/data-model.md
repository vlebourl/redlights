# Data Model: Database & Location Infrastructure

**Feature**: 001-database-location-foundation
**Date**: 2025-10-31
**Purpose**: Define database entities, relationships, validation rules, and state transitions

---

## Overview

This feature implements 4 core entities representing the data model for ride tracking and stop analysis:

1. **Ride** - A complete cycling session from start to finish
2. **RidePoint** - GPS coordinates captured during a ride (sampled)
3. **Stop** - A period when cyclist was stationary for >15 seconds
4. **StopCluster** - Aggregation of stops occurring at approximately the same location

**Architecture Pattern**: Clean Architecture with separate domain models and database entities

---

## Entity Definitions

### 1. Ride Entity

**Purpose**: Represents a complete cycling session with summary metrics

**Database Table**: `rides`

#### Fields

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | Long | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| `startTime` | Long | NOT NULL | Unix timestamp (milliseconds) when ride started |
| `endTime` | Long | NULLABLE | Unix timestamp when ride ended (null if active/incomplete) |
| `totalDistance` | Double | NOT NULL, >= 0 | Total distance traveled in kilometers |
| `totalStopTime` | Long | NOT NULL, >= 0 | Total time spent stopped in seconds |
| `stopCount` | Int | NOT NULL, >= 0 | Number of stops detected during ride |
| `averageSpeed` | Double | NOT NULL, >= 0 | Average speed in km/h (excluding stops) |
| `maxSpeed` | Double | NOT NULL, >= 0 | Maximum speed reached in km/h |

#### Validation Rules

- `startTime` must be <= current time
- `endTime` must be > `startTime` (if not null)
- `totalDistance` calculated from RidePoints, not user-editable
- `averageSpeed` = totalDistance / (duration - totalStopTime)
- `maxSpeed` derived from max speed across all RidePoints
- Incomplete rides (endTime = null) are deleted on app startup

#### State Transitions

```
[NEW] → startTime set, endTime = null, other fields = 0
  ↓
[ACTIVE] → GPS points added, metrics updated in real-time
  ↓
[COMPLETED] → endTime set, all metrics finalized
  ↓
[PERSISTED] → Saved permanently, available in history
```

#### Business Rules

- Only ONE ride can have `endTime = null` at any time (single active ride)
- Rides with `endTime = null` that are >24 hours old are considered abandoned and deleted
- Cannot delete a ride while it's active (must stop first)
- Deleting a ride CASCADE deletes all associated RidePoints and Stops

#### Indexes

- `CREATE INDEX idx_ride_start_time ON rides(startTime DESC)` - For chronological sorting

---

### 2. RidePoint Entity

**Purpose**: Stores GPS coordinates captured during a ride (smart sampled)

**Database Table**: `ride_points`

#### Fields

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | Long | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| `rideId` | Long | FOREIGN KEY → rides(id), NOT NULL | Parent ride |
| `latitude` | Double | NOT NULL, -90 to 90 | Latitude coordinate |
| `longitude` | Double | NOT NULL, -180 to 180 | Longitude coordinate |
| `speed` | Float | NOT NULL, >= 0 | Speed at this point in km/h |
| `timestamp` | Long | NOT NULL | Unix timestamp (milliseconds) |
| `bearing` | Float | NOT NULL, 0 to 360 | Direction of travel in degrees (0 = North) |
| `accuracy` | Float | NOT NULL, > 0 | GPS accuracy in meters |

#### Validation Rules

- `latitude` must be between -90 and 90 degrees
- `longitude` must be between -180 and 180 degrees
- `speed` must be >= 0 km/h
- `bearing` must be 0-360 degrees (0 = North, 90 = East, 180 = South, 270 = West)
- `accuracy` must be <= 50 meters (filtered during capture)
- `timestamp` must be >= parent ride's `startTime`
- `timestamp` must be <= parent ride's `endTime` (if ride completed)

#### Sampling Logic (Application Layer)

A RidePoint is stored if ANY of these conditions are met:
1. Speed changed by >2 km/h from last stored point
2. Bearing changed by >15 degrees from last stored point
3. More than 30 seconds elapsed since last stored point
4. User is currently in CONFIRMED_STOP state

**Not stored in database table**, but maintained in memory:
- Last stored RidePoint for comparison
- All raw GPS updates (processed but not all persisted)

#### Business Rules

- RidePoints are immutable once stored
- Cannot add RidePoints to completed rides
- Points stored in chronological order (timestamp ascending)
- Minimum 2 points required for distance calculation

#### Indexes

- `CREATE INDEX idx_ride_point_ride_id ON ride_points(rideId)` - For loading all points of a ride
- `CREATE INDEX idx_ride_point_timestamp ON ride_points(timestamp)` - For chronological queries

---

### 3. Stop Entity

**Purpose**: Records periods when cyclist was stationary for >15 seconds

**Database Table**: `stops`

#### Fields

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | Long | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| `rideId` | Long | FOREIGN KEY → rides(id), NOT NULL | Parent ride |
| `latitude` | Double | NOT NULL, -90 to 90 | Latitude of stop location |
| `longitude` | Double | NOT NULL, -180 to 180 | Longitude of stop location |
| `duration` | Long | NOT NULL, >= 15 | Duration of stop in seconds |
| `timestamp` | Long | NOT NULL | Unix timestamp when stop began |
| `speedBeforeStop` | Float | NOT NULL, >= 0 | Speed 5 seconds before stopping (km/h) |
| `sequenceNumber` | Int | NOT NULL, >= 1 | Stop number within this ride (1st, 2nd, 3rd...) |
| `bearing` | Float | NOT NULL, 0 to 360 | Direction cyclist was traveling before stop |

#### Validation Rules

- `duration` must be >= 15 seconds (detection threshold)
- `latitude` must be between -90 and 90 degrees
- `longitude` must be between -180 and 180 degrees
- `speedBeforeStop` must be >= 0 km/h
- `bearing` must be 0-360 degrees
- `timestamp` must be >= parent ride's `startTime`
- `timestamp` must be <= parent ride's `endTime` (if ride completed)
- `sequenceNumber` must be unique within a ride

#### State Machine (Application Layer)

```
GPS Update Received
  ↓
Is speed < 5 km/h?
  YES → Is already in POTENTIAL_STOP?
         NO → Enter POTENTIAL_STOP, record start time/location
         YES → Check duration and tolerance:
                Duration > 15s AND moved <10m?
                  YES → Create Stop record, enter CONFIRMED_STOP
                  NO → Continue monitoring
  NO → Is in POTENTIAL_STOP or CONFIRMED_STOP?
        YES → Exit stop state, finalize Stop if needed
        NO → Continue normal tracking
```

#### Business Rules

- Stops are immutable once recorded
- Cannot manually add/edit stops (auto-detected only)
- Deleting a ride CASCADE deletes all its stops
- Stops feed into clustering algorithm post-ride
- `sequenceNumber` auto-increments for each new stop in a ride

#### Indexes

- `CREATE INDEX idx_stop_ride_id ON stops(rideId)` - For loading all stops of a ride
- `CREATE INDEX idx_stop_location ON stops(latitude, longitude)` - For geospatial clustering queries

---

### 4. StopCluster Entity

**Purpose**: Aggregates stops occurring at approximately the same location across all rides

**Database Table**: `stop_clusters`

#### Fields

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | Long | PRIMARY KEY, AUTO_INCREMENT | Unique identifier |
| `centroidLatitude` | Double | NOT NULL, -90 to 90 | Center point latitude |
| `centroidLongitude` | Double | NOT NULL, -180 to 180 | Center point longitude |
| `averageDuration` | Double | NOT NULL, >= 0 | Average stop duration in seconds |
| `medianDuration` | Long | NOT NULL, >= 0 | Median stop duration in seconds |
| `stopCount` | Int | NOT NULL, >= 1 | Number of stops in this cluster |
| `stopIds` | String | NOT NULL, JSON array | JSON array of stop IDs (e.g., "[1,5,12,23]") |
| `lastUpdated` | Long | NOT NULL | Unix timestamp of last update |

#### Validation Rules

- `centroidLatitude` must be between -90 and 90 degrees
- `centroidLongitude` must be between -180 and 180 degrees
- `stopCount` must match number of IDs in `stopIds` JSON array
- `stopIds` must be valid JSON array of Long values
- `averageDuration` = sum(durations) / stopCount
- `medianDuration` = median of all durations in cluster

#### Clustering Algorithm (Post-Ride Processing)

```
For each Stop in completed Ride:
  1. Query existing StopClusters within 10m radius
  2. If matching cluster found:
     a. Add Stop.id to cluster's stopIds JSON array
     b. Recalculate centroid: weighted average of all stop locations
     c. Recalculate averageDuration: mean of all stop durations
     d. Recalculate medianDuration: median of all stop durations
     e. Increment stopCount
     f. Update lastUpdated to current timestamp
  3. If no matching cluster:
     a. Create new StopCluster
     b. Set centroid to Stop's location
     c. Set averageDuration = medianDuration = Stop.duration
     d. Set stopCount = 1
     e. Set stopIds = "[Stop.id]"
```

**Centroid Calculation**:
```kotlin
newCentroidLat = (oldCentroidLat * oldStopCount + newStopLat) / (oldStopCount + 1)
newCentroidLon = (oldCentroidLon * oldStopCount + newStopLon) / (oldStopCount + 1)
```

#### Business Rules

- Clusters are mutable (updated as new stops are added)
- Clustering runs as background job after ride completion
- Clustering radius: 10 meters (Haversine distance formula)
- Deleting a stop triggers cluster recalculation (remove from stopIds, recalc metrics)
- Empty clusters (stopCount = 0) are deleted
- No limit on cluster size (can grow indefinitely)

#### Indexes

- `CREATE INDEX idx_cluster_location ON stop_clusters(centroidLatitude, centroidLongitude)` - For geospatial queries
- `CREATE INDEX idx_cluster_count ON stop_clusters(stopCount DESC)` - For "most frequent stop" queries

---

## Relationships

### Entity Relationship Diagram

```
┌──────────────┐
│     Ride     │ (1)
│ ------------ │
│ id (PK)      │
│ startTime    │
│ endTime      │
│ ...          │
└──────┬───────┘
       │
       │ 1:N (CASCADE DELETE)
       │
       ├───────────────────────────┐
       │                           │
       ▼                           ▼
┌──────────────┐           ┌──────────────┐
│  RidePoint   │ (N)       │     Stop     │ (N)
│ ------------ │           │ ------------ │
│ id (PK)      │           │ id (PK)      │
│ rideId (FK)  │           │ rideId (FK)  │
│ latitude     │           │ latitude     │
│ longitude    │           │ longitude    │
│ ...          │           │ duration     │
└──────────────┘           │ ...          │
                           └──────┬───────┘
                                  │
                                  │ N:1 (REFERENCE, NO CASCADE)
                                  │
                                  ▼
                           ┌──────────────────┐
                           │   StopCluster    │ (1)
                           │ ---------------- │
                           │ id (PK)          │
                           │ centroidLat      │
                           │ centroidLon      │
                           │ stopIds (JSON)   │
                           │ ...              │
                           └──────────────────┘
```

### Relationship Details

1. **Ride → RidePoint** (1:N)
   - One ride has many GPS points
   - Foreign key: `RidePoint.rideId → Ride.id`
   - Cascade: DELETE (deleting ride deletes all its points)
   - Typical cardinality: 100-7200 points per ride

2. **Ride → Stop** (1:N)
   - One ride has many stops
   - Foreign key: `Stop.rideId → Ride.id`
   - Cascade: DELETE (deleting ride deletes all its stops)
   - Typical cardinality: 3-20 stops per ride

3. **Stop → StopCluster** (N:1, soft reference)
   - Many stops belong to one cluster
   - Implemented via: `StopCluster.stopIds` JSON array contains `Stop.id` values
   - NOT a foreign key constraint (stops can exist without being clustered yet)
   - Cascade: None (deleting a stop requires manual cluster update)
   - Cardinality: 1-∞ stops per cluster

### Referential Integrity

**Enforced by Database**:
- `RidePoint.rideId` must reference valid `Ride.id`
- `Stop.rideId` must reference valid `Ride.id`
- Cascade deletes for Ride → RidePoint, Ride → Stop

**Enforced by Application**:
- `StopCluster.stopIds` consistency (no orphaned IDs)
- Cluster metrics accuracy (avg/median match stop durations)
- Single active ride constraint (only one ride with endTime = null)

---

## Domain Models (Clean Architecture)

### Separation from Database Entities

**Why Separate?**
- Domain models contain business logic, entities are pure data
- Domain layer has no Android/Room dependencies (testable in JVM)
- Allows future database changes without breaking business logic
- Follows Clean Architecture principles

### Domain Model Definitions

```kotlin
// Domain Layer (pure Kotlin, no Android)

data class Ride(
    val id: Long,
    val startTime: Instant,
    val endTime: Instant?,
    val totalDistance: Distance, // Wrapper class for km/miles
    val totalStopTime: Duration,
    val stopCount: Int,
    val averageSpeed: Speed, // Wrapper class
    val maxSpeed: Speed
) {
    fun isActive(): Boolean = endTime == null
    fun duration(): Duration = Duration.between(startTime, endTime ?: Instant.now())
    fun movingDuration(): Duration = duration() - totalStopTime
}

data class RidePoint(
    val id: Long,
    val rideId: Long,
    val location: Location, // Wrapper for lat/lon
    val speed: Speed,
    val timestamp: Instant,
    val bearing: Bearing, // Wrapper for degrees
    val accuracy: Distance
)

data class Stop(
    val id: Long,
    val rideId: Long,
    val location: Location,
    val duration: Duration,
    val timestamp: Instant,
    val speedBeforeStop: Speed,
    val sequenceNumber: Int,
    val bearing: Bearing
)

data class StopCluster(
    val id: Long,
    val centroid: Location,
    val averageDuration: Duration,
    val medianDuration: Duration,
    val stopCount: Int,
    val stopIds: List<Long>,
    val lastUpdated: Instant
) {
    fun contains(stopId: Long): Boolean = stopIds.contains(stopId)
    fun isWithinRadius(location: Location, radiusMeters: Double): Boolean {
        return centroid.distanceTo(location) <= radiusMeters
    }
}
```

### Mapping Between Layers

**Data Layer → Domain Layer**:
- Repositories handle mapping from entities to domain models
- Convert Unix timestamps (Long) to Instant
- Convert primitive Double/Float to wrapper classes (Speed, Distance, etc.)
- Parse JSON strings to List<Long> for stopIds

**Domain Layer → Data Layer**:
- Use cases return domain models
- Repositories map domain models to entities before saving
- Convert Instant to Long, wrapper classes to primitives

---

## Validation & Constraints Summary

### Ride Validation
- ✅ Unique active ride (only one with endTime = null)
- ✅ startTime <= current time
- ✅ endTime > startTime (if not null)
- ✅ All metrics >= 0
- ✅ Calculated fields match source data

### RidePoint Validation
- ✅ Valid GPS coordinates (-90 to 90 lat, -180 to 180 lon)
- ✅ Accuracy <= 50 meters (filtered at capture)
- ✅ Speed >= 0
- ✅ Bearing 0-360 degrees
- ✅ Timestamp within parent ride timeframe

### Stop Validation
- ✅ Duration >= 15 seconds
- ✅ Valid GPS coordinates
- ✅ Unique sequenceNumber within ride
- ✅ Timestamp within parent ride timeframe
- ✅ Speed and bearing valid ranges

### StopCluster Validation
- ✅ stopCount matches stopIds array length
- ✅ Average and median correctly calculated
- ✅ All stopIds reference valid Stop records
- ✅ Centroid within valid coordinate ranges

---

## Performance Considerations

### Query Optimization

**Fast Queries** (< 1 second):
- Get all rides: `SELECT * FROM rides ORDER BY startTime DESC LIMIT 20`
- Get ride summary: `SELECT * FROM rides WHERE id = ?`
- Get stop count: Uses indexed `stopCount` field, no join needed

**Medium Queries** (< 2 seconds):
- Get ride with all points: Join with pagination
- Get ride with all stops: Join with indexed rideId
- Get clusters near location: Indexed geospatial query

**Background Queries** (no time limit):
- Clustering algorithm: Runs post-ride asynchronously
- Stop aggregation: Can take several seconds for large datasets

### Storage Estimates

**Single 1-Hour Ride**:
- Ride: 1 record × 64 bytes = 64 bytes
- RidePoints: 200 points × 56 bytes = 11,200 bytes (~11 KB)
- Stops: 8 stops × 64 bytes = 512 bytes
- **Total per ride**: ~12 KB

**500 Rides** (target capacity):
- Rides: 500 × 64 bytes = 32,000 bytes (32 KB)
- RidePoints: 500 × 200 × 56 bytes = 5,600,000 bytes (~5.6 MB)
- Stops: 500 × 8 × 64 bytes = 256,000 bytes (256 KB)
- StopClusters: ~100 clusters × 128 bytes = 12,800 bytes (13 KB)
- **Total database size**: ~6 MB (well within target)

### Index Impact

**Benefits**:
- 10-100x faster queries for common operations
- Sub-second response times for ride list/details

**Costs**:
- ~10-15% storage overhead for indexes
- Slightly slower writes (negligible for our write volume)

---

## Data Model Complete

**Phase 1 Status**: Data model fully defined
**Next Steps**: Generate repository contracts and quickstart guide
