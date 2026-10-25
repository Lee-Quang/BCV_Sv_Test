package com.project.bcv.core.map

import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Loads a road shape for display. The trip's saved step target remains server-owned. */
object GoongDirectionsClient {
    private val cache = LinkedHashMap<String, RoadRoute>(16, 0.75f, true)

    fun route(from: LatLng, to: LatLng, apiKey: String): RoadRoute? {
        if (apiKey.isBlank()) return null
        val origin = coordinate(from)
        val destination = coordinate(to)
        val cacheKey = "$origin|$destination"
        synchronized(cache) { cache[cacheKey]?.let { return it } }
        try {
            val url = URL("https://rsapi.goong.io/v2/direction?origin=$origin" +
                "&destination=$destination&vehicle=bike&api_key=${URLEncoder.encode(apiKey, "UTF-8")}")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/json")
                if (connection.responseCode !in 200..299) return null
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val encoded = JSONObject(body).optJSONArray("routes")?.optJSONObject(0)
                    ?.optJSONObject("overview_polyline")?.optString("points").orEmpty()
                val route = RoadRoute.fromEncodedPolyline(encoded)?.withEndpoints(from, to) ?: return null
                synchronized(cache) {
                    cache[cacheKey] = route
                    if (cache.size > 16) cache.remove(cache.keys.first())
                }
                return route
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            return null
        }
    }

    private fun coordinate(point: LatLng) = String.format(Locale.US, "%.6f,%.6f",
        point.latitude, point.longitude)
}
