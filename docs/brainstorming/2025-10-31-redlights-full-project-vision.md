# Redlights - Brainstorming Session
**Feature:** Complete Project Vision & v1.0 MVP Scope
**Date:** October 31, 2025
**Facilitator:** Claude (Scrum Master AI)
**Participants:** Project Owner

---

## 1. Executive Summary

**Purpose:**
Redlights is a bike computer app designed for commuters, recreational cyclists, and delivery riders. Its goal is to identify where riders lose time—specifically at red lights or other forced stops—and help optimize travel routes based on aggregated stop data.

**Core Value Proposition:**
Transform frustrating red-light stops into actionable data that helps cyclists understand where time is lost and optimize their routes accordingly.

**Project Status:**
- **Current State:** Early-stage skeleton with DI infrastructure (Hilt, Room, MapLibre dependencies)
- **Target:** v1.0 MVP focused on core ride tracking and stop detection functionality

---

## 2. Problem Statement

### User Problem
Cyclists lose significant time at traffic lights and forced stops during their rides. Currently, there's no easy way to:
- Quantify time lost at specific locations
- Identify problematic intersections/routes
- Make data-driven decisions about route optimization
- Track improvement over time

### Target Users
1. **Commuters** - Regular routes, need optimization
2. **Recreational Cyclists** - Exploring new routes, want efficient paths
3. **Delivery Riders** - Time-critical, need fastest routes

### Success Criteria
- Users can track rides with live map display
- App accurately detects and logs stops >15 seconds
- Users can view aggregated stop data by location
- App performs efficiently during long rides (1-2 hours)

---

## 3. Feature Breakdown - v1.0 MVP

### 3.1 Core Features (MUST HAVE)

#### Live Ride Tracking ✅
- Real-time position display on OpenStreetMap (via MapLibre)
- 1-second location update frequency
- Map configuration:
  - Top 50% of screen dedicated to map view
  - Auto-follow user location
  - North-up orientation (user direction points up)
  - Zoom level optimized for 20km/h cycling speed
  - Basic map style following device dark/light theme
- Screen stays on and unlocked while app is open during active ride

#### Stop Detection ✅
- Automatic detection when cyclist is stationary
- **Detection parameters:**
  - Speed threshold: <5 km/h = stopped
  - Duration threshold: >15 seconds
  - Tolerance window for brief movements (shuffling forward at lights)
  - Continuous 1-second location updates (same as moving)
- **Data captured per stop:**
  - GPS coordinates (lat/lon)
  - Duration (seconds)
  - Timestamp
  - Associated ride ID
  - Speed before stop (5 seconds prior)
  - Stop sequence number (1st, 2nd, 3rd... in ride)
  - Bearing/heading when stopped
- **Edge case handling:**
  - GPS signal loss: Keep ride active, show warning
  - Poor GPS accuracy: Filter noisy data, show accuracy indicator
  - Brief movements during stop: Use tolerance window, don't reset timer

#### Stop Aggregation & Analysis ✅
- **Post-ride clustering algorithm:**
  - Cluster stops at "approximately same location"
  - Clustering radius: 10 meters
  - Compute metrics per cluster:
    - Average/median stop duration
    - Stop frequency (count)
    - List of stop IDs in cluster
- **Data storage:**
  - Individual stops preserved
  - Cluster metadata stored separately
  - Centroid coordinates for cluster visualization

#### Ride Management ✅
- **Manual start/stop control:**
  - "Start Ride" button on home screen
  - Button becomes "Stop Ride" when ride is active
  - Force-close protection: Prompt "Are you sure? Ride in progress"
- **Ride lifecycle:**
  - User manually starts each ride
  - User manually stops each ride
  - NO automatic destination-based stop detection in v1.0 (deferred to v1.2+)
- **Crash/failure handling:**
  - App crash during ride: Discard incomplete ride
  - Battery death: Discard incomplete ride
  - Battery warning at 15% remaining
  - No recovery of corrupted rides (prioritize data integrity)

#### Data Widgets - Bottom Half of Screen ✅
All displayed during active ride:
- **Current speed** (km/h)
- **Distance traveled** (km)
- **Direction of travel** (compass/bearing)
- **Current stop duration** (if currently stopped)
- **Total stop time** accumulated during ride
- Material Design 3 styling (typography, spacing, colors)

#### Navigation & Screen Structure ✅
- **Bottom Navigation Bar** (MD3 2025 best practice)
  - Home/Map (unified ride view)
  - Rides (past rides history)
  - Stops (stop locations view)
  - Settings
