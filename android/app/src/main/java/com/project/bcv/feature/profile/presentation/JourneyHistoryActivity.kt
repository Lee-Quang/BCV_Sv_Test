package com.project.bcv.feature.profile.presentation

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.project.bcv.R
import com.project.bcv.core.di.ProfileModule
import com.project.bcv.core.ui.ScreenMotion
import com.project.bcv.core.ui.request
import com.project.bcv.core.ui.setUpDetailScreen
import com.project.bcv.databinding.ActivityJourneyHistoryBinding
import com.project.bcv.databinding.ItemJourneyHistoryBinding
import com.project.bcv.feature.explore.presentation.PlaceDetailActivity
import com.project.bcv.feature.profile.data.JourneyRecord
import java.text.SimpleDateFormat
import java.util.Locale

/** Every journey, newest first, with where it went, when it started and how it ended. */
class JourneyHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityJourneyHistoryBinding
    private val profile by lazy { ProfileModule.repository(this) }
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJourneyHistoryBinding.inflate(layoutInflater)
        setUpDetailScreen(binding.root)
        binding.topBar.screenTitle.text = "Lịch sử hành trình"
        binding.topBar.backButton.setOnClickListener { finish() }
        binding.state.stateRetry.setOnClickListener { load() }
        load()
    }

    private fun load() {
        with(binding.state) {
            binding.content.visibility = View.GONE
            root.visibility = View.VISIBLE
            stateProgress.visibility = View.VISIBLE
            stateText.text = "Đang tải lịch sử…"
            stateRetry.visibility = View.GONE
        }
        request(profile::history, { message ->
            binding.state.stateProgress.visibility = View.GONE
            binding.state.stateText.text = message
            binding.state.stateRetry.visibility = View.VISIBLE
        }, ::show)
    }

    private fun show(journeys: List<JourneyRecord>) = with(binding) {
        state.root.visibility = View.GONE
        content.visibility = View.VISIBLE
        subtitle.text = if (journeys.isEmpty()) "Bạn chưa bắt đầu hành trình nào. Chọn điểm đến ở Khám phá."
            else "Mới nhất theo thời gian bắt đầu"
        journeyList.removeAllViews()
        journeys.forEach { journeyList.addView(row(it).root) }
        ScreenMotion.reveal(content)
    }

    private fun row(journey: JourneyRecord): ItemJourneyHistoryBinding {
        val item = ItemJourneyHistoryBinding.inflate(layoutInflater, binding.journeyList, false)
        val teal = ContextCompat.getColor(this, R.color.teal)
        val muted = 0xFF7C9086.toInt()
        val (label, chipColor, textColor) = when (journey.status) {
            "IN_PROGRESS" -> Triple("Đang thực hiện", 0xFFDCE8EF.toInt(), 0xFF3C7DB0.toInt())
            "COMPLETED" -> Triple("Hoàn thành", 0xFFDCEBD6.toInt(), teal)
            else -> Triple("Đã từ bỏ", 0xFFF3E6CF.toInt(), 0xFFA0702E.toInt())
        }
        item.statusChip.text = label
        item.statusChip.setTextColor(textColor)
        item.statusChip.backgroundTintList = ColorStateList.valueOf(chipColor)
        val iconColor = if (journey.status == "ABANDONED") 0xFFA0702E.toInt() else teal
        item.startName.text = journey.start
        sizeIcon(item.startName, iconColor, 14)
        item.destinationName.text = journey.destination
        sizeIcon(item.destinationName, iconColor, 18)
        item.startedAt.text = journey.startedAt?.let(dateFormat::format) ?: "—"
        item.completedAt.text = when {
            journey.completedAt != null -> dateFormat.format(journey.completedAt)
            journey.status == "IN_PROGRESS" -> "Chưa hoàn thành"
            else -> "Không có"
        }
        item.completedAt.setTextColor(if (journey.completedAt != null) teal else muted)
        item.root.contentDescription = "$label, từ ${journey.start} đến ${journey.destination}, " +
            "bắt đầu ${item.startedAt.text}, hoàn thành ${item.completedAt.text}"
        item.root.setOnClickListener {
            startActivity(Intent(this, PlaceDetailActivity::class.java)
                .putExtra(PlaceDetailActivity.EXTRA_PLACE_ID, journey.destinationId)
                .putExtra(PlaceDetailActivity.EXTRA_PLACE_NAME, journey.destination)
                .putExtra(PlaceDetailActivity.EXTRA_DISCOVERED, journey.status == "COMPLETED"))
        }
        return item
    }

    private fun sizeIcon(view: TextView, color: Int, sizeDp: Int) {
        view.compoundDrawablesRelative.firstOrNull()?.mutate()?.let { icon ->
            val size = (sizeDp * resources.displayMetrics.density).toInt()
            icon.setBounds(0, 0, size, size)
            icon.setTint(color)
            view.setCompoundDrawablesRelative(icon, null, null, null)
        }
    }
}
