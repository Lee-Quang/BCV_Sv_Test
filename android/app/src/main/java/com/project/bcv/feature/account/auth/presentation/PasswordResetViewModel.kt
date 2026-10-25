package com.project.bcv.feature.account.auth.presentation

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.account.auth.domain.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class PasswordResetStep { EMAIL, CODE, PASSWORD, DONE }

data class PasswordResetErrors(
    val email: String? = null,
    val code: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null
)

data class PasswordResetUiState(
    val step: PasswordResetStep = PasswordResetStep.EMAIL,
    val email: String = "",
    val loading: Boolean = false,
    val resendSeconds: Int = 0,
    val errors: PasswordResetErrors = PasswordResetErrors(),
    val requestError: String? = null,
    val notice: String? = null
)

class PasswordResetViewModel(private val repository: AccountRepository) : ViewModel() {
    private val mutableState = MutableLiveData(PasswordResetUiState())
    val state: LiveData<PasswordResetUiState> = mutableState
    private var resetToken: String? = null
    private var countdown: Job? = null

    fun prefill(email: String) {
        val current = mutableState.value ?: return
        if (current.email.isEmpty()) mutableState.value = current.copy(email = email)
    }

    fun clearErrors() {
        val current = mutableState.value ?: return
        if (current.errors != PasswordResetErrors() || current.requestError != null) {
            mutableState.value = current.copy(errors = PasswordResetErrors(), requestError = null)
        }
    }

    fun sendCode(rawEmail: String) {
        val current = mutableState.value ?: return
        if (current.loading) return
        val email = rawEmail.trim().lowercase()
        val error = when {
            email.isBlank() -> "Vui lòng nhập email."
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Email không đúng định dạng."
            else -> null
        }
        if (error != null) {
            mutableState.value = current.copy(errors = PasswordResetErrors(email = error), requestError = null)
            return
        }
        run(current.copy(email = email)) { state ->
            val wait = repository.requestPasswordReset(email)
            startCountdown(wait)
            state.copy(step = PasswordResetStep.CODE, resendSeconds = wait,
                notice = "Nếu email đã đăng ký, mã xác minh đã được gửi tới $email.")
        }
    }

    fun resendCode() {
        val current = mutableState.value ?: return
        if (current.step != PasswordResetStep.CODE || current.resendSeconds > 0) return
        run(current) { state ->
            val wait = repository.requestPasswordReset(state.email)
            startCountdown(wait)
            state.copy(resendSeconds = wait, notice = "Đã gửi lại mã mới tới ${state.email}.")
        }
    }

    fun verifyCode(rawCode: String) {
        val current = mutableState.value ?: return
        if (current.loading) return
        val code = rawCode.trim()
        if (code.length != 6 || !code.all(Char::isDigit)) {
            mutableState.value = current.copy(
                errors = PasswordResetErrors(code = "Nhập đủ 6 chữ số của mã xác minh."), requestError = null)
            return
        }
        run(current) { state ->
            resetToken = repository.verifyPasswordResetCode(state.email, code)
            state.copy(step = PasswordResetStep.PASSWORD, notice = null)
        }
    }

    fun resetPassword(password: String, confirmPassword: String) {
        val current = mutableState.value ?: return
        if (current.loading) return
        val errors = PasswordResetErrors(
            password = when {
                password.isEmpty() -> "Vui lòng nhập mật khẩu mới."
                password.length !in 8..128 || !password.any(Char::isLetter) || !password.any(Char::isDigit) ->
                    "Mật khẩu cần 8–128 ký tự, có chữ và số."
                else -> null
            },
            confirmPassword = when {
                confirmPassword.isEmpty() -> "Vui lòng xác nhận mật khẩu."
                confirmPassword != password -> "Mật khẩu xác nhận không khớp."
                else -> null
            }
        )
        if (errors != PasswordResetErrors()) {
            mutableState.value = current.copy(errors = errors, requestError = null)
            return
        }
        val token = resetToken ?: run {
            mutableState.value = current.copy(step = PasswordResetStep.EMAIL,
                requestError = "Phiên khôi phục đã hết hạn. Vui lòng yêu cầu mã mới.")
            return
        }
        run(current) { state ->
            repository.completePasswordReset(token, password, confirmPassword)
            resetToken = null
            state.copy(step = PasswordResetStep.DONE)
        }
    }

    /** Steps back one screen; returns false when already on the first step. */
    fun back(): Boolean {
        val current = mutableState.value ?: return false
        if (current.loading) return true
        val previous = when (current.step) {
            PasswordResetStep.CODE -> PasswordResetStep.EMAIL
            // The verified code is single-use, so going back from the password step asks for a new one.
            PasswordResetStep.PASSWORD -> PasswordResetStep.CODE
            else -> return false
        }
        mutableState.value = current.copy(step = previous, errors = PasswordResetErrors(),
            requestError = null, notice = null)
        return true
    }

    private fun run(start: PasswordResetUiState, action: suspend (PasswordResetUiState) -> PasswordResetUiState) {
        mutableState.value = start.copy(loading = true, errors = PasswordResetErrors(), requestError = null)
        viewModelScope.launch {
            val loading = mutableState.value ?: start
            mutableState.value = try {
                withContext(Dispatchers.IO) { action(loading) }.copy(loading = false)
            } catch (error: ApiFailure) {
                failure(loading, error)
            } catch (_: IOException) {
                loading.copy(loading = false, requestError = "Chưa thể kết nối. Kiểm tra mạng và thử lại.")
            } catch (_: Exception) {
                loading.copy(loading = false, requestError = "Đã xảy ra lỗi. Vui lòng thử lại.")
            }
        }
    }

    private fun failure(state: PasswordResetUiState, error: ApiFailure): PasswordResetUiState {
        val failed = state.copy(loading = false)
        return when (error.code) {
            "RESET_EXPIRED" -> failed.copy(step = PasswordResetStep.EMAIL, requestError = error.message)
            "TOO_MANY_ATTEMPTS", "CODE_EXPIRED" -> {
                // The server dropped this code; let the user ask for a new one right away.
                countdown?.cancel()
                failed.copy(resendSeconds = 0, errors = PasswordResetErrors(code = error.message))
            }
            else -> when (error.field) {
                "email" -> failed.copy(errors = PasswordResetErrors(email = error.message))
                "code" -> failed.copy(errors = PasswordResetErrors(code = error.message))
                "password" -> failed.copy(errors = PasswordResetErrors(password = error.message))
                "confirmPassword" -> failed.copy(errors = PasswordResetErrors(confirmPassword = error.message))
                else -> failed.copy(requestError = error.message)
            }
        }
    }

    private fun startCountdown(seconds: Int) {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            var left = seconds
            while (left > 0) {
                delay(1_000L)
                left--
                mutableState.value = mutableState.value?.copy(resendSeconds = left)
            }
        }
    }
}

class PasswordResetViewModelFactory(private val repository: AccountRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PasswordResetViewModel(repository) as T
}
