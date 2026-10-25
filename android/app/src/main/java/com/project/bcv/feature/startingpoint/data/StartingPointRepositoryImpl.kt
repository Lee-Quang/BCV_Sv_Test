package com.project.bcv.feature.startingpoint.data

import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.data.local.SessionPreferences
import com.project.bcv.feature.startingpoint.domain.Coordinates
import com.project.bcv.feature.startingpoint.domain.StartingPoint
import com.project.bcv.feature.startingpoint.domain.StartingPointOptions
import com.project.bcv.feature.startingpoint.domain.StartingPointRepository
import com.project.bcv.feature.startingpoint.domain.StartingPointSource
import org.json.JSONObject

class StartingPointRepositoryImpl(
    private val remote: JsonApiClient,
    private val session: SessionPreferences
) : StartingPointRepository {
    override fun load(): StartingPointOptions {
        val response = remote.get("api/starting-point", token())
        return StartingPointOptions(
            response.optJSONObject("saved")?.toStartingPoint(),
            response.optJSONObject("suggested")?.toStartingPoint()
        )
    }

    override fun resolveCurrent(coordinates: Coordinates): StartingPoint = remote.post(
        "api/starting-point/resolve-current", coordinates.toJson(), token()
    ).toStartingPoint()

    override fun save(source: StartingPointSource, coordinates: Coordinates?): StartingPoint {
        val body = JSONObject().put("source", source.name)
        if (source == StartingPointSource.CURRENT) {
            val current = coordinates ?: throw IllegalArgumentException("Missing current coordinates")
            body.put("latitude", current.latitude).put("longitude", current.longitude)
        }
        return remote.put("api/starting-point", body, token()).toStartingPoint()
    }

    private fun JSONObject.toStartingPoint() = StartingPoint(
        getString("regionName"), optString("placeName").takeIf(String::isNotBlank),
        Coordinates(getDouble("latitude"), getDouble("longitude"))
    )

    private fun Coordinates.toJson() = JSONObject()
        .put("latitude", latitude).put("longitude", longitude)

    private fun token(): String = session.token
        ?: throw ApiFailure("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
}
