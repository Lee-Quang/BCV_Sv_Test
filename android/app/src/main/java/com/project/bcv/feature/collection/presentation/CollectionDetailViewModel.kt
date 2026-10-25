package com.project.bcv.feature.collection.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.collection.domain.CollectionDetail
import com.project.bcv.feature.collection.domain.CollectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface CollectionDetailUiState {
    data object Loading : CollectionDetailUiState
    data class Loaded(val detail: CollectionDetail) : CollectionDetailUiState
    data class Failed(val message: String) : CollectionDetailUiState
    data object SessionExpired : CollectionDetailUiState
}

class CollectionDetailViewModel(private val repository: CollectionRepository) : ViewModel() {
    private val mutableState = MutableLiveData<CollectionDetailUiState>(CollectionDetailUiState.Loading)
    val state: LiveData<CollectionDetailUiState> = mutableState

    fun load(collectionId: Long) {
        if (mutableState.value !is CollectionDetailUiState.Loaded) mutableState.value = CollectionDetailUiState.Loading
        viewModelScope.launch {
            mutableState.value = try {
                CollectionDetailUiState.Loaded(withContext(Dispatchers.IO) { repository.loadDetail(collectionId) })
            } catch (error: ApiFailure) {
                if (error.code == "UNAUTHORIZED") CollectionDetailUiState.SessionExpired
                else CollectionDetailUiState.Failed(error.message)
            } catch (error: IOException) {
                Log.w(TAG, "Collection detail could not reach the API", error)
                CollectionDetailUiState.Failed("Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
            } catch (error: Exception) {
                Log.e(TAG, "Collection detail load failed", error)
                CollectionDetailUiState.Failed("Chưa tải được bộ sưu tập. Vui lòng thử lại.")
            }
        }
    }

    private companion object { const val TAG = "CollectionDetailVM" }
}

class CollectionDetailViewModelFactory(private val repository: CollectionRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CollectionDetailViewModel(repository) as T
}
