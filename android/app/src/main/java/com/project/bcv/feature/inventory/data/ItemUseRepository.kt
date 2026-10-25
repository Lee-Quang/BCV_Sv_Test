package com.project.bcv.feature.inventory.data

import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.data.local.SessionPreferences
import org.json.JSONObject

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
class ItemUseRepository(private val remote: JsonApiClient, private val session: SessionPreferences) {
    private fun token() = session.token
        ?: throw ApiFailure("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")

    fun shoes() = remote.get("api/items/speed-shoes", token()).toShoes()

    fun useShoes() = remote.post("api/items/speed-shoes/use", JSONObject(), token()).toShoes()

    fun flight(): FlightTicketStatus {
        val json = remote.get("api/items/flight-ticket", token())
        val regions = json.getJSONArray("regions")
        return FlightTicketStatus(
            json.getString("name"), json.getBoolean("active"), json.getInt("quantity"),
            json.getBoolean("hasActiveJourney"), json.optString("currentRegion").takeUnless { json.isNull("currentRegion") },
            (0 until regions.length()).map { index ->
                regions.getJSONObject(index).let { RegionOption(it.getLong("regionId"), it.getString("name"), it.getInt("placeCount")) }
            }
        )
    }

    fun places(regionId: Long): List<FlightPlace> {
        val places = remote.get("api/items/flight-ticket/regions/$regionId/places", token()).getJSONArray("places")
        return (0 until places.length()).map { index ->
            places.getJSONObject(index).let { FlightPlace(it.getLong("placeId"), it.getString("name"), it.getBoolean("discovered")) }
        }
    }

    fun useFlight(placeId: Long): FlightResult {
        val json = remote.post("api/items/flight-ticket/use", JSONObject().put("placeId", placeId), token())
        return FlightResult(json.getString("region"), json.getString("place"), json.getInt("quantity"))
    }

    private fun JSONObject.toShoes() = SpeedShoesStatus(
        getString("name"), getBoolean("active"), getInt("quantity"), getDouble("multiplier"), getInt("range"),
        optJSONObject("journey")?.let {
            ShoesJourney(it.getLong("journeyId"), it.getString("destinationName"), it.getInt("recordedSteps"), it.getInt("requiredSteps"))
        },
        optJSONObject("effect")?.let { ShoesEffect(it.getDouble("multiplier"), it.getInt("range"), it.getInt("remainingSteps")) }
    )
}