- **Unified Home/Ride Screen:**
  - Top 50%: Live map
  - Bottom 50%: Data widgets
  - "Start Ride" / "Stop Ride" button
  - When no ride active: Map shows last known location
- **Ride Details:**
  - Slide-up panel for individual ride inspection
  - Accessible from Rides history screen
- **Active Ride Behavior:**
  - User CAN navigate to other screens while ride is active
  - Persistent notification: "Ride in progress" + duration
  - Foreground service ensures tracking continues in background

### 3.2 Features Deferred to Future Versions

#### v1.1 - Visualization Enhancements
- Heatmap-style visualization of stop points
- Color intensity reflects average/median stop duration
- Dedicated stop analysis view

#### v1.2+ - Advanced Features
- Destination-based auto-stop detection
  - Set destination before ride
  - Auto-prompt to end ride after >1 min stop near destination
- Route suggestion engine (minimize stop duration)
- Comparison view between rides
- GPX/CSV data export
- Integration with fitness apps (Strava, Komoot)
- Shareable heatmaps/leaderboards
- Auto-pause for extended inactivity

---

## 4. Technical Architecture

### 4.1 Technology Stack
- **Language:** Kotlin
- **UI Framework:** Jetpack Compose + Material Design 3
- **Dependency Injection:** Hilt (Dagger)
- **Database:** Room (SQLite)
- **Location Services:** Google Play Services Location API + FusedLocationProviderClient
- **Mapping:** MapLibre Android SDK (OpenStreetMap)
- **Coroutines:** Kotlinx Coroutines
- **Testing:** Manual testing only for v1.0

### 4.2 Database Schema (Room)

```kotlin
// Entities

@Entity(tableName = "rides")
data class Ride(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val totalDistance: Double,
    val totalStopTime: Long, // seconds
    val stopCount: Int,
    val averageSpeed: Double,
    val maxSpeed: Double
)

@Entity(tableName = "ride_points")
data class RidePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val timestamp: Long,
    val bearing: Float,
    val accuracy: Float
    // NOTE: Smart sampling - only stored when significant change occurs
)

@Entity(tableName = "stops")
data class Stop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val latitude: Double,
    val longitude: Double,
    val duration: Long, // seconds
    val timestamp: Long,
    val speedBeforeStop: Float, // speed 5 seconds before stopping
    val sequenceNumber: Int, // 1st, 2nd, 3rd stop in ride
    val bearing: Float
)

@Entity(tableName = "stop_clusters")
data class StopCluster(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val centroidLatitude: Double,
    val centroidLongitude: Double,
    val averageDuration: Double, // seconds
    val medianDuration: Long, // seconds
    val stopCount: Int,
    val stopIds: String, // JSON array of stop IDs
    val lastUpdated: Long
)
```

### 4.3 Location Service Architecture

**Foreground Service Requirements:**
- Runs as Android Foreground Service (mandatory for continuous background location)
- Persistent notification: "Ride in progress" + duration
- 1-second location update interval (both stopped and moving)
- Continues tracking when app is backgrounded

**Location Update Strategy:**
- Request high-accuracy GPS updates
- Filter out updates with poor accuracy (>50m)
- Apply smart sampling to reduce RidePoint storage:
  - Store point if speed change >2 km/h
  - Store point if bearing change >15°
  - Store point if >30 seconds since last stored point
  - Always store points during stop detection window

**Stop Detection Algorithm:**
```
Pseudocode:
1. Receive location update
2. If speed < 5 km/h:
   - If not currently in "potential stop" state:
     - Enter "potential stop" state
     - Record start time and location
   - Else:
     - Calculate duration since potential stop start
     - Check if movement within tolerance (≤10m from start)
     - If duration > 15s AND within tolerance:
       - Create Stop record
       - Mark as "confirmed stop"
3. If speed >= 5 km/h:
   - If in "potential stop" or "confirmed stop":
     - Exit stop state
     - Record stop end time
```

### 4.4 Post-Ride Processing

**Stop Clustering Algorithm (DBSCAN-inspired):**
```
After ride ends:
1. Fetch all stops for completed ride
2. For each stop:
   - Find all other stops within 10m radius
   - If cluster exists at this location (within 10m of centroid):
     - Add stop to existing cluster
     - Recalculate centroid (weighted average)
     - Update avgDuration, medianDuration, stopCount
   - Else:
     - Create new cluster with this stop as seed
3. Update StopCluster table
```

### 4.5 Dependency Injection Modules (Hilt)

**Existing modules to expand:**
- `DatabaseModule.kt` - Provide Room database, DAOs
- `LocationModule.kt` - Already provides FusedLocationProviderClient ✅
- `AppModule.kt` - Application context ✅

