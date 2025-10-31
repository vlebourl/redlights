# Tasks: Database & Location Infrastructure

**Feature**: 001-database-location-foundation
**Branch**: 001-database-location-foundation
**Input**: Design documents from `/specs/001-database-location-foundation/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: No automated tests for v1.0 - manual testing only (unit tests deferred to v1.1+)

**Organization**: Tasks organized to build complete foundational infrastructure incrementally, with each major component independently testable.

## Format: `[ID] [P?] [Component] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Component]**: Which component this task belongs to (Domain, Data, DI, Location, Use Cases)
- Include exact file paths in descriptions

## Path Conventions

This is an Android mobile app using Clean Architecture:
- **Domain Layer**: `app/src/main/java/com/tiarkaerell/redlights/domain/`
- **Data Layer**: `app/src/main/java/com/tiarkaerell/redlights/data/`
- **DI Layer**: `app/src/main/java/com/tiarkaerell/redlights/di/`

---

## Phase 1: Setup (Project Infrastructure)

**Purpose**: Project initialization and dependency configuration

- [X] T001 Add Room 2.6+ dependency to app/build.gradle.kts
- [X] T002 Add Hilt 2.48+ dependency and annotation processing to app/build.gradle.kts
- [X] T003 Add Google Play Services Location 21.0+ dependency to app/build.gradle.kts
- [X] T004 Add Kotlinx Coroutines 1.7+ dependency to app/build.gradle.kts
- [X] T005 Add @HiltAndroidApp annotation to application class
- [X] T006 Configure Hilt compilation in app/build.gradle.kts (kapt plugin)
- [X] T007 Add required permissions to AndroidManifest.xml (ACCESS_FINE_LOCATION, FOREGROUND_SERVICE, POST_NOTIFICATIONS)
- [X] T008 Create package structure per plan.md (domain/, data/, di/ directories)

**Checkpoint**: Build succeeds with all dependencies resolved

---

## Phase 2: Domain Layer (Pure Business Logic)

**Purpose**: Define domain models and contracts (no Android dependencies)

**⚠️ CRITICAL**: Domain models must be complete before Data Layer implementation

### Domain Models

- [X] T009 [P] [Domain] Create Ride.kt model in app/src/main/java/com/tiarkaerell/redlights/domain/model/Ride.kt with all fields per data-model.md
- [X] T010 [P] [Domain] Create RidePoint.kt model in app/src/main/java/com/tiarkaerell/redlights/domain/model/RidePoint.kt with all fields per data-model.md
- [X] T011 [P] [Domain] Create Stop.kt model in app/src/main/java/com/tiarkaerell/redlights/domain/model/Stop.kt with all fields per data-model.md
- [X] T012 [P] [Domain] Create StopCluster.kt model in app/src/main/java/com/tiarkaerell/redlights/domain/model/StopCluster.kt with all fields per data-model.md

**Checkpoint**: Domain models compile with no Android dependencies

---

## Phase 3: Data Layer - Database (Room Implementation)

**Purpose**: Implement Room database with entities, DAOs, and database class

### Database Entities

- [X] T013 [P] [Data] Create RideEntity.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/database/entities/RideEntity.kt with @Entity annotation, all fields, and toDomainModel() mapping
- [X] T014 [P] [Data] Create RidePointEntity.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/database/entities/RidePointEntity.kt with @Entity annotation, foreign key to rides table, indexes
- [X] T015 [P] [Data] Create StopEntity.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/database/entities/StopEntity.kt with @Entity annotation, foreign key to rides table, geospatial indexes
- [X] T016 [P] [Data] Create StopClusterEntity.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/database/entities/StopClusterEntity.kt with @Entity annotation and JSON stopIds field

### Data Access Objects (DAOs)

