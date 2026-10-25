package com.project.bcv.feature.questitem.inventory.domain

data class ShoesJourney(val id: Long, val destination: String, val recordedSteps: Int, val requiredSteps: Int)

/** A boost started on the current journey; it still applies while [remainingSteps] > 0. */
data class ShoesEffect(val multiplier: Double, val range: Int, val remainingSteps: Int)

data class SpeedShoesStatus(
    val name: String,
    val active: Boolean,
    val quantity: Int,
    val multiplier: Double,
    val range: Int,
    val journey: ShoesJourney?,
    val effect: ShoesEffect?
)

data class RegionOption(val id: Long, val name: String, val placeCount: Int)

data class FlightTicketStatus(
    val name: String,
    val active: Boolean,
    val quantity: Int,
    val hasActiveJourney: Boolean,
    val currentRegion: String?,
    val regions: List<RegionOption>
)

data class FlightPlace(val id: Long, val name: String, val discovered: Boolean)

data class FlightResult(val region: String, val place: String, val quantity: Int)

/** Speed shoes and flight tickets: what the user may do with them now, and using one. */
interface ItemUseRepository {
    fun shoes(): SpeedShoesStatus
    fun useShoes(): SpeedShoesStatus
    fun flight(): FlightTicketStatus
    fun places(regionId: Long): List<FlightPlace>
    /** Moves the virtual location to the place and unlocks it there. */
    fun useFlight(placeId: Long): FlightResult
}