**New modules needed:**
- `MapModule.kt` - Provide MapLibre configuration
- `ServiceModule.kt` - Provide location service dependencies

---

## 5. User Experience & Design

### 5.1 Screen Flow

```
App Launch
    ↓
Home/Ride Screen (Bottom Nav: Home)
    - Top 50%: Map (last location or current if ride active)
    - Bottom 50%: Data widgets OR "Start Ride" button
    ↓
[User taps "Start Ride"]
    ↓
Ride Active State
    - Button changes to "Stop Ride"
    - Map follows user in real-time
    - Widgets update every second
    - Screen stays on
    - Foreground service notification appears
    ↓
[User navigates to "Rides" tab]
    ↓
Past Rides List
    - List of completed rides (newest first)
    - Shows: date, duration, distance, stop count
    - Tap ride → Slide-up detail panel
    ↓
[User navigates to "Stops" tab]
    ↓
Stop Locations View
    - Map with all stop clusters marked
    - (v1.1: Heatmap overlay)
    - Tap cluster → Show aggregate stats
    ↓
[User navigates to "Settings" tab]
    ↓
Settings Screen
    - Theme selection (follow system/force dark/light)
    - Location update frequency (locked at 1s for v1.0)
    - Battery optimization settings
    - Data management (manual delete)
    - About/version info
```

### 5.2 Material Design 3 Requirements

**Typography:**
- Follow MD3 type scale
- High contrast for readability during rides
- Large touch targets (min 48dp) for bike computer use

**Colors:**
- Light theme: MD3 default palette
- Dark theme: MD3 default dark palette
- Automatic theme switching based on system preference

**Layout:**
- Proper spacing (8dp grid system)
- Elevation for overlays (slide-up panels)
- Motion: Standard MD3 transitions

**Accessibility:**
- Minimum contrast ratios for outdoor readability
- Large text option support
- Haptic feedback for ride start/stop

---

## 6. Implementation Strategy

### 6.1 Development Phases

**Phase 1: Foundation (Week 1-2)**
- ✅ Room database setup with all entities
- ✅ DAOs with queries for all CRUD operations
- ✅ Database migration strategy
- ✅ Repository pattern implementation

**Phase 2: Location Service (Week 2-3)**
- ✅ Foreground service implementation
- ✅ Location permission handling (runtime permissions)
- ✅ 1-second location updates with accuracy filtering
- ✅ Stop detection algorithm
- ✅ Smart GPS point sampling logic
- ✅ Battery warning at 15%

**Phase 3: Core UI - Home/Ride Screen (Week 3-4)**
- ✅ Bottom navigation setup (Compose Navigation)
- ✅ MapLibre integration
- ✅ Live map display with user tracking
- ✅ Map auto-follow and north-up orientation
- ✅ Start/Stop button with state management
- ✅ Screen stay-on behavior (keep screen awake flag)

**Phase 4: Data Widgets (Week 4)**
- ✅ Speed display (real-time)
- ✅ Distance calculation and display
- ✅ Direction/bearing display
- ✅ Current stop duration (if stopped)
- ✅ Total stop time counter
- ✅ MD3 styling for all widgets

**Phase 5: Ride State Management (Week 5)**
- ✅ ViewModel for ride state
- ✅ Ride start/stop logic
- ✅ Data persistence during ride
- ✅ Force-close confirmation dialog
- ✅ Foreground notification implementation

**Phase 6: Post-Ride Processing (Week 5-6)**
- ✅ Stop clustering algorithm
- ✅ Cluster centroid calculation
- ✅ Aggregate metrics (avg/median duration, frequency)
- ✅ Update StopCluster table after ride completion

**Phase 7: History Views (Week 6-7)**
- ✅ Past Rides screen with list
- ✅ Ride detail slide-up panel
- ✅ Stop Locations map view
- ✅ Cluster marker display
- ✅ Manual ride deletion

**Phase 8: Polish & Edge Cases (Week 7-8)**
- ✅ GPS signal loss handling + warning UI
- ✅ Poor accuracy indicator
- ✅ Crash recovery (discard incomplete rides)
- ✅ Settings screen implementation
- ✅ Dark/light theme switching
- ✅ Performance optimization

### 6.2 Testing Strategy

**v1.0 Testing:**
- Manual testing only
- Real-world ride testing on device
- Test scenarios:
  - Short rides (5-10 min) with 2-3 stops
  - Medium rides (20-30 min) with 5-8 stops
  - Long rides (1-2 hours) with 10+ stops
  - GPS loss scenarios (tunnels)
  - App backgrounding during ride
  - Battery drain monitoring
  - Stop detection accuracy at various speeds

