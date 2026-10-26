package com.project.bcv.feature.discovery.data

import android.content.Context
import com.project.bcv.core.steps.DeviceStepTracker
import com.project.bcv.core.steps.StepCounterService
import com.project.bcv.feature.discovery.domain.ExploreJourney
import com.project.bcv.feature.discovery.domain.StepSource
import java.io.IOException

class SensorStepSource(context: Context) : StepSource {
    private val context = context.applicationContext
    private val tracker = DeviceStepTracker.get(this.context)
    override val usesDeviceSensor = true

    override suspend fun hasPermission() = DeviceStepTracker.hasPermission(context)

    override fun track(journey: ExploreJourney?) {
        if (journey?.status != "IN_PROGRESS") {
            stopTracking()
            return
        }
        val changed = tracker.track(journey.id, journey.startedAtEpochSeconds, journey.syncedSteps.toLong())
        StepCounterService.start(context, resetListener = changed)
    }

    override fun stopTracking() = DeviceStepTracker.stop(context)

    override suspend fun totalStepsSince(startedAtEpochSeconds: Long): Long {
        tracker.failure?.let { throw IOException(it) }
        val state = tracker.state()
        check(state.active && state.startedAtEpochSeconds == startedAtEpochSeconds) { "Bộ đếm bước chưa sẵn sàng cho hành trình này." }
        return state.totalSteps
    }
}
