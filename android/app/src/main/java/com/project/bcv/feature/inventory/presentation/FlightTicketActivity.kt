package com.project.bcv.feature.inventory.presentation

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.project.bcv.core.di.ProfileModule
import com.project.bcv.core.ui.ScreenMotion
import com.project.bcv.core.ui.request
import com.project.bcv.core.ui.setUpDetailScreen
import com.project.bcv.databinding.ActivityFlightTicketBinding
import com.project.bcv.feature.inventory.data.FlightPlace
import com.project.bcv.feature.inventory.data.FlightTicketStatus
import com.project.bcv.feature.inventory.data.RegionOption

/** Flight ticket: pick another region and a place there, then move the virtual location to it and unlock it. */
class FlightTicketActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFlightTicketBinding
    private val items by lazy { ProfileModule.items(this) }
    private var status: FlightTicketStatus? = null
    private var region: RegionOption? = null
    private var places: List<FlightPlace>? = null
    private var place: FlightPlace? = null
    private var moving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFlightTicketBinding.inflate(layoutInflater)
        setUpDetailScreen(binding.root)
        binding.topBar.screenTitle.text = "Vé máy bay"
        binding.topBar.backButton.setOnClickListener { finish() }
        binding.backToInventory.setOnClickListener { finish() }
        binding.state.stateRetry.setOnClickListener { load() }
        binding.regionPicker.setOnClickListener { pickRegion() }
        binding.placePicker.setOnClickListener { pickPlace() }
        binding.moveButton.setOnClickListener { confirmMove() }
        load()
    }

    private fun load() {
        binding.content.visibility = View.GONE
        with(binding.state) {
            root.visibility = View.VISIBLE
            stateProgress.visibility = View.VISIBLE
            stateText.text = "Đang tải khu vực…"
            stateRetry.visibility = View.GONE
        }
        request(items::flight, { message ->
            binding.state.stateProgress.visibility = View.GONE
            binding.state.stateText.text = message
            binding.state.stateRetry.visibility = View.VISIBLE
        }) { loaded ->
            status = loaded
            binding.state.root.visibility = View.GONE
            binding.content.visibility = View.VISIBLE
            ScreenMotion.reveal(binding.content)
            loaded.regions.firstOrNull()?.let(::selectRegion)
            render()
        }
    }

    private fun selectRegion(option: RegionOption) {
        region = option
        places = null
        place = null
        render()
        request({ items.places(option.id) }, { message ->
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }) { loaded ->
            if (region != option) return@request
            places = loaded
            place = loaded.firstOrNull()
            render()
        }
    }

    private fun render() = with(binding) {
        val status = status ?: return@with
        ownedChip.text = "Đang có × ${status.quantity}"
        currentRegion.text = "Khác khu vực hiện tại: ${status.currentRegion ?: "chưa xác định"}"
        regionName.text = region?.name ?: "Chưa có khu vực khác"
        regionHint.text = region?.let { "${it.placeCount} địa danh đang hoạt động" } ?: "Không có khu vực nào để chọn."
        placeName.text = when {
            region == null -> "Chưa có địa danh"
            places == null -> "Đang tải địa danh…"
            place == null -> "Khu vực chưa có địa danh"
            else -> place!!.name
        }
        placeHint.text = if (place?.discovered == true) "Điểm đến hợp lệ theo hệ thống.\nBạn đã khám phá địa danh này."
            else "Điểm đến hợp lệ theo hệ thống.\nĐến nơi sẽ mở khóa nội dung và thẻ."
        placeHint.visibility = if (place == null) View.GONE else View.VISIBLE

        rulePanel.backgroundTintList = ColorStateList.valueOf(
            if (status.hasActiveJourney) 0xFFF6EEDF.toInt() else 0xFFE6EDF0.toInt())
        ruleTitle.text = if (status.hasActiveJourney) "Có hành trình đang thực hiện" else "Không có hành trình đang thực hiện"
        ruleTitle.compoundDrawableTintList = ColorStateList.valueOf(
            if (status.hasActiveJourney) 0xFFA0702E.toInt() else BLUE)

        val reason = when {
            !status.active -> "Vật phẩm tạm ngưng"
            status.quantity <= 0 -> "Chưa sở hữu vé"
            status.hasActiveJourney -> "Hành trình chưa kết thúc"
            place == null -> "Chọn địa danh đến"
            else -> null
        }
        moveButton.isEnabled = reason == null && !moving
        moveButton.text = if (moving) "Đang di chuyển…" else reason ?: "Di chuyển vị trí ảo"
        moveButton.backgroundTintList = ColorStateList.valueOf(if (moveButton.isEnabled) BLUE else 0xFFE4E7E1.toInt())
        moveButton.setTextColor(if (moveButton.isEnabled) 0xFFFFFFFF.toInt() else 0xFF8A8F86.toInt())
    }

    private fun pickRegion() {
        val regions = status?.regions.orEmpty()
        if (regions.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Chọn khu vực đích")
            .setItems(regions.map { "${it.name} · ${it.placeCount} địa danh" }.toTypedArray()) { _, index ->
                if (regions[index] != region) selectRegion(regions[index])
            }
            .show()
    }

    private fun pickPlace() {
        val options = places.orEmpty()
        if (options.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle("Chọn địa danh đến")
            .setItems(options.map { if (it.discovered) "${it.name} · đã khám phá" else it.name }.toTypedArray()) { _, index ->
                place = options[index]
                render()
            }
            .show()
    }

    private fun confirmMove() {
        val target = place ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Dùng 1 vé máy bay?")
            .setMessage("Vị trí ảo sẽ chuyển đến ${target.name}, ${region?.name}. Bạn còn ${status?.quantity} vé.")
            .setNegativeButton("Huỷ", null)
            .setPositiveButton("Di chuyển") { _, _ -> move(target) }
            .show()
    }

    private fun move(target: FlightPlace) {
        moving = true
        render()
        request({ items.useFlight(target.id) }, { message ->
            moving = false
            render()
            Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
        }) { result ->
            moving = false
            setResult(RESULT_OK)
            MaterialAlertDialogBuilder(this)
                .setTitle("Đã đổi vị trí ảo")
                .setMessage("Vị trí ảo mới: ${result.place}, ${result.region}. Địa danh đã được mở khóa. Còn ${result.quantity} vé.")
                .setCancelable(false)
                .setPositiveButton("Xong") { _, _ -> finish() }
                .show()
        }
    }

    private companion object { const val BLUE = 0xFF3C7DB0.toInt() }
}
