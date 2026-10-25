package com.project.bcv.feature.account.presentation

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.account.domain.AccountRepository
import java.io.IOException
import java.util.concurrent.Executors

enum class AccountMode { REGISTER, LOGIN }
enum class AccountField { EMAIL, PASSWORD, CONFIRM_PASSWORD }

data class AccountUiState(
    val mode: AccountMode = AccountMode.LOGIN,
    val loading: Boolean = false,
    val registrationSuccess: Boolean = false,
    val completed: Boolean = false,
    val onboardingCompleted: Boolean = false,
    val needsInterestSelection: Boolean = false,
    val needsActivityLevel: Boolean = false,
    val needsVirtualLocation: Boolean = false,
    val errors: FieldErrors = FieldErrors(),
    val requestError: String? = null
)

class AccountViewModel(private val repository: AccountRepository) : ViewModel() {
    private val executor = Executors.newSingleThreadExecutor()
    private val mutableState = MutableLiveData(AccountUiState())
    val state: LiveData<AccountUiState> = mutableState

    fun changeMode() {
        val current = mutableState.value ?: return
        if (current.loading) return
        mutableState.value = AccountUiState(
            mode = if (current.mode == AccountMode.REGISTER) AccountMode.LOGIN else AccountMode.REGISTER
        )
    }

    fun onFieldEdited(field: AccountField) {
        val current = mutableState.value ?: return
        if (current.loading) return
        val errors = when (field) {
            AccountField.EMAIL -> current.errors.copy(email = null)
            AccountField.PASSWORD -> current.errors.copy(password = null, confirmPassword = null)
            AccountField.CONFIRM_PASSWORD -> current.errors.copy(confirmPassword = null)
        }
        if (errors != current.errors || current.requestError != null) {
            mutableState.value = current.copy(errors = errors, requestError = null)
        }
    }

    fun submit(rawEmail: String, password: String, confirmPassword: String) {
        val current = mutableState.value ?: return
        if (current.loading || current.registrationSuccess || current.completed ||
            current.needsInterestSelection || current.needsActivityLevel || current.needsVirtualLocation) return
        val email = rawEmail.trim().lowercase()
        val errors = AccountFormValidator.validate(current.mode, email, password, confirmPassword)
        if (errors.hasErrors) {
            mutableState.value = current.copy(errors = errors, requestError = null)
            return
        }

        mutableState.value = current.copy(loading = true, errors = FieldErrors(), requestError = null)
        executor.execute {
            try {
                val session = if (current.mode == AccountMode.REGISTER) {
                    repository.register(email, password, confirmPassword)
                } else {
                    repository.login(email, password)
                }
                mutableState.postValue(if (current.mode == AccountMode.REGISTER) {
                    current.copy(registrationSuccess = true, needsActivityLevel = true,
                        needsVirtualLocation = true)
                } else {
                    current.copy(
                        completed = !session.needsInterestSelection && !session.needsActivityLevel &&
                            !session.needsVirtualLocation,
                        needsInterestSelection = session.needsInterestSelection,
                        needsActivityLevel = session.needsActivityLevel,
                        needsVirtualLocation = session.needsVirtualLocation
                    )
                })
            } catch (error: ApiFailure) {
                mutableState.postValue(current.copy(
                    errors = FieldErrors(
                        email = error.message.takeIf { error.field == "email" },
                        password = error.message.takeIf { error.field == "password" },
                        confirmPassword = error.message.takeIf { error.field == "confirmPassword" }
                    ),
                    requestError = error.message.takeIf { error.field == null }
                ))
            } catch (_: IOException) {
                mutableState.postValue(current.copy(requestError = "Chưa thể kết nối. Kiểm tra mạng và thử lại."))
            } catch (_: Exception) {
                mutableState.postValue(current.copy(requestError = "Đã xảy ra lỗi. Vui lòng thử lại."))
            }
        }
    }

    fun continueRegistration() {
        val current = mutableState.value ?: return
        if (!current.registrationSuccess || current.needsInterestSelection) return
        mutableState.value = current.copy(needsInterestSelection = true)
    }

    fun interestsSaved() {
        val current = mutableState.value ?: return
        mutableState.value = current.copy(
            registrationSuccess = false,
            needsInterestSelection = false,
            completed = !current.needsActivityLevel && !current.needsVirtualLocation,
            onboardingCompleted = !current.needsActivityLevel && !current.needsVirtualLocation
        )
    }

    fun activityLevelSaved() {
        val current = mutableState.value ?: return
        mutableState.value = current.copy(
            needsActivityLevel = false,
            needsVirtualLocation = false,
            completed = true,
            onboardingCompleted = true
        )
    }

    fun virtualLocationSaved() {
        val current = mutableState.value ?: return
        mutableState.value = current.copy(needsVirtualLocation = false,
            completed = true, onboardingCompleted = true)
    }

    fun virtualLocationCancelled() {
        executor.execute { repository.logout() }
        mutableState.value = AccountUiState(mode = AccountMode.LOGIN)
    }

    fun activityLevelCancelled() {
        val current = mutableState.value ?: return
        mutableState.value = current.copy(needsInterestSelection = true)
    }

    fun interestsCancelled() {
        executor.execute { repository.logout() }
        mutableState.value = AccountUiState(mode = AccountMode.LOGIN)
    }

    fun signOut() {
        executor.execute { repository.logout() }
        mutableState.value = AccountUiState(mode = AccountMode.LOGIN)
    }

    override fun onCleared() {
        executor.shutdown()
        super.onCleared()
    }
}

class AccountViewModelFactory(private val repository: AccountRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AccountViewModel(repository) as T
}
