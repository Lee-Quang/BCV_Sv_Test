package com.project.bcv.feature.account.profile.presentation

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.textfield.TextInputEditText
import com.project.bcv.core.di.ProfileModule
import com.project.bcv.core.presentation.viewModelFactory
import com.project.bcv.core.ui.openSignIn
import com.project.bcv.core.ui.setUpDetailScreen
import com.project.bcv.databinding.ActivityChangePasswordBinding

/** Change the password while signed in: current password, new password and its confirmation. */
class ChangePasswordActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChangePasswordBinding
    private lateinit var model: ChangePasswordViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChangePasswordBinding.inflate(layoutInflater)
        setUpDetailScreen(binding.root)
        binding.topBar.screenTitle.text = "Đổi mật khẩu"
        binding.topBar.backButton.setOnClickListener { finish() }
        model = ViewModelProvider(this, viewModelFactory { ChangePasswordViewModel(ProfileModule.repository(this)) })[
            ChangePasswordViewModel::class.java]
        listOf(binding.currentInput, binding.passwordInput, binding.confirmInput).forEach { input ->
            input.doAfterTextChanged { if (input.hasFocus()) model.clearErrors() }
        }
        binding.confirmInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) submit()
            false
        }
        binding.saveButton.setOnClickListener { submit() }
        model.state.observe(this, ::render)
    }

    private fun text(input: TextInputEditText) = input.text?.toString().orEmpty()

    private fun submit() = with(binding) {
        model.submit(text(currentInput), text(passwordInput), text(confirmInput))
    }

    private fun render(state: ChangePasswordState) = with(binding) {
        if (state.sessionExpired) { openSignIn(); return@with }
        if (state.done) {
            setResult(RESULT_OK)
            finish()
            return@with
        }
        currentLayout.error = state.currentError
        passwordLayout.error = state.passwordError
        confirmLayout.error = state.confirmError
        requestError.text = state.requestError
        requestError.visibility = if (state.requestError == null) View.GONE else View.VISIBLE
        saveButton.isEnabled = !state.saving
        saveButton.text = if (state.saving) "Đang đổi mật khẩu…" else "Đổi mật khẩu"
    }
}
