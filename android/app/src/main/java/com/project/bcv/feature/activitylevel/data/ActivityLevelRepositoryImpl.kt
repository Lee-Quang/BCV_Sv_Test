package com.project.bcv.feature.activitylevel.data

import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.data.local.SessionPreferences
import com.project.bcv.feature.activitylevel.domain.ActivityLevel
import com.project.bcv.feature.activitylevel.domain.ActivityLevelRepository
import org.json.JSONObject

class ActivityLevelRepositoryImpl(
    private val remote: JsonApiClient,
    private val session: SessionPreferences
) : ActivityLevelRepository {
    override fun load(): ActivityLevel? {
        val code = remote.get("api/activity-level", token()).optString("activityLevel")
        return ActivityLevel.entries.firstOrNull { it.name == code }
    }

    override fun save(level: ActivityLevel) {
        remote.put("api/activity-level", JSONObject().put("activityLevel", level.name), token())
    }

    private fun token(): String = session.token
        ?: throw ApiFailure("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
}
