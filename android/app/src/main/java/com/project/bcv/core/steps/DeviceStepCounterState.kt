package com.project.bcv.core.steps

/** Journey totals are independent of the hardware counter's lifetime and boot cycle. */
data class DeviceStepCounterState(
    val journeyId: Long = 0,
    val startedAtEpochSeconds: Long = 0,
    val totalSteps: Long = 0,
    val lastSensorSteps: Long? = null,
    val bootCount: Int = -1,
    val lastEventNanos: Long = 0,
    val active: Boolean = false
) {
    fun forJourney(id: Long, startedAt: Long, syncedSteps: Long): DeviceStepCounterState {
        val confirmed = syncedSteps.coerceIn(0, Int.MAX_VALUE.toLong())
        return if (id == journeyId && startedAt == startedAtEpochSeconds) {
            copy(totalSteps = maxOf(totalSteps, confirmed), active = true)
        } else DeviceStepCounterState(id, startedAt, confirmed, active = true)
    }

    fun reading(rawSteps: Long, currentBoot: Int, eventNanos: Long, occurredAt: Long): DeviceStepCounterState {
        if (!active || rawSteps < 0) return this
        val rebooted = bootCount >= 0 && currentBoot >= 0 && bootCount != currentBoot
        if (!rebooted && lastSensorSteps != null && eventNanos <= lastEventNanos) return this
        val delta: Long = when {
            lastSensorSteps == null || occurredAt < startedAtEpochSeconds -> 0L
            rebooted -> rawSteps
            else -> (rawSteps - lastSensorSteps).coerceAtLeast(0)
        }
        return copy(
            totalSteps = (totalSteps + delta.coerceAtMost(Int.MAX_VALUE.toLong())).coerceAtMost(Int.MAX_VALUE.toLong()),
            lastSensorSteps = rawSteps,
            bootCount = currentBoot,
            lastEventNanos = eventNanos
        )
    }

    fun stopped() = copy(active = false, lastSensorSteps = null, lastEventNanos = 0)
}
