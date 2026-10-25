package com.project.bcv.feature.journey.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.explore.domain.ExploreRepository
import com.project.bcv.feature.explore.domain.ExploreSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface JourneyUiState {
    data object Loading : JourneyUiState
    /** [syncedAtMillis] is when the server last confirmed this progress to the app. */
    data class Loaded(val snapshot: ExploreSnapshot, val syncedAtMillis: Long) : JourneyUiState
    data class Failed(val message: String) : JourneyUiState
    data object SessionExpired : JourneyUiState
}

/** Read-only view of the current journey; progress is owned and confirmed by the server. */
class JourneyViewModel(private val repository: ExploreRepository) : ViewModel() {
    private val mutableState = MutableLiveData<JourneyUiState>(JourneyUiState.Loading)
    val state: LiveData<JourneyUiState> = mutableState

    init { load() }

    fun load() {
        if (mutableState.value !is JourneyUiState.Loaded) mutableState.value = JourneyUiState.Loading
        viewModelScope.launch {
            mutableState.value = try {
                val snapshot = withContext(Dispatchers.IO) { repository.load() }
                JourneyUiState.Loaded(snapshot, System.currentTimeMillis())
            } catch (error: ApiFailure) {
                if (error.code == "UNAUTHORIZED") JourneyUiState.SessionExpired
                else JourneyUiState.Failed(error.message)
            } catch (error: IOException) {
                Log.w(TAG, "Journey load could not reach the API", error)
                JourneyUiState.Failed("Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
            } catch (error: Exception) {
                Log.e(TAG, "Journey load failed", error)
                JourneyUiState.Failed("Chưa tải được hành trình. Vui lòng thử lại.")
            }
        }
    }

    private companion object { const val TAG = "JourneyViewModel" }
}

class JourneyViewModelFactory(private val repository: ExploreRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = JourneyViewModel(repository) as T
}
