package com.tiarkaerell.redlights.data.local.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tiarkaerell.redlights.R
import com.tiarkaerell.redlights.data.repository.RideRepository
import com.tiarkaerell.redlights.domain.model.RidePoint
import com.tiarkaerell.redlights.domain.model.Stop
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs

/**
 * Foreground service for continuous GPS tracking during rides
 *
 * This service:
 * - Runs as a foreground service with persistent notification
 * - Tracks GPS location at 1 Hz using FusedLocationProviderClient
 * - Implements smart sampling to reduce data storage (80-95% reduction)
 * - Detects stops using a state machine (MOVING → POTENTIAL_STOP → CONFIRMED_STOP)
 * - Stores ride points and stops to database in real-time
 * - Survives app backgrounding and screen off
 *
 * State Machine (T036):
 * ```
 * MOVING: Speed > 1 m/s (3.6 km/h)
 *   ↓ Speed ≤ 1 m/s for 5 seconds
 * POTENTIAL_STOP: Accumulating stationary time
 *   ↓ Stationary for 15+ seconds
 * CONFIRMED_STOP: Stop recorded and stored
 *   ↓ Speed > 1 m/s
 * MOVING: Resume tracking
 * ```
 *
 * Smart Sampling (T037):
 * Only store GPS points when:
 * - Speed change > 2 km/h, OR
 * - Bearing change > 15°, OR
 * - Time since last point > 30 seconds
 *
 * Reduces 3600 points/hour to ~200-700 points/hour depending on route complexity.
 *
 * @property locationTracker GPS tracking implementation
 * @property rideRepository Database access for storing ride data
 */
@AndroidEntryPoint
class LocationTrackerService : Service() {

    @Inject
    lateinit var locationTracker: LocationTracker

    @Inject
    lateinit var rideRepository: RideRepository

    /**
     * Service coroutine scope - cancelled in onDestroy
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Current location tracking job - cancelled when service stops
     */
    private var locationJob: Job? = null

    /**
     * Current active ride ID
     */
    private var currentRideId: Long? = null

    /**
     * Last stored ride point - used for smart sampling comparison
     */
    private var lastStoredPoint: RidePoint? = null

    /**
     * Current stop detection state
     */
    private var stopState: StopDetectionState = StopDetectionState.MOVING

    /**
     * State tracking for potential stop
     */
    private var potentialStopLocation: Location? = null
    private var potentialStopStartTime: Long? = null
    private var speedBeforeStop: Float = 0f

    // ==================== Constants ====================

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        // Stop detection thresholds (T036)
        private const val SPEED_THRESHOLD_MPS = 1.0f // 3.6 km/h
        private const val POTENTIAL_STOP_DURATION_MS = 5000L // 5 seconds
        private const val CONFIRMED_STOP_MIN_DURATION_S = 15L // 15 seconds

        // Smart sampling thresholds (T037)
        private const val SPEED_CHANGE_THRESHOLD_KMH = 2.0f
        private const val BEARING_CHANGE_THRESHOLD_DEG = 15.0f
        private const val TIME_THRESHOLD_MS = 30000L // 30 seconds