- [X] T017 [P] [Data] Create RideDao.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/database/dao/RideDao.kt with CRUD operations per RideRepository contract
- [X] T018 [P] [Data] Create RidePointDao.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/database/dao/RidePointDao.kt with insert, query by rideId, and get last point operations
- [X] T019 [P] [Data] Create StopDao.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/database/dao/StopDao.kt with insert, query by rideId, and geospatial query operations
- [X] T020 [P] [Data] Create StopClusterDao.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/database/dao/StopClusterDao.kt with cluster CRUD, geospatial queries, and analytics operations

### Database Configuration

- [X] T021 [Data] Create RedlightsDatabase.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/database/RedlightsDatabase.kt with @Database annotation, version 1, all entities, and DAO getters
- [X] T022 [Data] Add database builder configuration in RedlightsDatabase.kt (fallbackToDestructiveMigration for v1.0, enableMultiInstanceInvalidation, setJournalMode WAL)

**Checkpoint**: Database compiles and can be instantiated (test with simple insert/query)

---

## Phase 4: Data Layer - Location Services

**Purpose**: Implement GPS tracking with FusedLocationProviderClient

### Location Tracking Core

- [X] T023 [Data] Create LocationTracker.kt interface in app/src/main/java/com/tiarkaerell/redlights/data/local/location/LocationTracker.kt defining location tracking contract
- [X] T024 [Data] Implement DefaultLocationTracker in app/src/main/java/com/tiarkaerell/redlights/data/local/location/DefaultLocationTracker.kt with FusedLocationProviderClient
- [X] T025 [Data] Add permission checking methods (hasLocationPermission, isLocationEnabled) to DefaultLocationTracker
- [X] T026 [Data] Implement startLocationUpdates() in DefaultLocationTracker with LocationRequest (1Hz, PRIORITY_HIGH_ACCURACY)
- [X] T027 [Data] Implement accuracy filtering (reject >50m) in DefaultLocationTracker location callback
- [X] T028 [Data] Implement stopLocationUpdates() in DefaultLocationTracker with proper cleanup
- [X] T029 [Data] Add Haversine distance calculation method to DefaultLocationTracker
- [X] T030 [Data] Add bearing calculation method to DefaultLocationTracker

### Foreground Service

- [X] T031 [Data] Create LocationTrackerService.kt in app/src/main/java/com/tiarkaerell/redlights/data/local/location/LocationTrackerService.kt extending Service with @AndroidEntryPoint
- [X] T032 [Data] Implement notification channel creation (IMPORTANCE_LOW) in LocationTrackerService
- [X] T033 [Data] Implement foreground notification builder (persistent, with Stop Ride action) in LocationTrackerService
- [X] T034 [Data] Implement onStartCommand with START_STICKY and startForeground() in LocationTrackerService
- [X] T035 [Data] Integrate DefaultLocationTracker into LocationTrackerService for continuous tracking
- [X] T036 [Data] Implement stop detection state machine (MOVING → POTENTIAL_STOP → CONFIRMED_STOP) in LocationTrackerService
- [X] T037 [Data] Implement smart sampling logic (speed/bearing/time thresholds) in LocationTrackerService
- [X] T038 [Data] Implement onDestroy with proper location cleanup in LocationTrackerService
- [X] T039 [Data] Register LocationTrackerService in AndroidManifest.xml with foregroundServiceType="location"

**Checkpoint**: LocationTrackerService can start, show notification, and receive location updates

---

## Phase 5: Data Layer - Repository Implementations

**Purpose**: Implement repository pattern bridging DAOs and domain models

### Repository Implementations

