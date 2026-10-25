package com.project.bcv.feature.account.profile.data

import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.auth.data.local.SessionPreferences
import com.project.bcv.feature.account.auth.domain.AccountRepository
import com.project.bcv.feature.account.profile.domain.Profile
import com.project.bcv.feature.account.profile.domain.ProfileRepository
import org.json.JSONObject

class ProfileRepositoryImpl(
    private val remote: JsonApiClient,
    private val session: SessionPreferences,
    private val account: AccountRepository
) : ProfileRepository {
    private fun token() = session.token
        ?: throw ApiFailure("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")

    override fun profile(): Profile = remote.get("api/account/profile", token()).toProfile()

    override fun updateProfile(fullName: String, avatar: String?): Profile {
        val body = JSONObject().put("fullName", fullName)
        if (avatar != null) body.put("avatar", avatar)
        return remote.put("api/account/profile", body, token()).toProfile()
    }

    override fun changePassword(current: String, new: String, confirm: String) {
        remote.post("api/account/change-password", JSONObject()
            .put("currentPassword", current).put("newPassword", new).put("confirmPassword", confirm), token())
    }

    override fun deleteAccount() {
        remote.post("api/account/delete", JSONObject(), token())
        session.token = null
    }

    override fun logout() = account.logout()

    private fun JSONObject.toProfile() = Profile(optString("fullName"), optString("email"),
        if (isNull("avatar")) null else optString("avatar").takeIf(String::isNotBlank))
}