**Future Testing (post-v1.0):**
- Unit tests for stop detection algorithm
- Unit tests for clustering logic
- Instrumented tests for database operations
- UI tests for critical user flows

---

## 7. Risk Assessment & Mitigation

### 7.1 Technical Risks

| Risk | Impact | Probability | Mitigation Strategy |
|------|--------|-------------|---------------------|
| Battery drain from continuous GPS | High | High | Implement smart sampling, use BALANCED_POWER_ACCURACY mode, warn at 15% battery |
| GPS drift causing false stop detection | Medium | Medium | Implement tolerance window, filter noisy data, show accuracy indicator |
| Large database size after many rides | Medium | Low | Use smart GPS point sampling, implement efficient indexing, consider compression |
| MapLibre performance issues | Medium | Low | Use appropriate zoom levels, limit visible markers, implement marker clustering |
| Location service killed by system | High | Medium | Use Foreground Service (highest priority), handle service restart gracefully |
| Stop clustering performance | Low | Low | Run clustering as background job post-ride, optimize DBSCAN algorithm |

### 7.2 UX Risks

| Risk | Impact | Probability | Mitigation Strategy |
|------|--------|-------------|---------------------|
| Users forget to stop ride | High | High | Prominent "Stop Ride" button, persistent notification, battery warning prompts user |
| Screen readability in sunlight | Medium | Medium | High contrast colors, large text, brightness boost option in settings |
| Map not following user | Medium | Low | Clear auto-follow indicator, easy re-center button |
| Stop detection too sensitive | High | Medium | Tunable parameters (future setting), clear feedback when stop is detected |

### 7.3 Scope Risks

| Risk | Impact | Probability | Mitigation Strategy |
|------|--------|-------------|---------------------|
| Feature creep delaying v1.0 | High | Medium | Strict MVP scope, defer heatmap and advanced features to v1.1+ |
| Underestimating clustering complexity | Medium | Low | Start with simple DBSCAN, iterate based on real data |
| OpenStreetMap tile availability | Low | Low | Use reliable tile provider (MapTiler, Maptoolkit), implement tile caching |

---

## 8. Future Enhancements (Post-v1.0)

### v1.1 - Visualization (Target: 2-3 weeks post-v1.0)
- Heatmap overlay for stop locations
- Color intensity based on avg stop duration
- Interactive cluster exploration
- Stop statistics dashboard

### v1.2 - Smart Ride Management (Target: 1 month post-v1.0)
- Destination-based ride ending
  - User sets destination before ride
  - Auto-prompt to end ride if stopped >1 min near destination
- Auto-pause for extended stops (>5 minutes)
- Ride templates for frequent routes

### v1.3 - Data Export & Integration (Target: 2 months post-v1.0)
- GPX export for ride tracks
- CSV export for stop data
- Strava integration (upload rides)
- Komoot integration (import routes)

### v1.4 - Route Optimization (Target: 3 months post-v1.0)
- Route suggestion engine
- "Avoid high-stop areas" routing
- Alternative route comparison
- Historical time-of-day analysis

### v2.0 - Community Features (Target: 6 months post-v1.0)
- Anonymous aggregate stop data sharing
- City-wide heatmaps
- Leaderboards (fewest stops, fastest routes)
- Social features (share rides, compete with friends)

---

## 9. Success Metrics & KPIs

### v1.0 Launch Metrics
- **Stability:** <1% crash rate during rides
- **Accuracy:** >90% of actual stops detected, <5% false positives
- **Performance:** <10% battery drain per hour of riding
- **Usability:** Users can complete a full ride cycle (start → ride → stop → view) without assistance

### Post-Launch Metrics (Future)
- Daily active users (DAU)
- Average rides per user per week
- Ride completion rate (started vs. stopped properly)
- Average ride duration
- Stop cluster accuracy (user feedback)
- App retention rate (1 week, 1 month)

---

## 10. Open Questions & Decisions Needed

### Resolved in Brainstorming Session ✅
- ✅ MVP scope (no heatmap in v1.0)
- ✅ Navigation pattern (bottom nav bar)
- ✅ Stop detection parameters (1s updates, <5 km/h, 15s threshold)
- ✅ Clustering approach (10m radius, post-ride)
- ✅ Map configuration (OpenStreetMap, auto-follow, north-up)
- ✅ Testing strategy (manual only for v1.0)
- ✅ Edge case handling (GPS loss, crashes, battery)
- ✅ Data storage approach (smart sampling)

