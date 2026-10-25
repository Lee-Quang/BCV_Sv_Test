package com.project.bcv.feature.collection.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.collection.domain.CollectionOverview
import com.project.bcv.feature.collection.domain.CollectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface CollectionUiState {
    data object Loading : CollectionUiState
    data class Loaded(val overview: CollectionOverview) : CollectionUiState
    data class Failed(val message: String) : CollectionUiState
    data object SessionExpired : CollectionUiState
}

class CollectionViewModel(private val repository: CollectionRepository) : ViewModel() {
    private val mutableState = MutableLiveData<CollectionUiState>(CollectionUiState.Loading)
    val state: LiveData<CollectionUiState> = mutableState

    init { load() }

    fun load() {
        // A refresh keeps the shown collections instead of flashing back to the loading state.
        if (mutableState.value !is CollectionUiState.Loaded) mutableState.value = CollectionUiState.Loading
        viewModelScope.launch {
            mutableState.value = try {
                CollectionUiState.Loaded(withContext(Dispatchers.IO) { repository.load() })
            } catch (error: ApiFailure) {
                if (error.code == "UNAUTHORIZED") CollectionUiState.SessionExpired
                else CollectionUiState.Failed(error.message)
            } catch (error: IOException) {
                Log.w(TAG, "Collections could not reach the API", error)
                CollectionUiState.Failed("Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
            } catch (error: Exception) {
                Log.e(TAG, "Collections load failed", error)
                CollectionUiState.Failed("Chưa tải được bộ sưu tập. Vui lòng thử lại.")
            }
        }
    }

    private companion object { const val TAG = "CollectionViewModel" }
}

class CollectionViewModelFactory(private val repository: CollectionRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CollectionViewModel(repository) as T
}
