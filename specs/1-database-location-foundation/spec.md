# Feature Specification: Database & Location Infrastructure

**Status**: Draft
**Created**: 2025-10-31
**Last Updated**: 2025-10-31
**Feature ID**: 1
**Branch**: 1-database-location-foundation
**Owner**: Development Team

---

## Overview

### Problem Statement

Cyclists using Redlights need a reliable system to store and retrieve their ride data, including every point tracked during a ride, stops encountered, and aggregated stop patterns. Without proper data persistence, users cannot:

- Review their past rides and analyze patterns
- Build up historical data about problematic intersections
- Track their progress in optimizing routes over time
- Trust that their ride data will survive app restarts or device issues

Additionally, the app must continuously track the cyclist's position with high accuracy to enable real-time ride monitoring and stop detection. This requires a location tracking system that works reliably even when the app is not the active foreground application.

### Target Users

1. **Commuters** - Need reliable storage of daily ride patterns to identify route optimizations
2. **Recreational Cyclists** - Want to review and compare different routes they've explored
3. **Delivery Riders** - Require consistent data collection across multiple daily rides

### Success Criteria

1. **Data Persistence**: 100% of completed rides are saved successfully and can be retrieved without data loss
2. **Location Accuracy**: Position updates maintain accuracy within 10 meters for 95% of outdoor riding conditions
3. **Location Update Frequency**: Position updates occur at 1-second intervals with no more than 5% variation during active rides
4. **Data Retrieval Performance**: Users can access their ride history and stop data within 1 second of request
5. **Storage Efficiency**: A 2-hour ride with 20 stops consumes no more than 5MB of device storage
6. **Battery Impact**: Location tracking during a 1-hour ride consumes no more than 10% of device battery
7. **Background Reliability**: Location tracking continues uninterrupted when user switches to other apps or locks screen

---

## User Scenarios & Testing

### Primary Scenario: First Ride Data Collection

**User**: Sarah, a daily commuter

**Flow**:
1. Sarah starts her first ride using Redlights
2. As she cycles, the app continuously records her position every second
3. When she stops at a red light for 20 seconds, the app records this as a stop event
4. She continues riding, encountering 5 more traffic lights
5. After 25 minutes, Sarah arrives at work and stops her ride
6. All ride data (route points and stops) are saved permanently
7. Sarah can immediately view her completed ride in the ride history
8. The next day, Sarah can still access yesterday's ride data

**Expected Outcomes**:
- Ride shows accurate route on map
- All 6 stops are recorded with correct locations and durations
- Total distance and duration match Sarah's actual trip
- Data remains accessible across app sessions

### Alternative Scenario 1: Multiple Rides Per Day

**User**: Marco, a delivery rider

**Flow**:
1. Marco completes 8 separate delivery rides throughout his workday
2. Each ride is tracked and stored independently
3. At the end of the day, Marco reviews all 8 rides
4. Stop data from all rides contributes to identifying problematic intersections
5. Marco can distinguish between different rides based on timestamps and routes

**Expected Outcomes**:
- All 8 rides appear as separate entries in history
- No data from one ride interferes with another
- Cumulative stop data builds useful patterns

### Alternative Scenario 2: Background Tracking Reliability

**User**: Lisa, recreational cyclist

**Flow**:
1. Lisa starts a ride and receives a phone call
2. She switches to the phone app for a 3-minute conversation
3. During the call, location tracking continues in the background
4. After the call, she returns to Redlights
5. The ride continues seamlessly with no gaps in the route

**Expected Outcomes**:
- No data loss during the phone call
- Route shows continuous tracking with no missing segments
- Any stops during the call are still detected

### Edge Case 1: App Force-Closed During Ride

**Situation**: User accidentally force-quits the app mid-ride

**Expected Behavior**:
- Incomplete ride data is discarded (not saved)
- No corrupted or partial rides appear in history
- User sees a clean slate on next app launch
- No data integrity issues

**Rationale**: Prioritizes data quality over quantity - better to lose one incomplete ride than risk corrupted data affecting analysis

### Edge Case 2: Device Storage Nearly Full

**Situation**: User's device has less than 100MB free storage

**Expected Behavior**:
- App detects low storage before starting a new ride
- User receives clear warning: "Low storage - ride recording may fail"
- User can choose to continue or free up space
- If user continues and storage runs out mid-ride, ride is discarded cleanly

