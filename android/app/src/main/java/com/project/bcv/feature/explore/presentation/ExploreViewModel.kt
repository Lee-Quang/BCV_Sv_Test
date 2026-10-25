package com.project.bcv.feature.explore.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.explore.domain.ExploreRepository
import com.project.bcv.feature.explore.domain.ExploreSnapshot
import com.project.bcv.feature.explore.domain.StepSource
import com.project.bcv.feature.startingpoint.domain.AddressLookup
import com.project.bcv.feature.startingpoint.domain.Coordinates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

enum class ExploreStage {
    LOADING, EMPTY, ACTIVE, COMPLETED, RESTORING, LOAD_FAILED, RESTORE_FAILED,
    SYNCING, SYNC_FAILED, NO_NEW_STEPS, PERMISSION_REQUIRED, SESSION_EXPIRED
}

data class ExploreUiState(
    val stage: ExploreStage = ExploreStage.LOADING,
    val snapshot: ExploreSnapshot? = null,
    val message: String? = null
)

class ExploreViewModel(
    private val repository: ExploreRepository,
    private val steps: StepSource,
    private val addressLookup: AddressLookup
) : ViewModel() {
    private val mutableState = MutableLiveData(ExploreUiState())
    val state: LiveData<ExploreUiState> = mutableState
    private val mutableAddress = MutableLiveData<String?>()
    /** Street address of the virtual location; kept apart because states are rebuilt often. */
    val address: LiveData<String?> = mutableAddress
    private var addressFor: Coordinates? = null

    private companion object { const val TAG = "ExploreViewModel" }

    fun load() {
        val current = mutableState.value ?: ExploreUiState()
        if (current.stage == ExploreStage.RESTORING || current.stage == ExploreStage.SYNCING) return
        mutableState.value = current.copy(
            stage = if (current.snapshot == null) ExploreStage.LOADING else ExploreStage.RESTORING,
            message = null
        )
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { repository.load() }
                mutableState.value = ExploreUiState(snapshot.stage(), snapshot)
                lookupAddress(snapshot)
                checkPermission()
            } catch (error: ApiFailure) {
                if (error.code == "UNAUTHORIZED") sessionExpired() else failLoad(error.message)
            } catch (error: IOException) {
                Log.w(TAG, "Explore load could not reach the API", error)
                failLoad("Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
            } catch (error: Exception) {
                Log.e(TAG, "Explore load failed", error)
                failLoad("Chưa tải được dữ liệu khám phá. Vui lòng thử lại.")
            }
        }
    }

    fun checkPermission() {
        val current = mutableState.value ?: return
        if (current.snapshot?.journey?.status != "IN_PROGRESS" ||
            current.stage in setOf(ExploreStage.SYNCING, ExploreStage.RESTORING)) return
        viewModelScope.launch {
            val granted = runCatching { steps.hasPermission() }.getOrDefault(false)
            val latest = mutableState.value ?: return@launch
            if (latest.snapshot?.journey?.id != current.snapshot.journey?.id) return@launch
            if (!granted) mutableState.value = latest.copy(stage = ExploreStage.PERMISSION_REQUIRED)
            else if (latest.stage == ExploreStage.PERMISSION_REQUIRED) {
                mutableState.value = latest.copy(stage = ExploreStage.ACTIVE)
            }
        }
    }

    fun syncSteps() {
        val current = mutableState.value ?: return
        val journey = current.snapshot?.journey ?: return
        if (journey.status != "IN_PROGRESS" || current.stage == ExploreStage.SYNCING) return
        mutableState.value = current.copy(stage = ExploreStage.SYNCING, message = null)
        viewModelScope.launch {
            try {
                if (!steps.hasPermission()) {
                    mutableState.value = current.copy(stage = ExploreStage.PERMISSION_REQUIRED)
                    return@launch
                }
                val total = steps.totalStepsSince(journey.startedAtEpochSeconds)
                val snapshot = withContext(Dispatchers.IO) { repository.syncSteps(journey.id, total) }
                val newJourney = snapshot.journey
                mutableState.value = ExploreUiState(
                    if (newJourney?.status == "COMPLETED") ExploreStage.COMPLETED
                    else if (newJourney?.recordedSteps == journey.recordedSteps) ExploreStage.NO_NEW_STEPS
                    else ExploreStage.ACTIVE,
                    snapshot
                )
                lookupAddress(snapshot)
            } catch (error: ApiFailure) {
                if (error.code == "UNAUTHORIZED") sessionExpired()
                else mutableState.value = current.copy(stage = ExploreStage.SYNC_FAILED, message = error.message)
            } catch (_: Exception) {
                mutableState.value = current.copy(stage = ExploreStage.SYNC_FAILED,
                    message = "Chưa đồng bộ được bước chân. Vui lòng thử lại.")
            }
        }
    }

    private fun lookupAddress(snapshot: ExploreSnapshot) {
        val coordinates = snapshot.location?.coordinates ?: return
        if (coordinates == addressFor) return
        addressFor = coordinates
        mutableAddress.value = null
        viewModelScope.launch {
            val address = withContext(Dispatchers.IO) { addressLookup.describe(coordinates) }
            if (addressFor != coordinates) return@launch
            mutableAddress.value = address
            if (address == null) addressFor = null // retry on the next load

        }
    }

    private fun failLoad(message: String) {
        val current = mutableState.value ?: return
        mutableState.value = current.copy(
            stage = if (current.snapshot == null) ExploreStage.LOAD_FAILED else ExploreStage.RESTORE_FAILED,
            message = message
        )
    }

    private fun sessionExpired() {
        mutableState.value = ExploreUiState(ExploreStage.SESSION_EXPIRED,
            message = "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
    }

    private fun ExploreSnapshot.stage() = when (journey?.status) {
        "IN_PROGRESS" -> ExploreStage.ACTIVE
        "COMPLETED" -> ExploreStage.COMPLETED
        else -> ExploreStage.EMPTY
    }
}

class ExploreViewModelFactory(
    private val repository: ExploreRepository,
    private val steps: StepSource,
    private val addressLookup: AddressLookup
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ExploreViewModel(repository, steps, addressLookup) as T
}
