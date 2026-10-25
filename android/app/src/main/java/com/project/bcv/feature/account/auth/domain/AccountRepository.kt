package com.project.bcv.feature.account.auth.domain

data class AccountUser(val id: Long, val name: String, val email: String)
data class AccountSession(
    val token: String,
    val user: AccountUser,
    val needsInterestSelection: Boolean,
    val needsActivityLevel: Boolean,
    val needsVirtualLocation: Boolean
)

interface AccountRepository {
    fun register(email: String, password: String, confirmPassword: String): AccountSession
    fun login(email: String, password: String): AccountSession
    fun logout()

    /** Sends a 6-digit code; returns seconds before another code may be requested. */
    fun requestPasswordReset(email: String): Int
    /** Returns a one-time token for [completePasswordReset]. */
    fun verifyPasswordResetCode(email: String, code: String): String
    fun completePasswordReset(resetToken: String, password: String, confirmPassword: String)
}
