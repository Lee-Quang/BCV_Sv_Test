package com.project.bcv.feature.account.auth.presentation

import android.util.Patterns

data class FieldErrors(
    val email: String? = null,
    val password: String? = null,
    val confirmPassword: String? = null
) {
    val hasErrors: Boolean get() = email != null || password != null || confirmPassword != null
}

object AccountFormValidator {
    fun validate(mode: AccountMode, email: String, password: String, confirmPassword: String): FieldErrors {
        val emailError = when {
            email.isBlank() -> "Vui lòng nhập email."
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Email không đúng định dạng."
            else -> null
        }
        val passwordError = when {
            password.isEmpty() -> "Vui lòng nhập mật khẩu."
            mode == AccountMode.REGISTER &&
                (password.length !in 8..128 || !password.any(Char::isLetter) || !password.any(Char::isDigit)) ->
                "Mật khẩu cần 8–128 ký tự, có chữ và số."
            else -> null
        }
        val confirmError = if (mode == AccountMode.REGISTER) when {
            confirmPassword.isEmpty() -> "Vui lòng xác nhận mật khẩu."
            confirmPassword != password -> "Mật khẩu xác nhận không khớp."
            else -> null
        } else null
        return FieldErrors(emailError, passwordError, confirmError)
    }
}