### Edge Case 3: GPS Signal Loss in Tunnel

**Situation**: Cyclist enters a tunnel with no GPS signal

**Expected Behavior**:
- Last known position is maintained
- No location updates are stored during signal loss
- User sees visual indicator that GPS signal is weak/lost
- When signal returns, tracking resumes normally
- Ride remains valid with gap in the route

### Edge Case 4: Extremely Long Ride (>4 hours)

**Situation**: User goes on an all-day cycling tour

**Expected Behavior**:
- Location tracking continues for entire duration
- Data sampling prevents excessive storage consumption
- Ride completes successfully when user stops
- All stops throughout the day are recorded

---

## Functional Requirements

### Core Requirements

#### FR1: Ride Data Persistence
- System must store each ride with unique identifier, start time, end time, total distance, stop count, and speed metrics
- Ride data must survive app crashes, device restarts, and OS updates
- System must support storing minimum of 500 rides before considering cleanup
- Data must be retrievable in chronological order (newest first)

#### FR2: Route Point Storage
- System must store geographic coordinates for each recorded position during a ride
- Each position must include: latitude, longitude, speed, timestamp, bearing, and GPS accuracy
- System must associate each position with its parent ride
- Storage must be optimized to store only significant position changes (not every update)
- System must store a position when:
  - Speed changes by more than 2 km/h from last stored point
  - Bearing changes by more than 15 degrees from last stored point
  - More than 30 seconds have elapsed since last stored point
  - User is in a detected stop state

#### FR3: Stop Event Recording
- System must store each detected stop with: location, duration, timestamp, and associated ride
- Each stop must include: speed before stopping, sequence number within ride, and direction of travel
- Stops must be permanently linked to their originating ride
- System must support minimum 50 stops per ride

#### FR4: Stop Pattern Aggregation
- System must group stops that occur within 10 meters of each other
- For each stop cluster, system must calculate: average duration, median duration, total occurrence count
- Stop clusters must be updated when new stops are added from subsequent rides
- System must maintain references to individual stops within each cluster

#### FR5: Location Tracking Capability
- System must obtain device position updates every 1 second
- System must filter out position updates with accuracy worse than 50 meters
- System must continue tracking when app is in background
- System must show persistent notification during background tracking
- System must request highest available GPS accuracy

#### FR6: Data Query Performance
- Retrieving list of all rides must complete within 1 second
- Retrieving single ride with all positions and stops must complete within 2 seconds
- Retrieving all stop clusters must complete within 1 second
- Database queries must not block the user interface

#### FR7: Storage Space Management
- System must calculate and display total storage used by ride data
- Users must be able to manually delete old rides
- Deleting a ride must also delete all associated positions and stops
- System must check available storage before starting a new ride

### Optional Requirements

#### FR8: Data Export Capability (Future)
- System should support exporting ride data in standard formats
- Export should include all ride details, positions, and stops

#### FR9: Data Backup (Future)
- System should support backing up ride data to cloud storage
- Users should be able to restore data on new devices

---

## Data & Entities

### Key Entities

#### Ride
Represents a single cycling session from start to finish.

**Attributes**:
- Unique identifier
- Start date and time
- End date and time (null if ride still active)
- Total distance traveled (kilometers)
- Total time spent stopped (seconds)
- Number of stops encountered
- Average speed (km/h)
- Maximum speed reached (km/h)

**Lifecycle**: Created when user starts a ride, finalized when user stops ride or ride is discarded

#### Route Position
Represents a specific geographic point captured during a ride.

**Attributes**:
- Unique identifier
- Reference to parent ride
- Latitude coordinate
- Longitude coordinate
- Speed at this point (km/h)
- Timestamp when captured
- Direction of travel (degrees)
- GPS accuracy (meters)

**Lifecycle**: Created during active ride based on sampling rules, permanent once ride completes

#### Stop Event
Represents a period when cyclist was stationary for more than 15 seconds.

**Attributes**:
- Unique identifier
- Reference to parent ride
- Latitude of stop location
- Longitude of stop location
- Duration of stop (seconds)
- Timestamp when stop began
- Speed before stopping (km/h)
- Sequence number (1st stop, 2nd stop, etc. in this ride)
- Direction cyclist was traveling (degrees)

