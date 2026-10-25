package com.project.bcv.feature.profile.presentation

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.project.bcv.R
import com.project.bcv.core.di.ProfileModule
import com.project.bcv.core.ui.ScreenMotion
import com.project.bcv.core.ui.request
import com.project.bcv.core.ui.setUpDetailScreen
import com.project.bcv.databinding.ActivityEditProfileBinding
import com.project.bcv.feature.profile.data.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Edit the display name and avatar; the email is shown only. Also the way into changing the password. */
class EditProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityEditProfileBinding
    private val profile by lazy { ProfileModule.repository(this) }
    private var saved: Profile? = null
    /** A newly picked avatar not saved yet. */
    private var pendingAvatar: String? = null
    private var saving = false

    private val pickAvatar = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val encoded = runCatching { withContext(Dispatchers.Default) { Avatars.encode(contentResolver, uri) } }.getOrNull()
            if (encoded == null) {
                status(error = true, "Chưa đọc được ảnh", "Hãy chọn một ảnh khác.")
                return@launch
            }
            pendingAvatar = encoded
            binding.statusPanel.visibility = View.GONE
            showAvatar()
            updateSaveButton()
        }
    }

    private val changePassword = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK)
            status(error = false, "Đã đổi mật khẩu", "Dùng mật khẩu mới cho lần đăng nhập sau.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditProfileBinding.inflate(layoutInflater)
        setUpDetailScreen(binding.root)
        binding.topBar.screenTitle.text = "Chỉnh sửa hồ sơ"
        binding.topBar.backButton.setOnClickListener { finish() }
        binding.state.stateRetry.setOnClickListener { load() }
        binding.changeAvatar.setOnClickListener {
            pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        with(binding.passwordLink) {
            rowIcon.setImageResource(R.drawable.ic_lock)
            rowTitle.text = "Đổi mật khẩu"
            rowSubtitle.text = "Nhập mật khẩu hiện tại và mật khẩu mới"
            root.setOnClickListener {
                changePassword.launch(Intent(this@EditProfileActivity, ChangePasswordActivity::class.java))
            }
        }
        binding.nameInput.doAfterTextChanged {
            binding.nameLayout.error = null
            if (binding.nameInput.hasFocus()) binding.statusPanel.visibility = View.GONE
            showAvatar()
            updateSaveButton()
        }
        binding.nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && binding.saveButton.isEnabled) save()
            false
        }
        binding.saveButton.setOnClickListener { save() }
        load()
    }

    private fun load() {
        with(binding.state) {
            binding.content.visibility = View.GONE
            root.visibility = View.VISIBLE
            stateProgress.visibility = View.VISIBLE
            stateText.text = "Đang tải hồ sơ…"
            stateRetry.visibility = View.GONE
        }
        request(profile::profile, { message ->
            binding.state.stateProgress.visibility = View.GONE
            binding.state.stateText.text = message
            binding.state.stateRetry.visibility = View.VISIBLE
        }) { account ->
            saved = account
            binding.state.root.visibility = View.GONE
            binding.content.visibility = View.VISIBLE
            binding.nameInput.setText(account.name)
            binding.emailValue.text = account.email
            showAvatar()
            updateSaveButton()
            ScreenMotion.reveal(binding.content)
        }
    }

    private fun typedName() = binding.nameInput.text?.toString().orEmpty().trim().replace(Regex("\\s+"), " ")

    private fun showAvatar(): Unit = with(binding) {
        val value = pendingAvatar ?: saved?.avatar
        val bitmap = Avatars.immediate(value)
        if (bitmap == null && value != null) lifecycleScope.launch {
            // Fetched once; later calls find it in the cache.
            if (Avatars.load(value) != null && value == (pendingAvatar ?: saved?.avatar)) showAvatar()
        }
        avatarImage.setImageBitmap(bitmap)
        avatarImage.visibility = if (bitmap == null) View.GONE else View.VISIBLE
        avatarInitials.text = Avatars.initials(typedName().ifBlank { saved?.name.orEmpty() })
        changeAvatar.text = if (bitmap == null) "Thêm ảnh đại diện" else "Đổi ảnh đại diện"
    }

    private fun updateSaveButton() = with(binding.saveButton) {
        val changed = saved != null && (typedName() != saved?.name || pendingAvatar != null)
        isEnabled = changed && !saving
        text = if (saving) "Đang lưu…" else "Lưu thay đổi"
        backgroundTintList = ColorStateList.valueOf(
            if (isEnabled) ContextCompat.getColor(context, R.color.teal) else 0xFFE4E7E1.toInt())
        setTextColor(if (isEnabled) 0xFFFFFFFF.toInt() else 0xFF8A8F86.toInt())
    }

    private fun save() {
        val name = typedName()
        if (name.length < 2) {
            binding.nameLayout.error = "Họ và tên cần ít nhất 2 ký tự."
            return
        }
        saving = true
        updateSaveButton()
        request({ profile.updateProfile(name, pendingAvatar) }, { message ->
            saving = false
            updateSaveButton()
            status(error = true, "Chưa lưu được hồ sơ", message)
        }) { account ->
            saving = false
            saved = account
            pendingAvatar = null
            binding.nameInput.setText(account.name)
            binding.nameInput.clearFocus()
            showAvatar()
            updateSaveButton()
            setResult(RESULT_OK)
            status(error = false, "Đã cập nhật hồ sơ", "Thông tin mới đã được hệ thống xác nhận.")
        }
    }

    private fun status(error: Boolean, title: String, body: String) = with(binding) {
        statusPanel.visibility = View.VISIBLE
        statusPanel.backgroundTintList = ColorStateList.valueOf(if (error) 0xFFFBE9E5.toInt() else 0xFFE8F0E2.toInt())
        val accent = if (error) 0xFFC2553F.toInt() else ContextCompat.getColor(this@EditProfileActivity, R.color.teal)
        statusTitle.text = title
        statusTitle.compoundDrawableTintList = ColorStateList.valueOf(accent)
        statusBody.text = body
    }
}
