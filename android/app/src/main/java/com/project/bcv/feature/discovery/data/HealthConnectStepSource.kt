package com.project.bcv.feature.discovery.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.project.bcv.feature.discovery.domain.StepSource
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Health Connect is the main step source: it already combines every app that records steps
 * (Google Fit, a watch, Health Connect's own phone tracking...) without counting overlaps twice.
 *
 * Only when no app records steps there does a journey fall back to Google Play services' Recording
 * API ([RecordingStepCounter]), and only once the user has allowed physical-activity access. Those
 * steps are never written to Health Connect, so the total is the recorded steps plus this app's own
 * Health Connect records (the "+2.000 bước thử" samples) — no step is read back and counted again.
 * A journey that started on the Recording API keeps it to the end, so its total never drops when the
 * source changes.
 */
class HealthConnectStepSource(context: Context) : StepSource {
    private val appContext = context.applicationContext
    private val readPermission = HealthPermission.getReadPermission(StepsRecord::class)
    private val recording = RecordingStepCounter(appContext)
    private val ownOrigin = DataOrigin(appContext.packageName)
    private var otherSourceCheckedAt = 0L
    private var otherSource = false

    override suspend fun hasPermission(): Boolean =
        canReadHealthConnect() || (RECORDING_FALLBACK && recording.isUsable)

    override suspend fun needsActivityPermission(): Boolean = RECORDING_FALLBACK &&
        recording.isAvailable && !recording.hasPermission() && !hasOtherStepSource()

    override suspend fun capture(journeyId: Long) {
        if (!RECORDING_FALLBACK) return
        if (usesRecording(journeyId)) {
            if (!recording.isTracking(journeyId)) recording.start(journeyId)
        } else {
            recording.stop()
        }
    }

    override suspend fun totalStepsSince(journeyId: Long, startedAtEpochSeconds: Long): Long {
        val startedAt = Instant.ofEpochSecond(startedAtEpochSeconds)
        return if (usesRecording(journeyId)) {
            if (!recording.isTracking(journeyId)) recording.start(journeyId)
            recording.stepsSince(startedAt) + healthConnectSteps(startedAt, onlyThisApp = true)
        } else {
            healthConnectSteps(startedAt, onlyThisApp = false)
        }
    }

    private suspend fun usesRecording(journeyId: Long): Boolean = RECORDING_FALLBACK &&
        (recording.isTracking(journeyId) || (recording.isUsable && !hasOtherStepSource()))

    private suspend fun canReadHealthConnect(): Boolean =
        HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE &&
            runCatching {
                HealthConnectClient.getOrCreate(appContext).permissionController.getGrantedPermissions()
            }.getOrDefault(emptySet()).contains(readPermission)

    /** Whether any app other than BCV recorded steps in Health Connect in the last 30 days (cached briefly). */
    private suspend fun hasOtherStepSource(): Boolean {
        val now = System.currentTimeMillis()
        if (now - otherSourceCheckedAt < SOURCE_CACHE_MS) return otherSource
        otherSource = canReadHealthConnect() && runCatching {
            val end = Instant.now()
            HealthConnectClient.getOrCreate(appContext).aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL),
                TimeRangeFilter.between(end.minus(30, ChronoUnit.DAYS), end)))
                .dataOrigins.any { it.packageName != appContext.packageName }
        }.getOrDefault(false)
        otherSourceCheckedAt = now
        return otherSource
    }

    private suspend fun healthConnectSteps(startedAt: Instant, onlyThisApp: Boolean): Long {
        val now = Instant.now()
        if (!startedAt.isBefore(now) || !canReadHealthConnect()) return 0
        val request = if (onlyThisApp) {
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startedAt, now),
                dataOriginFilter = setOf(ownOrigin))
        } else {
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startedAt, now))
        }
        return HealthConnectClient.getOrCreate(appContext).aggregate(request)[StepsRecord.COUNT_TOTAL] ?: 0L
    }

    private companion object {
        /**
         * true: count walking with the Recording API when no app records steps in Health Connect.
         * false: Health Connect only (nothing is recorded, physical-activity access never asked),
         * e.g. to test Health Connect on its own.
         */
        const val RECORDING_FALLBACK = true
        const val SOURCE_CACHE_MS = 60_000L
    }
}