**Lifecycle**: Created when stop is detected during ride, permanent once ride completes

#### Stop Cluster
Represents an aggregation of stops that occur at approximately the same location.

**Attributes**:
- Unique identifier
- Center point latitude
- Center point longitude
- Average stop duration (seconds)
- Median stop duration (seconds)
- Total number of stops at this location
- List of stop identifiers in this cluster
- Last update timestamp

**Lifecycle**: Created when first stop at a location is recorded, updated when additional stops occur nearby

### Data Relationships

1. **Ride to Route Positions**: One ride contains many route positions (typically 100-7200 for a 1-2 hour ride after sampling)
2. **Ride to Stops**: One ride contains many stop events (typically 3-20 stops per ride)
3. **Stop Cluster to Stops**: One cluster aggregates many stops from different rides at similar locations
4. **Referential Integrity**: Deleting a ride must cascade delete all its positions and stops

---

## Constraints & Assumptions

### Assumptions

1. **Device Capability**: Users have Android devices with GPS capability and minimum Android 12 (API 31)
2. **Storage Availability**: Users maintain at least 100MB free storage for ride data
3. **Battery Capacity**: Users accept battery drain during active tracking as necessary trade-off
4. **Network Independence**: Location tracking does not require internet connection (GPS only)
5. **Single Active Ride**: Only one ride can be active at a time
6. **Data Retention**: Users are responsible for managing their ride history storage
7. **Position Sampling**: Storing every GPS point is unnecessary - sampling based on significance provides adequate route reproduction

### Technical Constraints

1. **Android Permissions**: Users must grant precise location permission for app to function
2. **Background Restrictions**: Modern Android versions limit background services - must use foreground service
3. **GPS Accuracy**: Indoor or urban canyon environments may reduce GPS accuracy to 20-50 meters
4. **Battery Optimization**: Aggressive battery optimization settings may interfere with background tracking
5. **Storage Limits**: Ride data accumulates over time - users with hundreds of rides may need cleanup

### Business Constraints

1. **Privacy**: All ride data stays on device - no cloud storage in v1.0
2. **Timeline**: Must be implemented as first feature before any UI work can proceed
3. **No External Dependencies**: Cannot rely on third-party services for data storage

---

## Out of Scope

**This feature does NOT include:**

1. User interface for viewing rides or stops (separate feature)
2. Map visualization of routes (separate feature)
3. Real-time ride statistics display (separate feature)
4. Automatic ride detection (manual start/stop only)
5. Cloud synchronization or backup
6. Data export to external formats
7. Integration with other cycling apps
8. Social features or sharing capabilities
9. Route planning or navigation
10. Stop detection algorithm (logic covered, but separate feature will implement)

---

## Dependencies

### Internal Dependencies

**None** - This is the foundational feature that other features depend on.

### External Dependencies

1. **Android Platform Services**:
   - Location Services API for GPS access
   - Storage access for database files
   - Foreground Service framework for background tracking

2. **Device Hardware**:
   - GPS receiver for position tracking
   - Sufficient storage space (minimum 100MB recommended)
   - Battery capacity to sustain continuous GPS use

---

## Acceptance Criteria

- [ ] User can start a ride and all position data is captured at 1-second intervals
- [ ] User can stop a ride and all data is permanently saved
- [ ] User can view list of all completed rides with basic details (date, distance, duration)
- [ ] A ride with 100 position points and 10 stops stores and retrieves all data accurately
- [ ] Ride data survives app restart - previously completed rides remain accessible
- [ ] Location tracking continues when user switches to another app
- [ ] Location tracking continues when user locks screen
- [ ] Persistent notification shows during background tracking
- [ ] Poor GPS accuracy (>50m) is filtered out
- [ ] Stop events capture all required data (location, duration, sequence, bearing)
- [ ] Stops occurring within 10 meters of each other aggregate into same cluster
- [ ] Stop cluster statistics (average, median, count) calculate correctly
- [ ] User can manually delete a ride and all associated data is removed
- [ ] Database queries for ride list complete in under 1 second
- [ ] Storage consumption for 2-hour ride is under 5MB
- [ ] App shows clear warning when device storage is low
- [ ] Incomplete ride (from app crash or battery death) does not appear in history
- [ ] System supports storing minimum 500 rides without performance degradation

