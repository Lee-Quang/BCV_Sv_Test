package com.project.bcv.feature.account.profile.domain

/** [avatar] is the image URL (Cloudflare R2) or, for older avatars, a JPEG data URI; null when none is set. */
data class Profile(val name: String, val email: String, val avatar: String?)

interface ProfileRepository {
    fun profile(): Profile

    /** Saves the name and, when [avatar] (a JPEG data URI) is not null, replaces the avatar. */
    fun updateProfile(fullName: String, avatar: String?): Profile

    /** Errors carry the field they belong to (currentPassword, password or confirmPassword). */
    fun changePassword(current: String, new: String, confirm: String)

    /** Closes the account on the server and forgets the session here. */
    fun deleteAccount()

    fun logout()
}