- [X] T040 [Data] Create RideRepositoryImpl.kt in app/src/main/java/com/tiarkaerell/redlights/data/repository/RideRepositoryImpl.kt implementing RideRepository interface
- [X] T041 [Data] Implement createRide() in RideRepositoryImpl with entity-to-model mapping
- [X] T042 [Data] Implement completeRide() in RideRepositoryImpl with metrics finalization
- [X] T043 [Data] Implement getRideById() and getActiveRide() in RideRepositoryImpl
- [X] T044 [Data] Implement getAllRides() with Flow and pagination in RideRepositoryImpl
- [X] T045 [Data] Implement deleteRide() with cascade deletion in RideRepositoryImpl
- [X] T046 [Data] Implement deleteIncompleteRides() for crash recovery in RideRepositoryImpl
- [X] T047 [Data] Implement addRidePoint(), getRidePoints(), getLastRidePoint() in RideRepositoryImpl
- [X] T048 [Data] Implement addStop(), getStops(), getNextStopSequence() in RideRepositoryImpl
- [X] T049 [Data] Implement updateRideMetrics() in RideRepositoryImpl
- [X] T050 [Data] Implement getTotalStorageUsed() and getRideCount() in RideRepositoryImpl

- [X] T051 [Data] Create LocationRepositoryImpl.kt in app/src/main/java/com/tiarkaerell/redlights/data/repository/LocationRepositoryImpl.kt implementing LocationRepository interface
- [X] T052 [Data] Delegate all location operations to injected LocationTracker in LocationRepositoryImpl

- [X] T053 [Data] Create StopClusterRepositoryImpl.kt in app/src/main/java/com/tiarkaerell/redlights/data/repository/StopClusterRepositoryImpl.kt implementing StopClusterRepository interface
- [X] T054 [Data] Implement processRideStops() with DBSCAN-inspired clustering algorithm in StopClusterRepositoryImpl
- [X] T055 [Data] Implement createCluster() and recalculateCluster() with centroid calculation in StopClusterRepositoryImpl
- [X] T056 [Data] Implement findClustersNear() with geospatial radius query in StopClusterRepositoryImpl
- [X] T057 [Data] Implement getClusterById() and getAllClusters() with Flow in StopClusterRepositoryImpl
- [X] T058 [Data] Implement getClustersWithMinStops() and getClustersWithLongDuration() in StopClusterRepositoryImpl
- [X] T059 [Data] Implement analytics methods (getClusterCount, getMostFrequentCluster, getLongestWaitCluster) in StopClusterRepositoryImpl
- [X] T060 [Data] Implement getClusterStops() in StopClusterRepositoryImpl
- [X] T061 [Data] Implement cluster maintenance operations (deleteCluster, rebuildAllClusters, deleteStaleClusters) in StopClusterRepositoryImpl

**Checkpoint**: All repositories compile and can be instantiated with mock DAOs

---

## Phase 6: Dependency Injection (Hilt Modules)

**Purpose**: Wire up all components with Hilt for dependency injection

### Hilt Modules

- [ ] T062 [P] [DI] Create DatabaseModule.kt in app/src/main/java/com/tiarkaerell/redlights/di/DatabaseModule.kt with @Module and @InstallIn(SingletonComponent::class)
- [ ] T063 [DI] Provide RedlightsDatabase instance in DatabaseModule (@Singleton scope)
- [ ] T064 [P] [DI] Provide RideDao from database in DatabaseModule
- [ ] T065 [P] [DI] Provide RidePointDao from database in DatabaseModule
- [ ] T066 [P] [DI] Provide StopDao from database in DatabaseModule
- [ ] T067 [P] [DI] Provide StopClusterDao from database in DatabaseModule
- [ ] T068 [DI] Bind RideRepositoryImpl to RideRepository interface in DatabaseModule
- [ ] T069 [DI] Bind StopClusterRepositoryImpl to StopClusterRepository interface in DatabaseModule

- [ ] T070 [P] [DI] Create LocationModule.kt in app/src/main/java/com/tiarkaerell/redlights/di/LocationModule.kt with @Module and @InstallIn(SingletonComponent::class)
- [ ] T071 [DI] Provide FusedLocationProviderClient in LocationModule using context
- [ ] T072 [DI] Bind DefaultLocationTracker to LocationTracker interface in LocationModule
- [ ] T073 [DI] Bind LocationRepositoryImpl to LocationRepository interface in LocationModule

**Checkpoint**: App builds with Hilt and all dependencies inject correctly

