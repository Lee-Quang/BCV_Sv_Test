package com.project.bcv.feature.location.startingpoint.data

import com.project.bcv.feature.location.startingpoint.domain.AddressLookup
import com.project.bcv.feature.location.startingpoint.domain.Coordinates
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Reverse geocoding through Goong. Display only: the server still decides the region. */
class GoongAddressLookup(private val apiKey: String) : AddressLookup {
    override fun describe(coordinates: Coordinates): String? {
        if (apiKey.isBlank()) return null
        val latlng = String.format(Locale.US, "%.6f,%.6f", coordinates.latitude, coordinates.longitude)
        val connection = URL("https://rsapi.goong.io/Geocode?latlng=$latlng&api_key=$apiKey")
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            if (connection.responseCode !in 200..299) return null
            val content = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            JSONObject(content).optJSONArray("results")?.optJSONObject(0)
                ?.optString("formatted_address")?.takeIf(String::isNotBlank)
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
