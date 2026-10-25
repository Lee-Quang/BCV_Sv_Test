package com.project.bcv.feature.quest.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.quest.domain.QuestOverview
import com.project.bcv.feature.quest.domain.QuestRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface QuestUiState {
    data object Loading : QuestUiState
    data class Loaded(val overview: QuestOverview) : QuestUiState
    data class Failed(val message: String) : QuestUiState
    data object SessionExpired : QuestUiState
}

class QuestViewModel(private val repository: QuestRepository) : ViewModel() {
    private val mutableState = MutableLiveData<QuestUiState>(QuestUiState.Loading)
    val state: LiveData<QuestUiState> = mutableState

    init { load() }

    fun load() {
        // A refresh keeps the shown collections instead of flashing back to the loading state.
        if (mutableState.value !is QuestUiState.Loaded) mutableState.value = QuestUiState.Loading
        viewModelScope.launch {
            mutableState.value = try {
                QuestUiState.Loaded(withContext(Dispatchers.IO) { repository.load() })
            } catch (error: ApiFailure) {
                if (error.code == "UNAUTHORIZED") QuestUiState.SessionExpired
                else QuestUiState.Failed(error.message)
            } catch (error: IOException) {
                Log.w(TAG, "Quests could not reach the API", error)
                QuestUiState.Failed("Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
            } catch (error: Exception) {
                Log.e(TAG, "Quests load failed", error)
                QuestUiState.Failed("Chưa tải được nhiệm vụ. Vui lòng thử lại.")
            }
        }
    }

    private companion object { const val TAG = "QuestViewModel" }
}

class QuestViewModelFactory(private val repository: QuestRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = QuestViewModel(repository) as T
}