---

## Phase 7: Domain Layer - Use Cases

**Purpose**: Implement business logic orchestrating repositories

### Use Cases

- [ ] T074 [P] [Use Cases] Create StartRideUseCase.kt in app/src/main/java/com/tiarkaerell/redlights/domain/usecase/StartRideUseCase.kt
- [ ] T075 [Use Cases] Implement invoke() in StartRideUseCase checking permissions, location services, storage, and creating ride
- [ ] T076 [Use Cases] Add foreground service start logic to StartRideUseCase using Intent to LocationTrackerService
- [ ] T077 [Use Cases] Implement error handling and Result type in StartRideUseCase

- [ ] T078 [P] [Use Cases] Create StopRideUseCase.kt in app/src/main/java/com/tiarkaerell/redlights/domain/usecase/StopRideUseCase.kt
- [ ] T079 [Use Cases] Implement invoke() in StopRideUseCase completing active ride and stopping location service
- [ ] T080 [Use Cases] Add foreground service stop logic to StopRideUseCase
- [ ] T081 [Use Cases] Trigger stop clustering after ride completion in StopRideUseCase

- [ ] T082 [P] [Use Cases] Create GetRideHistoryUseCase.kt in app/src/main/java/com/tiarkaerell/redlights/domain/usecase/GetRideHistoryUseCase.kt
- [ ] T083 [Use Cases] Implement invoke() in GetRideHistoryUseCase returning Flow<List<Ride>> with pagination support

- [ ] T084 [P] [Use Cases] Create ClusterStopsUseCase.kt in app/src/main/java/com/tiarkaerell/redlights/domain/usecase/clustering/ClusterStopsUseCase.kt
- [ ] T085 [Use Cases] Implement invoke() in ClusterStopsUseCase calling StopClusterRepository.processRideStops()
- [ ] T086 [Use Cases] Add background coroutine dispatcher (Dispatchers.Default) for CPU-intensive clustering in ClusterStopsUseCase

**Checkpoint**: Use cases compile and can be invoked with mock repositories

---

## Phase 8: Integration & Data Flow

**Purpose**: Connect all components and ensure end-to-end data flow works

### Integration Tasks

- [ ] T087 [Integration] Wire StartRideUseCase to start LocationTrackerService via Intent
- [ ] T088 [Integration] Implement ride data storage in LocationTrackerService using RideRepository
- [ ] T089 [Integration] Implement RidePoint sampling and storage in LocationTrackerService based on smart sampling rules
- [ ] T090 [Integration] Implement Stop detection and storage in LocationTrackerService using stop state machine
- [ ] T091 [Integration] Test complete ride lifecycle: start → track → stops detected → complete → data persisted
- [ ] T092 [Integration] Implement incomplete ride cleanup on app startup (deleteIncompleteRides called from Application.onCreate)
- [ ] T093 [Integration] Verify cascade deletion works (deleting ride removes all points and stops)
- [ ] T094 [Integration] Verify stop clustering runs automatically after ride completion

**Checkpoint**: Complete ride lifecycle works end-to-end with data persisting correctly

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Refinements, optimizations, and final validation

### Performance & Optimization

- [ ] T095 [P] Verify database indexes exist on all foreign keys and frequently queried columns
- [ ] T096 [P] Add @Transaction annotations to multi-table query methods in DAOs
- [ ] T097 Optimize query performance: verify ride list loads in <1s, single ride loads in <2s
- [ ] T098 Test storage efficiency: verify 2-hour ride consumes <5MB

### Error Handling & Robustness

- [ ] T099 [P] Add comprehensive try-catch blocks to all repository methods
- [ ] T100 [P] Add logging (Timber or Android Log) for all critical operations
- [ ] T101 Implement low storage warning check in StartRideUseCase (<100MB free)
- [ ] T102 Test GPS signal loss scenario: verify app doesn't crash in tunnel
- [ ] T103 Test app force-close scenario: verify incomplete ride is discarded

