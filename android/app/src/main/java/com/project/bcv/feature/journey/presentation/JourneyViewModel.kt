package com.project.bcv.feature.journey.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.presentation.Event
import com.project.bcv.feature.discovery.domain.ExploreRepository
import com.project.bcv.feature.discovery.domain.ExploreSnapshot
import com.project.bcv.feature.discovery.domain.StepSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.IOException

sealed interface JourneyUiState {
    data object Loading : JourneyUiState
    /** [syncedAtMillis] is when the server last confirmed this progress to the app. */
    data class Loaded(
        val snapshot: ExploreSnapshot,
        val syncedAtMillis: Long,
        val syncing: Boolean = false,
        val syncError: String? = null,
        val permissionRequired: Boolean = false
    ) : JourneyUiState
    data class Failed(val message: String) : JourneyUiState
    data object SessionExpired : JourneyUiState
}

/** Syncs the same cumulative step total as Explore; displayed progress is confirmed by the server. */
class JourneyViewModel(
    private val repository: ExploreRepository,
    private val steps: StepSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {
    private val mutableState = MutableLiveData<JourneyUiState>(JourneyUiState.Loading)
    val state: LiveData<JourneyUiState> = mutableState
    private val mutablePermissionRequest = MutableLiveData<Event<Unit>>()
    val permissionRequest: LiveData<Event<Unit>> = mutablePermissionRequest
    val usesDeviceSensor: Boolean get() = steps.usesDeviceSensor
    private var requestJob: Job? = null
    private var foreground = false

    init {
        load()
        viewModelScope.launch {
            while (isActive) {
                delay(3_000)
                val current = mutableState.value as? JourneyUiState.Loaded ?: continue
                val journey = current.snapshot.journey ?: continue
                if (!foreground || !steps.usesDeviceSensor || journey.status != "IN_PROGRESS" ||
                    current.syncing || current.syncError != null || current.permissionRequired ||
                    requestJob?.isActive == true) continue
                try {
                    if (!steps.hasPermission()) syncSteps(automatic = true)
                    else if (steps.totalStepsSince(journey.startedAtEpochSeconds) > journey.syncedSteps) {
                        syncSteps(automatic = true)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    mutableState.value = current.copy(syncError = error.message ?: "Chưa đọc được bước chân trên điện thoại.")
                }
            }
        }
    }

    fun setForeground(active: Boolean) { foreground = active }

    fun load() {
        val previous = mutableState.value as? JourneyUiState.Loaded
        if (requestJob?.isActive == true || previous?.syncing == true) return
        if (previous == null) mutableState.value = JourneyUiState.Loading
        requestJob = viewModelScope.launch {
            mutableState.value = try {
                val snapshot = withContext(ioDispatcher) { repository.load() }
                steps.track(snapshot.journey)
                JourneyUiState.Loaded(snapshot, System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ApiFailure) {
                if (error.code == "UNAUTHORIZED") sessionExpired() else failed(previous, error.message)
            } catch (error: IOException) {
                Log.w(TAG, "Journey load could not reach the API", error)
                failed(previous, "Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
            } catch (error: Exception) {
                Log.e(TAG, "Journey load failed", error)
                failed(previous, "Chưa tải được hành trình. Vui lòng thử lại.")
            }
        }
    }

    fun syncSteps(automatic: Boolean = false) {
        val current = mutableState.value as? JourneyUiState.Loaded ?: return
        val journey = current.snapshot.journey ?: return
        if (journey.status != "IN_PROGRESS" || current.syncing || requestJob?.isActive == true) return
        mutableState.value = current.copy(syncing = true, syncError = null, permissionRequired = false)
        requestJob = viewModelScope.launch {
            try {
                if (!steps.hasPermission()) {
                    steps.stopTracking()
                    mutableState.value = current.copy(syncing = false, permissionRequired = true,
                        syncError = if (steps.usesDeviceSensor) "Cần quyền hoạt động thể chất để đếm bước chân."
                            else "Cần quyền đọc bước chân từ Health Connect.")
                    if (!automatic) viewModelScope.launch {
                        yield()
                        mutablePermissionRequest.value = Event(Unit)
                    }
                    return@launch
                }
                steps.track(journey)
                val total = steps.totalStepsSince(journey.startedAtEpochSeconds)
                val snapshot = withContext(ioDispatcher) { repository.syncSteps(journey.id, total) }
                steps.track(snapshot.journey)
                mutableState.value = JourneyUiState.Loaded(snapshot, System.currentTimeMillis())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ApiFailure) {
                mutableState.value = if (error.code == "UNAUTHORIZED") sessionExpired()
                    else current.copy(syncing = false, syncError = error.message)
            } catch (error: IOException) {
                Log.w(TAG, "Journey step sync could not reach the API", error)
                mutableState.value = current.copy(syncing = false,
                    syncError = "Chưa kết nối được máy chủ. Số bước vẫn được giữ trên điện thoại.")
            } catch (error: Exception) {
                Log.e(TAG, "Journey step sync failed", error)
                mutableState.value = current.copy(syncing = false, syncError = "Chưa đồng bộ được bước chân. Vui lòng thử lại.")
            }
        }
    }

    private fun failed(previous: JourneyUiState.Loaded?, message: String): JourneyUiState =
        previous?.copy(syncing = false, syncError = message) ?: JourneyUiState.Failed(message)

    private fun sessionExpired(): JourneyUiState {
        steps.stopTracking()
        return JourneyUiState.SessionExpired
    }

    private companion object { const val TAG = "JourneyViewModel" }
}

class JourneyViewModelFactory(private val repository: ExploreRepository, private val steps: StepSource) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = JourneyViewModel(repository, steps) as T
}