### Future Decisions (Post-v1.0)
- Tile provider selection for OpenStreetMap (MapTiler vs. Maptoolkit vs. self-hosted)
- Heatmap visualization library (custom vs. third-party)
- Cloud backup strategy (if any)
- Monetization approach (free vs. freemium vs. paid)

---

## 11. Action Items & Next Steps

### Immediate Actions (This Week)
1. ✅ **Resolve build issues:**
   - Fix resource compilation errors (mipmap PNG files)
   - Fix R8 minification errors (ProGuard rules for OkHttp security providers)
   - Ensure clean build before starting development

2. **Start Phase 1 - Foundation:**
   - Define Room entities (Ride, RidePoint, Stop, StopCluster)
   - Create DAOs with all necessary queries
   - Implement Repository pattern
   - Write database migration strategy
   - Set up Hilt modules (DatabaseModule)

### Sprint Planning (Week 1)
- **Sprint Goal:** Complete database foundation + location service skeleton
- **Stories:**
  - US-001: As a developer, I need a Room database with all entities defined
  - US-002: As a developer, I need DAOs for CRUD operations on rides and stops
  - US-003: As a developer, I need a location service that can track GPS at 1s intervals
  - US-004: As a developer, I need a stop detection algorithm that identifies stops >15s

### Definition of Done (v1.0)
- [ ] User can start a ride with one tap
- [ ] App tracks location continuously at 1-second intervals
- [ ] Map displays user location in real-time
- [ ] Data widgets show accurate speed, distance, direction, stop duration
- [ ] App detects stops >15 seconds with <5 km/h threshold
- [ ] User can stop a ride with one tap
- [ ] Stops are clustered by 10m proximity post-ride
- [ ] User can view past rides in history
- [ ] User can view stop locations on map
- [ ] App shows persistent notification during active ride
- [ ] Screen stays on during active ride
- [ ] App handles GPS loss gracefully with warning
- [ ] App warns user at 15% battery
- [ ] App confirms before force-closing during active ride
- [ ] Clean build with no errors (fix current build issues first)
- [ ] Manual testing on real device shows <10% battery/hour
- [ ] All MD3 design guidelines followed

---

## 12. Appendices

### A. Technology Justifications

**Why MapLibre over Google Maps?**
- Open-source commitment
- No API costs
- Full control over styling and tiles
- Better privacy (no Google tracking)

**Why Room over raw SQLite?**
- Compile-time SQL query verification
- Cleaner API with coroutines support
- Built-in migration support
- Type-safe queries

**Why Hilt over manual DI?**
- Standard Android DI solution
- Compile-time validation
- Scope management (singleton, activity, service)
- Better testability

### B. Glossary

- **Stop:** A detected period of cyclist inactivity >15 seconds with speed <5 km/h
- **Stop Cluster:** A group of stops occurring within 10m of each other, likely representing the same traffic light or intersection
- **Ride Point:** A GPS coordinate snapshot stored during a ride (with smart sampling)
- **Centroid:** The geographic center point of a stop cluster
- **Bearing:** Compass direction in degrees (0° = North, 90° = East, etc.)
- **Foreground Service:** An Android service that runs with high priority and displays a persistent notification

### C. References

- [Material Design 3 Guidelines](https://m3.material.io/)
- [MapLibre Android Documentation](https://maplibre.org/maplibre-native/android/api/)
- [Android Location Services](https://developer.android.com/training/location)
- [Room Persistence Library](https://developer.android.com/training/data-storage/room)
- [Hilt Dependency Injection](https://developer.android.com/training/dependency-injection/hilt-android)
- [Foreground Services Guide](https://developer.android.com/develop/background-work/services/foreground-services)

---

## Session Metadata

**Duration:** 8 progressive question rounds
**Methodology:** Adaptive questioning with deep analysis between responses
**Outcome:** Comprehensive project vision with clear v1.0 scope and implementation roadmap

**Key Decisions Made:**
1. MVP scope strictly limited (no heatmap, no export, manual start/stop)
2. Bottom navigation bar for primary navigation
3. Unified home/ride screen (not separate screens)
4. Post-ride clustering (not real-time)
5. Smart GPS sampling (not all points)
6. Foreground service for background tracking
7. Manual testing only for v1.0
8. OpenStreetMap via MapLibre
9. Material Design 3 compliance mandatory
10. No automatic ride ending in v1.0 (deferred to v1.2+)

---

**Next Steps:**
1. Fix current build errors (resource compilation, R8 minification)
2. Create Archon project and task breakdown
3. Begin Phase 1 implementation (database foundation)

**Document Status:** ✅ Complete
**Ready for Implementation:** ✅ Yes (pending build fixes)