### Documentation & Validation

- [ ] T104 [P] Add KDoc comments to all public methods in repositories and use cases
- [ ] T105 Run through all manual test scenarios from quickstart.md
- [ ] T106 Validate all 18 acceptance criteria from spec.md
- [ ] T107 Verify battery consumption meets <10% per hour target

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - start immediately
- **Domain Layer (Phase 2)**: Depends on Setup - Models needed before entities
- **Database (Phase 3)**: Depends on Domain models - Entities need to map to domain
- **Location Services (Phase 4)**: Depends on Setup - Independent of database
- **Repositories (Phase 5)**: Depends on Database AND Domain - Bridges both layers
- **DI (Phase 6)**: Depends on Repositories - Wires everything together
- **Use Cases (Phase 7)**: Depends on Repositories and DI - Orchestrates business logic
- **Integration (Phase 8)**: Depends on Use Cases - End-to-end flows
- **Polish (Phase 9)**: Depends on Integration - Final refinements

### Critical Path

The fastest path to a working system:

1. **Phase 1** (Setup) → **Phase 2** (Domain) → **Phase 3** (Database) → **Phase 5** (Repositories) → **Phase 6** (DI) → **Phase 7** (Use Cases) → **Phase 8** (Integration)

**Phase 4** (Location Services) can be developed in parallel with Database/Repositories but must complete before Integration.

### Parallel Opportunities

**During Phase 3 (Database)**:
- All Entity files [T013-T016] can be created in parallel
- All DAO files [T017-T020] can be created in parallel after entities exist

**During Phase 4 (Location)**:
- Location tracking core can be built in parallel with database work

**During Phase 5 (Repositories)**:
- After interfaces defined, implementations can proceed in parallel

**During Phase 6 (DI)**:
- Module provider methods can be written in parallel where no dependencies exist

**During Phase 7 (Use Cases)**:
- All use case files [T074, T078, T082, T084] can be created in parallel
- Implementation logic added sequentially

**During Phase 9 (Polish)**:
- Performance testing, documentation, and validation can run in parallel

---

## Parallel Example: Database Layer

```bash
# After Phase 2 completes, launch all entity files together:
Task T013: "Create RideEntity.kt in app/src/main/java/.../entities/RideEntity.kt"
Task T014: "Create RidePointEntity.kt in app/src/main/java/.../entities/RidePointEntity.kt"
Task T015: "Create StopEntity.kt in app/src/main/java/.../entities/StopEntity.kt"
Task T016: "Create StopClusterEntity.kt in app/src/main/java/.../entities/StopClusterEntity.kt"

# After entities compile, launch all DAO files together:
Task T017: "Create RideDao.kt in app/src/main/java/.../dao/RideDao.kt"
Task T018: "Create RidePointDao.kt in app/src/main/java/.../dao/RidePointDao.kt"
Task T019: "Create StopDao.kt in app/src/main/java/.../dao/StopDao.kt"
Task T020: "Create StopClusterDao.kt in app/src/main/java/.../dao/StopClusterDao.kt"
```

---

## Implementation Strategy

### Sequential Approach (Single Developer)

1. Complete Phase 1 (Setup) - 1 day
2. Complete Phase 2 (Domain) - 1 day
3. Complete Phase 3 (Database) - 2-3 days
4. Complete Phase 4 (Location) - 2-3 days
5. Complete Phase 5 (Repositories) - 2-3 days
6. Complete Phase 6 (DI) - 1 day
7. Complete Phase 7 (Use Cases) - 1-2 days
8. Complete Phase 8 (Integration) - 2-3 days
9. Complete Phase 9 (Polish) - 2-3 days

**Total Estimate**: 14-21 days for single developer

### Parallel Approach (Team)

With 2-3 developers:

1. **Together**: Phase 1 (Setup) - 1 day
2. **Together**: Phase 2 (Domain) - 1 day
3. **Parallel**:
   - Dev A: Phase 3 (Database) - 2-3 days
   - Dev B: Phase 4 (Location) - 2-3 days
