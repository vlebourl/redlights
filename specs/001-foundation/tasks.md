# Tasks: 001-foundation

## Overview
Implementation tasks for the Redlights Android bike tracking application foundation.
Total: 110 tasks across 8 phases.

## Phase 1: Setup (10 tasks) ✅ COMPLETED

- [X] **T001**: Update gradle/libs.versions.toml with complete dependency catalog
  - Kotlin 2.0.21, Compose BOM 2024.02.00, Hilt 2.50, Room 2.6.1
  - MapLibre 10.3.0, Coroutines 1.7.3, Location Services 21.1.0
  - LeakCanary 2.13, detekt 1.23.4, ktlint 11.6.1, Mockk, Turbine, JUnit5

- [X] **T002**: Update build.gradle.kts (project-level)
  - Add all plugins with apply false
  - Configure ktlint subproject settings
  - Set up detekt configuration

- [X] **T003**: Update settings.gradle.kts
  - Add MapLibre Maven repository
  - Configure repository resolution mode

- [X] **T004**: Update gradle.properties
  - Kotlin compiler options (jvmargs, incremental, caching)
  - R8 full mode optimization
  - KSP incremental compilation settings

- [X] **T005**: Create detekt.yml
  - Comprehensive static analysis rules (600+ lines)
  - Complexity thresholds, naming conventions
  - Coroutines rules, exception handling, style rules

- [X] **T006**: Create .editorconfig
  - ktlint code style rules (4 space indent, 120 max line length)
  - Android Studio code style

- [X] **T007**: Enhance proguard-rules.pro
  - Kotlin, Coroutines, Compose rules
  - Hilt, Room, MapLibre specific rules
  - Logging removal for release builds

- [X] **T008**: Update AndroidManifest.xml
  - Add INTERNET, ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION permissions
  - Add OpenGL ES 2.0 feature requirement for MapLibre
  - Register RedlightsApplication and MainActivity

- [X] **T009**: Update strings.xml
  - Add permission rationale strings
  - Add permission denied strings

- [X] **T010**: Enhance .gitignore
  - Comprehensive Android/Kotlin patterns (113 lines)
  - Build artifacts, IDE files, keystores, environment files

## Phase 2: Foundational (9 tasks) ✅ COMPLETED

- [X] **T011**: Create RedlightsApplication.kt
  - @HiltAndroidApp annotation
  - Mark as `open class` for debug extension
  - Register in AndroidManifest

- [X] **T012**: Create DebugApplication.kt
  - Extend RedlightsApplication
  - Setup LeakCanary configuration
  - Place in src/debug/ source set

- [X] **T013**: Create MainActivity.kt
  - @AndroidEntryPoint annotation
  - Compose setContent with RedlightsApp composable
  - Material Design 3 theme
  - Basic UI with centered "Redlights" text

- [X] **T014**: Create AppModule.kt
  - @Module @InstallIn(SingletonComponent::class)
  - Provide ApplicationContext

- [X] **T015**: Create DatabaseModule.kt
  - Placeholder for future Room implementation
  - Will be implemented in Phase 7

- [X] **T016**: Create LocationModule.kt
  - Provide FusedLocationProviderClient
  - Singleton scope

- [X] **T017**: Run initial build verification
  - Execute ./gradlew build
  - Verify no compilation errors

- [X] **T018**: Fix any build errors
  - Kotlin 2.0 Compose Compiler plugin requirement
  - RedlightsApplication marked as open
  - ProGuard rules for OkHttp optional dependencies

- [X] **T019**: Verify debug APK
  - Build successful: assembleDebug
  - Install and run on emulator
  - Verify app launches without crashes

## Phase 3: US1 - Development Environment Ready (10 tasks) ⏳ PENDING

Details to be determined based on original specification.

## Phase 4: US2 - Clean Architecture Structure (18 tasks) ⏳ PENDING

Details to be determined based on original specification.

## Phase 5: US3 - Material Design 3 Theme System (11 tasks) ⏳ PENDING

Details to be determined based on original specification.

## Phase 6: US4 - Basic Map Display (20 tasks) ⏳ PENDING

Details to be determined based on original specification.

## Phase 7: US5 - Database Schema Ready (17 tasks) ⏳ PENDING

Details to be determined based on original specification.

## Phase 8: Polish & Cross-Cutting Concerns (15 tasks) ⏳ PENDING

Details to be determined based on original specification.

---

## Progress Summary

- **Completed**: 19/110 tasks (17.3%)
- **Phase 1**: 10/10 ✅
- **Phase 2**: 9/9 ✅
- **Phase 3-8**: 0/91 ⏳

## Notes

- Project successfully recovered from data loss incident
- Debug build compiles and runs on emulator
- Foundation is solid and ready for feature implementation
- Phases 3-8 need detailed task breakdown from original specification
