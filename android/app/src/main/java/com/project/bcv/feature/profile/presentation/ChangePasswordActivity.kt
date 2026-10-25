package com.project.bcv.feature.profile.presentation

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.project.bcv.core.di.ProfileModule
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.ui.openSignIn
import com.project.bcv.core.ui.setUpDetailScreen
import com.project.bcv.databinding.ActivityChangePasswordBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Change the password while signed in: current password, new password and its confirmation. */
class ChangePasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChangePasswordBinding
    private val profile by lazy { ProfileModule.repository(this) }
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setUpDetailScreen(binding.root)
        binding.topBar.screenTitle.text = "Đổi mật khẩu"
        binding.topBar.backButton.setOnClickListener { finish() }
        listOf(binding.currentLayout to binding.currentInput, binding.passwordLayout to binding.passwordInput,
            binding.confirmLayout to binding.confirmInput).forEach { (layout, input) ->
            input.doAfterTextChanged {
                layout.error = null
                binding.requestError.visibility = View.GONE
            }
        }
        binding.confirmInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) save()
            false
        }
        binding.saveButton.setOnClickListener { save() }
    }

    private fun text(input: com.google.android.material.textfield.TextInputEditText) = input.text?.toString().orEmpty()

    private fun save() = with(binding) {
        if (saving) return@with
        val current = text(currentInput)
        val password = text(passwordInput)
        val confirm = text(confirmInput)
        // Same checks as the server, so the common mistakes show before a round trip.
        currentLayout.error = if (current.isEmpty()) "Vui lòng nhập mật khẩu hiện tại." else null
        passwordLayout.error = when {
            password.length !in 8..128 || password.none(Char::isLetter) || password.none(Char::isDigit) ->
                "Mật khẩu cần 8–128 ký tự, có chữ và số."
            password == current -> "Mật khẩu mới cần khác mật khẩu hiện tại."
            else -> null
        }
        confirmLayout.error = if (confirm != password) "Mật khẩu xác nhận không khớp." else null
        if (currentLayout.error != null || passwordLayout.error != null || confirmLayout.error != null) return@with

        saving = true
        saveButton.isEnabled = false
        saveButton.text = "Đang đổi mật khẩu…"
        lifecycleScope.launch {
            val error = try {
                withContext(Dispatchers.IO) { profile.changePassword(current, password, confirm) }
                null
            } catch (failure: ApiFailure) {
                failure
            } catch (_: IOException) {
                ApiFailure("NETWORK", "Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
            }
            saving = false
            saveButton.isEnabled = true
            saveButton.text = "Đổi mật khẩu"
            when {
                error == null -> {
                    setResult(RESULT_OK)
                    finish()
                }
                error.code == "UNAUTHORIZED" -> openSignIn()
                error.field == "currentPassword" -> currentLayout.error = error.message
                error.field == "password" -> passwordLayout.error = error.message
                error.field == "confirmPassword" -> confirmLayout.error = error.message
                else -> {
                    requestError.text = error.message
                    requestError.visibility = View.VISIBLE
                }
            }
        }
    }
}