4. **Sequential**: Phase 5 (Repositories) - 2-3 days (needs both previous phases)
5. **Together**: Phase 6 (DI) - 1 day
6. **Parallel**:
   - Dev A: StartRideUseCase, StopRideUseCase
   - Dev B: GetRideHistoryUseCase, ClusterStopsUseCase
7. **Together**: Phase 8 (Integration) - 2-3 days
8. **Parallel**: Phase 9 (Polish) - different team members test different scenarios

**Total Estimate**: 10-14 days with parallelization

### Incremental Validation

**After Phase 6 (DI Complete)**:
- Test: Can app build? Can repositories be injected?

**After Phase 7 (Use Cases Complete)**:
- Test: Can use cases be invoked? Do they compile?

**After Phase 8 (Integration Complete)**:
- Test: Full ride lifecycle - START THIS TESTING IMMEDIATELY
- Validate: Primary scenario from spec.md works end-to-end

**After Phase 9 (Polish Complete)**:
- Test: All manual scenarios from quickstart.md
- Validate: All 18 acceptance criteria from spec.md

---

## Manual Testing Checklist

### Primary Scenario: First Ride (FR1-FR7)

- [ ] Start ride via StartRideUseCase
- [ ] Verify foreground notification appears
- [ ] Cycle for 5 minutes with 3-5 stops
- [ ] Stop ride via StopRideUseCase
- [ ] Verify ride saved with all data (distance, stops, metrics)
- [ ] Restart app and verify ride still accessible

### Alternative Scenarios

- [ ] **Multiple Rides**: Complete 3 separate rides, verify all saved independently
- [ ] **Background Tracking**: Start ride, switch to another app for 2 minutes, return - verify no data gaps
- [ ] **Phone Call**: Start ride, receive call, verify tracking continues

### Edge Cases

- [ ] **Force Close**: Start ride, force close app, reopen - verify incomplete ride discarded
- [ ] **Low Storage**: Set device to <100MB free, start ride - verify warning shown
- [ ] **GPS Loss**: Enter tunnel during ride - verify app doesn't crash, tracking resumes after
- [ ] **Long Ride**: Run 4+ hour simulation - verify data saved correctly

### Performance Validation

- [ ] Query ride list - must complete in <1 second
- [ ] Query single ride with all data - must complete in <2 seconds
- [ ] Verify 2-hour ride consumes <5MB storage
- [ ] Monitor battery during 1-hour ride - should be <10% drain

---

## Notes

- **[P] tasks**: Different files with no dependencies - can run in parallel
- **Checkpoints**: Stop and validate before proceeding to next phase
- **Manual testing**: No automated tests in v1.0 - follow quickstart.md scenarios
- **Commit frequency**: Commit after completing each phase or logical group
- **Data integrity**: Always test crash recovery (incomplete ride cleanup)
- **Performance**: Continuously monitor query times and storage usage
- **Documentation**: KDoc comments required for all public APIs

---

## Success Criteria (From Spec.md)

This implementation is complete when all 7 success criteria are met:

1. ✅ **Data Persistence**: 100% of completed rides saved and retrievable
2. ✅ **Location Accuracy**: Within 10m for 95% of outdoor conditions (FusedLocationProvider + accuracy filtering)
3. ✅ **Update Frequency**: 1-second intervals with <5% variation (LocationRequest configuration)
4. ✅ **Retrieval Performance**: <1s for ride list, <2s for single ride (Room with indexes)
5. ✅ **Storage Efficiency**: <5MB per 2-hour ride (smart sampling reduces 80-95%)
6. ✅ **Battery Impact**: <10% per hour (FusedLocationProvider optimization)
7. ✅ **Background Reliability**: Foreground service ensures uninterrupted tracking

All 18 acceptance criteria from spec.md must also be validated during Phase 9 (Polish).
