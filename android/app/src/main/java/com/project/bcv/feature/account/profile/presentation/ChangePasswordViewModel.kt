package com.project.bcv.feature.account.profile.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project.bcv.core.presentation.Remote
import com.project.bcv.core.presentation.launchRemote
import com.project.bcv.feature.account.profile.domain.ProfileRepository

/** Errors are shown under their field; [requestError] is for anything not tied to one. */
data class ChangePasswordState(
    val saving: Boolean = false,
    val currentError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val requestError: String? = null,
    val done: Boolean = false,
    val sessionExpired: Boolean = false
)

class ChangePasswordViewModel(private val repository: ProfileRepository) : ViewModel() {
    private val mutableState = MutableLiveData(ChangePasswordState())
    val state: LiveData<ChangePasswordState> = mutableState

    fun clearErrors() {
        val current = mutableState.value ?: return
        if (current.currentError != null || current.passwordError != null || current.confirmError != null ||
            current.requestError != null) mutableState.value = ChangePasswordState(saving = current.saving)
    }

    fun submit(current: String, password: String, confirm: String) {
        if (mutableState.value?.saving == true) return
        // Same checks as the server, so the common mistakes show before a round trip.
        val checked = ChangePasswordState(
            currentError = if (current.isEmpty()) "Vui lòng nhập mật khẩu hiện tại." else null,
            passwordError = when {
                password.length !in 8..128 || password.none(Char::isLetter) || password.none(Char::isDigit) ->
                    "Mật khẩu cần 8–128 ký tự, có chữ và số."
                password == current -> "Mật khẩu mới cần khác mật khẩu hiện tại."
                else -> null
            },
            confirmError = if (confirm != password) "Mật khẩu xác nhận không khớp." else null
        )
        if (checked.currentError != null || checked.passwordError != null || checked.confirmError != null) {
            mutableState.value = checked
            return
        }
        launchRemote<Unit>({ result ->
            mutableState.value = when (result) {
                Remote.Loading -> ChangePasswordState(saving = true)
                is Remote.Ready -> ChangePasswordState(done = true)
                Remote.SessionExpired -> ChangePasswordState(sessionExpired = true)
                is Remote.Failed -> when (result.field) {
                    "currentPassword" -> ChangePasswordState(currentError = result.message)
                    "password" -> ChangePasswordState(passwordError = result.message)
                    "confirmPassword" -> ChangePasswordState(confirmError = result.message)
                    else -> ChangePasswordState(requestError = result.message)
                }
            }
        }) { repository.changePassword(current, password, confirm) }
    }
}
