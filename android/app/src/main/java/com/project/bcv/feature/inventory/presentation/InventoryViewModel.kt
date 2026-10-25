package com.project.bcv.feature.inventory.presentation

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.inventory.domain.InventoryOverview
import com.project.bcv.feature.inventory.domain.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

sealed interface InventoryUiState {
    /** First load: quantities are not known yet. */
    data object Loading : InventoryUiState
    /** First load failed: never shown as an empty inventory. */
    data class Failed(val message: String) : InventoryUiState
    /**
     * Confirmed quantities. While [refreshing], or after a refresh failed ([refreshError]),
     * the last confirmed numbers stay on screen instead of being cleared or guessed.
     */
    data class Loaded(
        val overview: InventoryOverview,
        val refreshing: Boolean = false,
        val refreshError: String? = null
    ) : InventoryUiState
    data object SessionExpired : InventoryUiState
}

class InventoryViewModel(private val repository: InventoryRepository) : ViewModel() {
    private val mutableState = MutableLiveData<InventoryUiState>(InventoryUiState.Loading)
    val state: LiveData<InventoryUiState> = mutableState

    init { load() }

    fun load() {
        val previous = mutableState.value as? InventoryUiState.Loaded
        mutableState.value = previous?.copy(refreshing = true, refreshError = null) ?: InventoryUiState.Loading
        viewModelScope.launch {
            mutableState.value = try {
                InventoryUiState.Loaded(withContext(Dispatchers.IO) { repository.load() })
            } catch (error: ApiFailure) {
                if (error.code == "UNAUTHORIZED") InventoryUiState.SessionExpired
                else failure(previous, error.message)
            } catch (error: IOException) {
                Log.w(TAG, "Inventory could not reach the API", error)
                failure(previous, "Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
            } catch (error: Exception) {
                Log.e(TAG, "Inventory load failed", error)
                failure(previous, "Chưa tải được kho vật phẩm. Vui lòng thử lại.")
            }
        }
    }

    private fun failure(previous: InventoryUiState.Loaded?, message: String): InventoryUiState =
        previous?.copy(refreshing = false, refreshError = message) ?: InventoryUiState.Failed(message)

    private companion object { const val TAG = "InventoryViewModel" }
}

class InventoryViewModelFactory(private val repository: InventoryRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = InventoryViewModel(repository) as T
}
