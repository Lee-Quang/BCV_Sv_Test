package com.project.bcv.feature.profile.data

import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.data.local.SessionPreferences
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** [avatar] is a JPEG data URI, or null when the user has not set one. */
data class Profile(val name: String, val email: String, val avatar: String?)

data class Tally(val completed: Int, val total: Int)

data class ProfileStats(val totalSteps: Long, val discoveredPlaces: Int, val ownedCards: Int, val daily: Tally, val weekly: Tally)

data class JourneyRecord(
    val id: Long,
    val status: String,
    val start: String,
    val destinationId: Long,
    val destination: String,
    val startedAt: Date?,
    val completedAt: Date?
)

data class AppNotification(val id: Long, val type: String, val title: String, val content: String, val read: Boolean, val createdAt: Date?)

data class NotificationPage(val unread: Int, val items: List<AppNotification>)

/** Account details, personal statistics, journey history and notifications. */
class ProfileRepository(private val remote: JsonApiClient, private val session: SessionPreferences) {
    private fun token() = session.token
        ?: throw ApiFailure("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")

    fun profile(): Profile = remote.get("api/account/profile", token()).toProfile()

    /** Saves the name and, when [avatar] is not null, replaces the avatar. */
    fun updateProfile(fullName: String, avatar: String?): Profile {
        val body = JSONObject().put("fullName", fullName)
        if (avatar != null) body.put("avatar", avatar)
        return remote.put("api/account/profile", body, token()).toProfile()
    }

    private fun JSONObject.toProfile() = Profile(optString("fullName"), optString("email"),
        if (isNull("avatar")) null else optString("avatar").takeIf(String::isNotBlank))

    fun stats(): ProfileStats = remote.get("api/profile/stats", token()).let {
        ProfileStats(it.getLong("totalSteps"), it.getInt("discoveredPlaces"), it.getInt("ownedCards"),
            it.getJSONObject("daily").toTally(), it.getJSONObject("weekly").toTally())
    }

    fun history(): List<JourneyRecord> {
        val journeys = remote.get("api/journeys/history", token()).getJSONArray("journeys")
        return (0 until journeys.length()).map { index ->
            journeys.getJSONObject(index).let {
                JourneyRecord(it.getLong("journeyId"), it.getString("status"), it.getString("startName"),
                    it.getLong("destinationPlaceId"), it.getString("destinationName"), parse(it.optString("startedAt")),
                    if (it.isNull("completedAt")) null else parse(it.optString("completedAt")))
            }
        }
    }

    /** Errors carry the field they belong to (currentPassword, password or confirmPassword). */
    fun changePassword(current: String, new: String, confirm: String) {
        remote.post("api/account/change-password", JSONObject()
            .put("currentPassword", current).put("newPassword", new).put("confirmPassword", confirm), token())
    }

    fun notifications() = remote.get("api/notifications", token()).toPage()

    fun markRead(id: Long) = remote.post("api/notifications/$id/read", JSONObject(), token()).toPage()

    /** Closes the account on the server and forgets the session here. */
    fun deleteAccount() {
        remote.post("api/account/delete", JSONObject(), token())
        session.token = null
    }

    private fun JSONObject.toTally() = Tally(getInt("completed"), getInt("total"))

    private fun JSONObject.toPage(): NotificationPage {
        val items = getJSONArray("items")
        return NotificationPage(getInt("unreadCount"), (0 until items.length()).map { index ->
            items.getJSONObject(index).let {
                AppNotification(it.getLong("notificationId"), it.getString("type"), it.getString("title"),
                    it.getString("content"), it.getBoolean("read"), parse(it.optString("createdAt")))
            }
        })
    }

    /** Server timestamps are local (Vietnam) wall-clock times without an offset, possibly with fractions. */
    private fun parse(value: String): Date? = value.takeIf { it.isNotBlank() && it != "null" }?.let {
        runCatching { SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).parse(it.take(19)) }.getOrNull()
    }
}
