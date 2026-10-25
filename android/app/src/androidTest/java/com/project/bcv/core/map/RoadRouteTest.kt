package com.project.bcv.core.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.geometry.LatLng

@RunWith(AndroidJUnit4::class)
class RoadRouteTest {
    @Test fun decodedPolylineSplitsAtOneRoadPoint() {
        val route = RoadRoute.fromEncodedPolyline("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertNotNull(route)
        route!!
        assertEquals(38.5, route.pointAt(0.0).latitude, 0.00001)
        assertEquals(-120.2, route.pointAt(0.0).longitude, 0.00001)
        assertEquals(43.252, route.pointAt(1.0).latitude, 0.00001)
        assertEquals(-126.453, route.pointAt(1.0).longitude, 0.00001)

        val features = JSONObject(route.progressGeoJson(0.5)).getJSONArray("features")
        assertEquals(2, features.length())
        val done = features.getJSONObject(0).getJSONObject("geometry").getJSONArray("coordinates")
        val remaining = features.getJSONObject(1).getJSONObject("geometry").getJSONArray("coordinates")
        assertEquals(done.getJSONArray(done.length() - 1).toString(), remaining.getJSONArray(0).toString())
        assertEquals(1, JSONObject(route.progressGeoJson(0.0)).getJSONArray("features").length())
        assertEquals(1, JSONObject(route.progressGeoJson(1.0)).getJSONArray("features").length())

        val anchored = route.withEndpoints(LatLng(38.499, -120.199), LatLng(43.253, -126.454))
        assertEquals(38.499, anchored.pointAt(0.0).latitude, 0.00001)
        assertEquals(-126.454, anchored.pointAt(1.0).longitude, 0.00001)
    }

    @Test fun invalidPolylineIsRejected() {
        assertNull(RoadRoute.fromEncodedPolyline("bad!"))
    }
}
