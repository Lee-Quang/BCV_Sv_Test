package com.project.bcv.feature.inventory.presentation

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.project.bcv.R
import com.project.bcv.core.di.ProfileModule
import com.project.bcv.core.ui.ScreenMotion
import com.project.bcv.core.ui.request
import com.project.bcv.core.ui.setUpDetailScreen
import com.project.bcv.databinding.ActivitySpeedShoesBinding
import com.project.bcv.feature.inventory.data.SpeedShoesStatus
import java.text.NumberFormat
import java.util.Locale

/** Speed shoes: the configured boost, the journey it would apply to, and using one. */
class SpeedShoesActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySpeedShoesBinding
    private val items by lazy { ProfileModule.items(this) }
    private val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).apply { maximumFractionDigits = 2 }
    private var shown: SpeedShoesStatus? = null
    private var using = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySpeedShoesBinding.inflate(layoutInflater)
        setUpDetailScreen(binding.root)
        binding.topBar.screenTitle.text = "Giày tăng tốc"
        binding.topBar.backButton.setOnClickListener { finish() }
        binding.backToInventory.setOnClickListener { finish() }
        binding.state.stateRetry.setOnClickListener { load() }
        binding.useButton.setOnClickListener { confirmUse() }
        load()
    }

    private fun load() {
        showState(null)
        request(items::shoes, ::showState, ::show)
    }

    private fun showState(error: String?) = with(binding.state) {
        if (shown != null && error == null) return@with
        binding.content.visibility = View.GONE
        root.visibility = View.VISIBLE
        stateProgress.visibility = if (error == null) View.VISIBLE else View.GONE
        stateText.text = error ?: "Đang tải hiệu ứng…"
        stateRetry.visibility = if (error == null) View.GONE else View.VISIBLE
    }

    private fun show(status: SpeedShoesStatus) = with(binding) {
        val first = shown == null
        shown = status
        state.root.visibility = View.GONE
        content.visibility = View.VISIBLE
        val effect = status.effect?.takeIf { it.remainingSteps > 0 }
        val factor = "×" + number.format(effect?.multiplier ?: status.multiplier)
        multiplier.text = factor
        multiplier.contentDescription = "Nhân ${number.format(effect?.multiplier ?: status.multiplier)} lần"
        ownedChip.text = "Đang có × ${status.quantity}"
        factorValue.text = factor
        rangeValue.text = "${number.format(effect?.range ?: status.range)} bước hợp lệ"
        effectNote.text = if (effect != null) "Đang áp dụng · còn ${number.format(effect.remainingSteps)} bước được tăng tốc."
            else "Thông số từ cấu hình vật phẩm hiện tại."

        val journey = status.journey
        val blocked = journey == null || effect != null
        journeyPanel.backgroundTintList = ColorStateList.valueOf(if (blocked) 0xFFF6EEDF.toInt() else 0xFFE8F0E2.toInt())
        journeyLabel.text = if (journey != null) "Hành trình đang thực hiện" else "Chưa có hành trình đang thực hiện"
        journeyName.text = journey?.destination ?: "Bắt đầu một hành trình ở Khám phá"
        journeyCheck.text = when {
            journey == null -> "Giày chỉ dùng được khi đang có hành trình."
            effect != null -> "Hiệu ứng trước còn hiệu lực, chưa thể kích hoạt thêm."
            else -> "Đã kiểm tra điều kiện tương thích."
        }

        val reason = when {
            !status.active -> "Vật phẩm tạm ngưng"
            status.quantity <= 0 -> "Chưa sở hữu giày"
            journey == null || effect != null -> "Chưa thể sử dụng"
            else -> null
        }
        useButton.isEnabled = reason == null && !using
        useButton.text = if (using) "Đang kích hoạt…" else reason ?: "Sử dụng 1 giày"
        useButton.backgroundTintList = ColorStateList.valueOf(
            if (useButton.isEnabled) ContextCompat.getColor(this@SpeedShoesActivity, R.color.teal) else 0xFFE4E7E1.toInt())
        useButton.setTextColor(if (useButton.isEnabled) 0xFFFFFFFF.toInt() else 0xFF8A8F86.toInt())
        if (first) ScreenMotion.reveal(content)
    }

    private fun confirmUse() {
        val status = shown ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Dùng 1 giày tăng tốc?")
            .setMessage("Áp dụng cho hành trình đến ${status.journey?.destination}. Bạn còn ${status.quantity} giày.")
            .setNegativeButton("Huỷ", null)
            .setPositiveButton("Sử dụng") { _, _ -> use() }
            .show()
    }

    private fun use() {
        using = true
        shown?.let(::show)
        request(items::useShoes, { message ->
            using = false
            shown?.let(::show)
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }) { status ->
            using = false
            show(status)
            setResult(RESULT_OK)
            Snackbar.make(binding.root, "Đã kích hoạt giày tăng tốc. Còn ${status.quantity} giày.", Snackbar.LENGTH_LONG).show()
        }
    }
}
