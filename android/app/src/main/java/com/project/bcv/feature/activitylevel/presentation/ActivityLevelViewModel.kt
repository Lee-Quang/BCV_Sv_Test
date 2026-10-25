package com.project.bcv.feature.activitylevel.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.project.bcv.feature.activitylevel.domain.ActivityLevel
import com.project.bcv.feature.activitylevel.domain.ActivityLevelRepository
import java.io.IOException
import java.util.concurrent.Executors

data class ActivityLevelUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val selected: ActivityLevel? = null,
    val error: String? = null,
    val saved: Boolean = false
)

class ActivityLevelViewModel(private val repository: ActivityLevelRepository) : ViewModel() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mutableState = MutableLiveData(ActivityLevelUiState())
    val state: LiveData<ActivityLevelUiState> = mutableState

    init { load() }

    fun load() {
        val current = mutableState.value ?: ActivityLevelUiState()
        mutableState.value = current.copy(loading = true, error = null)
        executor.execute {
            try {
                val level = repository.load()
                mutableState.postValue(current.copy(loading = false, selected = level, error = null))
            } catch (error: Exception) {
                mutableState.postValue(current.copy(loading = false, error = message(error)))
            }
        }
    }

    fun select(level: ActivityLevel) {
        val current = mutableState.value ?: return
        if (current.loading || current.saving) return
        mutableState.value = current.copy(selected = level, error = null)
    }

    fun save() {
        val current = mutableState.value ?: return
        val level = current.selected ?: return
        if (current.loading || current.saving || current.saved) return
        mutableState.value = current.copy(saving = true, error = null)
        executor.execute {
            try {
                repository.save(level)
                mutableState.postValue(current.copy(saved = true))
            } catch (error: Exception) {
                mutableState.postValue(current.copy(error = message(error)))
            }
        }
    }

    private fun message(error: Exception) = when (error) {
        is IOException -> "Chưa thể kết nối. Kiểm tra mạng và thử lại."
        else -> error.message ?: "Đã xảy ra lỗi. Vui lòng thử lại."
    }

    override fun onCleared() {
        executor.shutdown()
        super.onCleared()
    }
}

class ActivityLevelViewModelFactory(private val repository: ActivityLevelRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ActivityLevelViewModel(repository) as T
}
