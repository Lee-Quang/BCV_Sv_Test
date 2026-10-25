package com.project.bcv.feature.explore.domain

import com.project.bcv.feature.startingpoint.domain.Coordinates

data class ExploreLocation(val regionName: String, val placeName: String?, val coordinates: Coordinates)

data class ExploreJourney(
    val id: Long,
    val destinationName: String,
    val destination: Coordinates,
    val requiredSteps: Int,
    val recordedSteps: Int,
    val status: String,
    val startedAtEpochSeconds: Long,
    /** Where the journey began; the virtual location moves to the destination once it is completed. */
    val start: Coordinates? = null
) {
    val progressPercent: Int
        get() = if (requiredSteps <= 0) 0 else (recordedSteps.toLong() * 100 / requiredSteps).toInt().coerceIn(0, 100)
}

data class ExploreMapPlace(val id: Long, val name: String, val coordinates: Coordinates, val isDiscovered: Boolean)

/** [imageUrls] is filled only once the place is discovered; [imageUrl] is the locked (blurred) preview. */
data class ExplorePlaceDetail(val id: Long, val name: String, val placeType: String,
    val imageUrl: String?, val isDiscovered: Boolean, val distanceKm: Double,
    val requiredSteps: Int, val introduction: String?, val highlights: String?,
    val regionName: String? = null, val imageUrls: List<String> = emptyList())

data class ExploreSnapshot(val location: ExploreLocation?, val journey: ExploreJourney?,
    val places: List<ExploreMapPlace>)

interface ExploreRepository {
    fun load(): ExploreSnapshot
    fun loadPlace(placeId: Long): ExplorePlaceDetail
    fun createJourney(placeId: Long, replaceActive: Boolean): ExploreSnapshot
    fun syncSteps(journeyId: Long, cumulativeSteps: Long): ExploreSnapshot
}

interface StepSource {
    suspend fun hasPermission(): Boolean
    suspend fun totalStepsSince(startedAtEpochSeconds: Long): Long
}
