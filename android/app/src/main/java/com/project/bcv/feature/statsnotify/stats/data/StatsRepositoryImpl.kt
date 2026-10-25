package com.project.bcv.feature.statsnotify.stats.data

import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.auth.data.local.SessionPreferences
import com.project.bcv.feature.statsnotify.stats.domain.ProfileStats
import com.project.bcv.feature.statsnotify.stats.domain.StatsRepository
import com.project.bcv.feature.statsnotify.stats.domain.Tally
import org.json.JSONObject

class StatsRepositoryImpl(private val remote: JsonApiClient, private val session: SessionPreferences) : StatsRepository {
    override fun stats(): ProfileStats {
        val token = session.token
            ?: throw ApiFailure("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
        return remote.get("api/profile/stats", token).let {
            ProfileStats(it.getLong("totalSteps"), it.getInt("discoveredPlaces"), it.getInt("ownedCards"),
                it.getJSONObject("daily").toTally(), it.getJSONObject("weekly").toTally())
        }
    }

    private fun JSONObject.toTally() = Tally(getInt("completed"), getInt("total"))
}
