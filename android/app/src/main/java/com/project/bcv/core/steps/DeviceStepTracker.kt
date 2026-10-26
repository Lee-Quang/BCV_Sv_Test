package com.project.bcv.core.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat

class DeviceStepTracker private constructor(context: Context) {
    private val preferences = context.getSharedPreferences("device_steps", Context.MODE_PRIVATE)
    @Volatile private var current = DeviceStepCounterState(
        journeyId = preferences.getLong("journey_id", 0),
        startedAtEpochSeconds = preferences.getLong("started_at", 0),
        totalSteps = preferences.getLong("total_steps", 0),
        lastSensorSteps = preferences.getLong("last_sensor_steps", -1).takeIf { it >= 0 },
        bootCount = preferences.getInt("boot_count", -1),
        lastEventNanos = preferences.getLong("last_event_nanos", 0),
        active = preferences.getBoolean("active", false)
    )
    @Volatile var failure: String? = null
        internal set

    fun state() = current

    @Synchronized fun track(journeyId: Long, startedAt: Long, syncedSteps: Long): Boolean {
        val changed = current.journeyId != journeyId || current.startedAtEpochSeconds != startedAt
        save(current.forJourney(journeyId, startedAt, syncedSteps))
        return changed
    }

    @Synchronized fun record(rawSteps: Long, bootCount: Int, eventNanos: Long, occurredAt: Long) {
        save(current.reading(rawSteps, bootCount, eventNanos, occurredAt))
    }

    @Synchronized fun stop() {
        save(current.stopped())
        failure = null
    }

    private fun save(state: DeviceStepCounterState) {
        if (state == current) return
        // Sensor callbacks run on the service's worker thread. Persist each delta before publishing it.
        check(preferences.edit()
            .putLong("journey_id", state.journeyId)
            .putLong("started_at", state.startedAtEpochSeconds)
            .putLong("total_steps", state.totalSteps)
            .putLong("last_sensor_steps", state.lastSensorSteps ?: -1)
            .putInt("boot_count", state.bootCount)
            .putLong("last_event_nanos", state.lastEventNanos)
            .putBoolean("active", state.active)
            .commit()) { "Không lưu được số bước trên thiết bị." }
        current = state
    }

    companion object {
        @Volatile private var instance: DeviceStepTracker? = null

        fun get(context: Context): DeviceStepTracker = instance ?: synchronized(this) {
            instance ?: DeviceStepTracker(context.applicationContext).also { instance = it }
        }

        fun isSupported(context: Context): Boolean =
            context.getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        fun hasPermission(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

        fun stop(context: Context) {
            get(context).stop()
            context.stopService(android.content.Intent(context, StepCounterService::class.java))
        }
    }
}
