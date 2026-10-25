package com.project.bcv.feature.journey.domain

import java.util.Date

/** [status]: IN_PROGRESS, COMPLETED or ABANDONED; only completed journeys have [completedAt]. */
data class JourneyRecord(
    val id: Long,
    val status: String,
    val start: String,
    val destinationId: Long,
    val destination: String,
    val startedAt: Date?,
    val completedAt: Date?
)

interface JourneyHistoryRepository {
    /** Newest first by start time. */
    fun history(): List<JourneyRecord>
}
