package com.project.bcv.feature.journey.data

import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.core.network.ServerDates
import com.project.bcv.feature.account.auth.data.local.SessionPreferences
import com.project.bcv.feature.journey.domain.JourneyHistoryRepository
import com.project.bcv.feature.journey.domain.JourneyRecord

class JourneyHistoryRepositoryImpl(
    private val remote: JsonApiClient,
    private val session: SessionPreferences
) : JourneyHistoryRepository {
    override fun history(): List<JourneyRecord> {
        val token = session.token
            ?: throw ApiFailure("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
        val journeys = remote.get("api/journeys/history", token).getJSONArray("journeys")
        return (0 until journeys.length()).map { index ->
            journeys.getJSONObject(index).let {
                JourneyRecord(it.getLong("journeyId"), it.getString("status"), it.getString("startName"),
                    it.getLong("destinationPlaceId"), it.getString("destinationName"),
                    ServerDates.parse(it.optString("startedAt")),
                    if (it.isNull("completedAt")) null else ServerDates.parse(it.optString("completedAt")))
            }
        }
    }
}
