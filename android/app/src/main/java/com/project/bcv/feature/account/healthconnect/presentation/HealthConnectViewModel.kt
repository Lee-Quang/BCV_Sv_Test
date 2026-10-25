package com.project.bcv.feature.account.healthconnect.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.feature.account.healthconnect.domain.HealthConnectAvailability
import com.project.bcv.feature.account.healthconnect.domain.HealthConnectRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HealthConnectStage {
    READY, CHECKING, WAITING_FOR_SYSTEM, GRANTED, DENIED,
    INSTALL_OR_UPDATE, UNSUPPORTED, REQUEST_FAILED, REVOKED, SETTINGS_REQUIRED
}

class HealthConnectViewModel(private val repository: HealthConnectRepository) : ViewModel() {
    private val mutableStage = MutableLiveData(HealthConnectStage.CHECKING)
    val stage: LiveData<HealthConnectStage> = mutableStage

    fun refresh() {
        mutableStage.value = HealthConnectStage.CHECKING
        viewModelScope.launch {
            mutableStage.value = try {
                when (withContext(Dispatchers.IO) { repository.availability() }) {
                    HealthConnectAvailability.UNSUPPORTED -> HealthConnectStage.UNSUPPORTED
                    HealthConnectAvailability.INSTALL_OR_UPDATE -> HealthConnectStage.INSTALL_OR_UPDATE
                    HealthConnectAvailability.AVAILABLE -> {
                        if (repository.hasReadStepsPermission()) {
                            repository.markGranted()
                            HealthConnectStage.GRANTED
                        } else when {
                            repository.wasGrantedBefore() -> HealthConnectStage.REVOKED
                            repository.deniedRequestCount() >= 2 -> HealthConnectStage.SETTINGS_REQUIRED
                            else -> HealthConnectStage.READY
                        }
                    }
                }
            } catch (_: Exception) {
                HealthConnectStage.REQUEST_FAILED
            }
        }
    }

    fun permissionRequestStarted() {
        mutableStage.value = HealthConnectStage.WAITING_FOR_SYSTEM
    }

    fun permissionRequestFailed() {
        mutableStage.value = HealthConnectStage.REQUEST_FAILED
    }

    fun permissionRequestReturned() {
        mutableStage.value = HealthConnectStage.CHECKING
        viewModelScope.launch {
            mutableStage.value = try {
                if (repository.hasReadStepsPermission()) {
                    repository.markGranted()
                    HealthConnectStage.GRANTED
                } else if (repository.recordDeniedRequest() >= 2) {
                    HealthConnectStage.SETTINGS_REQUIRED
                } else {
                    HealthConnectStage.DENIED
                }
            } catch (_: Exception) {
                HealthConnectStage.REQUEST_FAILED
            }
        }
    }
}

class HealthConnectViewModelFactory(private val repository: HealthConnectRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = HealthConnectViewModel(repository) as T
}
