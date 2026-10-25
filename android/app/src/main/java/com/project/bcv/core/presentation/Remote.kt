package com.project.bcv.core.presentation

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.project.bcv.core.network.ApiFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** State of one server request as a screen shows it. */
sealed interface Remote<out T> {
    data object Loading : Remote<Nothing>
    data class Ready<T>(val value: T) : Remote<T>
    /** [field] names the input the server rejected (e.g. "currentPassword"), when it says so. */
    data class Failed(val message: String, val field: String? = null) : Remote<Nothing>
    /** The token was rejected; the screen sends the user back to sign-in. */
    data object SessionExpired : Remote<Nothing>
}

/** A value consumed once, for results that should not replay after rotation (snackbars, navigation). */
class Event<out T>(private val content: T) {
    private var handled = false
    fun take(): T? = if (handled) null else content.also { handled = true }
}

/**
 * Runs [call] on the IO dispatcher and reports it as [Remote] through [onResult]
 * (Loading first, then Ready, Failed or SessionExpired).
 */
fun <T> ViewModel.launchRemote(onResult: (Remote<T>) -> Unit, call: () -> T) {
    onResult(Remote.Loading)
    viewModelScope.launch {
        onResult(
            try {
                Remote.Ready(withContext(Dispatchers.IO) { call() })
            } catch (error: ApiFailure) {
                if (error.code == "UNAUTHORIZED") Remote.SessionExpired else Remote.Failed(error.message, error.field)
            } catch (error: IOException) {
                Log.w("Remote", "Request could not reach the API", error)
                Remote.Failed("Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
            } catch (error: Exception) {
                Log.e("Remote", "Request failed", error)
                Remote.Failed("Chưa tải được dữ liệu. Vui lòng thử lại.")
            }
        )
    }
}

/** Shorthand for [launchRemote] into a LiveData. */
fun <T> ViewModel.loadInto(target: MutableLiveData<Remote<T>>, call: () -> T) = launchRemote({ target.value = it }, call)

/** Factory for a ViewModel built from its repositories. */
inline fun <reified VM : ViewModel> viewModelFactory(crossinline create: () -> VM) = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
}
