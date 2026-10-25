package com.project.bcv.feature.startingpoint.domain

data class Coordinates(val latitude: Double, val longitude: Double)

data class StartingPoint(
    val regionName: String,
    val placeName: String?,
    val coordinates: Coordinates
)

data class StartingPointOptions(
    val saved: StartingPoint?,
    val suggested: StartingPoint?
)

enum class StartingPointSource { SUGGESTED, CURRENT }

interface StartingPointRepository {
    fun load(): StartingPointOptions
    fun resolveCurrent(coordinates: Coordinates): StartingPoint
    fun save(source: StartingPointSource, coordinates: Coordinates? = null): StartingPoint
}

interface AddressLookup {
    /** A street-level address for display, or null when none is available. */
    fun describe(coordinates: Coordinates): String?
}

interface DeviceLocationSource {
    fun findCurrent(callback: (Coordinates?) -> Unit)
    fun cancel()
}