        // Actions
        const val ACTION_START_TRACKING = "START_TRACKING"
        const val ACTION_STOP_TRACKING = "STOP_TRACKING"
    }

    /**
     * Stop detection state machine (T036)
     */
    private enum class StopDetectionState {
        MOVING,           // Normal cycling
        POTENTIAL_STOP,   // Speed dropped, accumulating time
        CONFIRMED_STOP    // Stop recorded, waiting to resume
    }

    // ==================== Service Lifecycle ====================

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Handle service start commands (T034)
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> {
                startTracking()
            }
            ACTION_STOP_TRACKING -> {
                stopTracking()
                stopSelf()
            }
        }

        // START_STICKY: Restart service if killed by system (T034)
        return START_STICKY
    }

    /**
     * Clean up resources when service is destroyed (T038)
     */
    override fun onDestroy() {
        super.onDestroy()
        locationJob?.cancel()
        serviceScope.launch {
            locationTracker.stopLocationUpdates()
        }
        serviceScope.cancel()
    }

    // ==================== Notification Management ====================

    /**
     * Create notification channel for foreground service (T032)
     * Required for Android O+ (API 26+)
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Ride Tracking",
                NotificationManager.IMPORTANCE_LOW // Low: No sound, minimal intrusion
            ).apply {
                description = "Ongoing ride tracking and GPS location updates"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Build foreground notification with Stop action (T033)
     */
    private fun buildNotification(): NotificationCompat.Builder {
        // Create stop action intent
        val stopIntent = Intent(this, LocationTrackerService::class.java).apply {
            action = ACTION_STOP_TRACKING
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Recording Ride")
            .setContentText("GPS tracking active")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with actual icon
            .setOngoing(true) // Cannot be dismissed
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_launcher_foreground, // Replace with stop icon
                "Stop Ride",
                stopPendingIntent
            )
    }

    // ==================== Location Tracking ====================

    /**
     * Start location tracking and move service to foreground (T034, T035)
     */
    private fun startTracking() {
        // Create notification channel (T032)
        createNotificationChannel()

        // Start foreground service (T034)
        val notification = buildNotification().build()
        startForeground(NOTIFICATION_ID, notification)

        // Get or create active ride
        serviceScope.launch {
            val activeRide = rideRepository.getActiveRide()
            if (activeRide == null) {
                val rideId = rideRepository.createRide(Instant.now())
                currentRideId = rideId
            } else {
                currentRideId = activeRide.id
            }

            // Start location updates (T035)
            startLocationUpdates()
        }
    }

    /**
     * Start receiving location updates and process them (T035, T036, T037)
     */
    private fun startLocationUpdates() {
        locationJob?.cancel()

        locationJob = serviceScope.launch {
            locationTracker.startLocationUpdates()
                .catch { exception ->
                    // Log error and stop service
                    stopTracking()
                    stopSelf()
                }
                .collect { location ->
                    processLocationUpdate(location)
                }
        }
    }

    /**
     * Process each location update (T036, T037)
     *
     * Flow:
     * 1. Update ride metrics
     * 2. Check stop detection state machine
     * 3. Apply smart sampling to decide if point should be stored
     * 4. Store point if sampling rules met
     */
    private suspend fun processLocationUpdate(location: Location) {
        val rideId = currentRideId ?: return

        // Update stop detection state machine (T036)
        updateStopDetectionState(location, rideId)

        // Apply smart sampling (T037)
        if (shouldStorePoint(location)) {
            storeRidePoint(location, rideId)
        }

        // Update ride metrics
        updateRideMetrics(location, rideId)
    }

    /**
     * Stop detection state machine implementation (T036)
     */
    private suspend fun updateStopDetectionState(location: Location, rideId: Long) {
        val currentTime = System.currentTimeMillis()
        val speed = location.speed // m/s

        when (stopState) {
            StopDetectionState.MOVING -> {
                if (speed <= SPEED_THRESHOLD_MPS) {
                    // Transition to POTENTIAL_STOP
                    stopState = StopDetectionState.POTENTIAL_STOP
                    potentialStopLocation = location
                    potentialStopStartTime = currentTime
                    speedBeforeStop = lastStoredPoint?.speed ?: speed
                }
            }

            StopDetectionState.POTENTIAL_STOP -> {
                val potentialStartTime = potentialStopStartTime ?: return

                if (speed > SPEED_THRESHOLD_MPS) {
                    // False alarm - back to MOVING
                    stopState = StopDetectionState.MOVING
                    potentialStopLocation = null
                    potentialStopStartTime = null
                } else {
                    // Check if we've been stopped long enough
                    val stoppedDuration = currentTime - potentialStartTime
                    if (stoppedDuration >= POTENTIAL_STOP_DURATION_MS) {
                        // Confirm stop if duration >= 15 seconds
                        val durationSeconds = stoppedDuration / 1000
                        if (durationSeconds >= CONFIRMED_STOP_MIN_DURATION_S) {
                            recordStop(rideId, potentialStopLocation!!, durationSeconds)
                            stopState = StopDetectionState.CONFIRMED_STOP
                        }
                    }
                }
            }

            StopDetectionState.CONFIRMED_STOP -> {
                if (speed > SPEED_THRESHOLD_MPS) {
                    // Resume moving
                    stopState = StopDetectionState.MOVING
                    potentialStopLocation = null
                    potentialStopStartTime = null
                }
            }
        }
    }

    /**
     * Record a confirmed stop to the database (T036)
     */
    private suspend fun recordStop(rideId: Long, location: Location, durationSeconds: Long) {
        val sequenceNumber = rideRepository.getNextStopSequence(rideId)

        val stop = Stop(
            id = 0, // Auto-generated
            rideId = rideId,
            latitude = location.latitude,
            longitude = location.longitude,
            duration = durationSeconds,
            timestamp = Instant.ofEpochMilli(location.time),
            speedBeforeStop = speedBeforeStop,
            sequenceNumber = sequenceNumber,
            bearing = location.bearing
        )

        rideRepository.addStop(stop)
    }

    /**
     * Smart sampling decision logic (T037)
     *
     * Store point if ANY of these conditions are true:
     * - Speed changed by >2 km/h
     * - Bearing changed by >15°
     * - More than 30 seconds since last point
     * - This is the first point
     */
    private fun shouldStorePoint(location: Location): Boolean {
        val lastPoint = lastStoredPoint ?: return true // Always store first point

        val timeDiff = location.time - lastPoint.timestamp.toEpochMilli()
        if (timeDiff >= TIME_THRESHOLD_MS) return true

        // Convert m/s to km/h for speed comparison
        val speedKmh = location.speed * 3.6f
        val lastSpeedKmh = lastPoint.speed * 3.6f
        val speedDiff = abs(speedKmh - lastSpeedKmh)
        if (speedDiff > SPEED_CHANGE_THRESHOLD_KMH) return true

        // Bearing change
        if (location.hasBearing()) {
            val bearingDiff = abs(location.bearing - lastPoint.bearing)
            val actualBearingDiff = if (bearingDiff > 180f) 360f - bearingDiff else bearingDiff
            if (actualBearingDiff > BEARING_CHANGE_THRESHOLD_DEG) return true
        }

        return false
    }

    /**
     * Store ride point to database (T037)
     */
    private suspend fun storeRidePoint(location: Location, rideId: Long) {
        val point = RidePoint(
            id = 0, // Auto-generated
            rideId = rideId,
            latitude = location.latitude,
            longitude = location.longitude,
            speed = location.speed,
            timestamp = Instant.ofEpochMilli(location.time),
            bearing = if (location.hasBearing()) location.bearing else 0f,
            accuracy = location.accuracy
        )

        rideRepository.addRidePoint(point)
        lastStoredPoint = point
    }

    /**
     * Update ride metrics based on new location
     */
    private suspend fun updateRideMetrics(location: Location, rideId: Long) {
        // Get last point to calculate distance
        val lastPoint = lastStoredPoint ?: return

        // Calculate distance traveled
        val distance = locationTracker.calculateDistance(
            lastPoint.latitude,
            lastPoint.longitude,
            location.latitude,
            location.longitude
        )

        // Update ride with new metrics
        rideRepository.updateRideMetrics(
            rideId = rideId,
            additionalDistance = distance,
            currentSpeed = location.speed.toDouble() * 3.6, // m/s to km/h
            isMoving = location.speed > SPEED_THRESHOLD_MPS
        )
    }

    /**
     * Stop location tracking (T038)
     */
    private fun stopTracking() {
        locationJob?.cancel()
        locationJob = null

        serviceScope.launch {
            locationTracker.stopLocationUpdates()
        }

        // Reset state
        lastStoredPoint = null
        stopState = StopDetectionState.MOVING
        potentialStopLocation = null
        potentialStopStartTime = null
        currentRideId = null
    }
}
