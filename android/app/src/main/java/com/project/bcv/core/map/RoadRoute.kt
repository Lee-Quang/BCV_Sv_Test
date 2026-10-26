package com.project.bcv.core.map

import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Geometry returned by Goong Directions. Progress is measured along the road polyline. */
class RoadRoute private constructor(private val points: List<LatLng>) {
    private val lengths = DoubleArray(points.size).also { cumulative ->
        for (index in 1 until points.size) {
            cumulative[index] = cumulative[index - 1] + distanceMeters(points[index - 1], points[index])
        }
    }

    fun bounds(): LatLngBounds = LatLngBounds.Builder().apply {
        points.forEach(::include)
    }.build()

    /** Directions snaps to mapped roads; connect those endpoints to the app's exact pins. */
    fun withEndpoints(start: LatLng, destination: LatLng): RoadRoute {
        val anchored = ArrayList<LatLng>(points.size + 2)
        if (distanceMeters(start, points.first()) > 2.0) anchored.add(start)
        anchored.addAll(points)
        if (distanceMeters(points.last(), destination) > 2.0) anchored.add(destination)
        return RoadRoute(anchored)
    }

    fun pointAt(fraction: Double): LatLng {
        val progress = fraction.coerceIn(0.0, 1.0)
        if (progress <= 0.0) return points.first()
        if (progress >= 1.0 || lengths.last() == 0.0) return points.last()
        val target = lengths.last() * progress
        val after = (1 until points.size).first { lengths[it] >= target }
        val before = after - 1
        val segment = lengths[after] - lengths[before]
        val along = if (segment == 0.0) 0.0 else (target - lengths[before]) / segment
        return interpolate(points[before], points[after], along)
    }

    /**
     * Whether the walk ahead of [fraction] heads east (right on the north-up map). It looks
     * [LOOK_AHEAD_SHARE] of the route ahead, about what the map shows around the walker, so small
     * bends in the road do not turn the walker against the visible way to the destination.
     * Near the end it looks back over the last stretch instead. Null on a pure north/south run.
     */
    fun headsEastAt(fraction: Double): Boolean? {
        val progress = fraction.coerceIn(0.0, 1.0)
        val total = lengths.last()
        if (total <= 0.0) return null
        val window = maxOf(LOOK_AHEAD_SHARE, min(1.0, LOOK_AHEAD_METERS / total))
        val (from, to) = if (progress + window <= 1.0) pointAt(progress) to pointAt(progress + window)
            else pointAt((1.0 - window).coerceAtLeast(0.0)) to pointAt(1.0)
        val east = to.longitude - from.longitude
        return if (kotlin.math.abs(east) < 1e-7) null else east > 0
    }

    fun progressGeoJson(fraction: Double): String {
        val progress = fraction.coerceIn(0.0, 1.0)
        val features = JSONArray()
        when {
            progress <= 0.0 -> features.put(line("remaining", points))
            progress >= 1.0 -> features.put(line("done", points))
            else -> {
                val target = lengths.last() * progress
                val after = (1 until points.size).first { lengths[it] >= target }
                val marker = pointAt(progress)
                features.put(line("done", points.take(after) + marker))
                features.put(line("remaining", listOf(marker) + points.drop(after)))
            }
        }
        return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
    }

    private fun line(part: String, coordinates: List<LatLng>) = JSONObject()
        .put("type", "Feature")
        .put("properties", JSONObject().put("part", part))
        .put("geometry", JSONObject().put("type", "LineString").put("coordinates", JSONArray().apply {
            coordinates.forEach { put(JSONArray().put(it.longitude).put(it.latitude)) }
        }))

    companion object {
        private const val LOOK_AHEAD_METERS = 40.0
        private const val LOOK_AHEAD_SHARE = 0.05

        fun fromEncodedPolyline(encoded: String): RoadRoute? {
            var index = 0
            var latitude = 0
            var longitude = 0
            val decoded = ArrayList<LatLng>()
            fun nextDelta(): Int? {
                var result = 0
                var shift = 0
                while (index < encoded.length && shift <= 30) {
                    val value = encoded[index++].code - 63
                    if (value !in 0..63) return null
                    result = result or ((value and 0x1f) shl shift)
                    if (value < 0x20) return if (result and 1 != 0) (result shr 1).inv() else result shr 1
                    shift += 5
                }
                return null
            }
            while (index < encoded.length && decoded.size < 20_000) {
                latitude += nextDelta() ?: return null
                longitude += nextDelta() ?: return null
                val lat = latitude / 100_000.0
                val lon = longitude / 100_000.0
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
                decoded.add(LatLng(lat, lon))
            }
            return decoded.takeIf { it.size >= 2 && index == encoded.length }?.let(::RoadRoute)
        }

        private fun interpolate(from: LatLng, to: LatLng, fraction: Double) = LatLng(
            from.latitude + (to.latitude - from.latitude) * fraction,
            from.longitude + (to.longitude - from.longitude) * fraction
        )

        private fun distanceMeters(from: LatLng, to: LatLng): Double {
            val latitudeDelta = Math.toRadians(to.latitude - from.latitude)
            val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
            val square = sin(latitudeDelta / 2).pow(2) +
                cos(Math.toRadians(from.latitude)) * cos(Math.toRadians(to.latitude)) *
                sin(longitudeDelta / 2).pow(2)
            return 12_742_000.0 * asin(min(1.0, sqrt(square)))
        }
    }
}
