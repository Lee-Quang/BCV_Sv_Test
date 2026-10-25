package com.project.bcv.feature.account.profile.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.project.bcv.core.presentation.Event
import com.project.bcv.core.presentation.Remote
import com.project.bcv.core.presentation.launchRemote
import com.project.bcv.feature.account.profile.domain.Profile
import com.project.bcv.feature.account.profile.domain.ProfileRepository

class EditProfileViewModel(private val repository: ProfileRepository) : ViewModel() {
    private val mutableState = MutableLiveData<Remote<Profile>>()
    /** The saved profile; a successful save replaces it. */
    val state: LiveData<Remote<Profile>> = mutableState
    private val mutablePendingAvatar = MutableLiveData<String?>(null)
    /** A newly picked avatar (JPEG data URI) not saved yet. */
    val pendingAvatar: LiveData<String?> = mutablePendingAvatar
    private val mutableSaving = MutableLiveData(false)
    val saving: LiveData<Boolean> = mutableSaving
    private val mutableSaved = MutableLiveData<Event<Result<Profile>>>()
    val saved: LiveData<Event<Result<Profile>>> = mutableSaved

    val profile: Profile? get() = (mutableState.value as? Remote.Ready)?.value

    init { load() }

    fun load() = launchRemote({ mutableState.value = it }, repository::profile)

    fun pickAvatar(dataUri: String) {
        mutablePendingAvatar.value = dataUri
    }

    fun save(name: String) {
        if (mutableSaving.value == true) return
        val avatar = mutablePendingAvatar.value
        launchRemote<Profile>({ result ->
            mutableSaving.value = result == Remote.Loading
            when (result) {
                is Remote.Ready -> {
                    mutablePendingAvatar.value = null
                    mutableState.value = result
                    mutableSaved.value = Event(Result.success(result.value))
                }
                is Remote.Failed -> mutableSaved.value = Event(Result.failure(IllegalStateException(result.message)))
                Remote.SessionExpired -> mutableState.value = result
                Remote.Loading -> Unit
            }
        }) { repository.updateProfile(name, avatar) }
    }
}
