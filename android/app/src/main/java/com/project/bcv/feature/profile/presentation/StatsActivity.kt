package com.project.bcv.feature.profile.presentation

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.project.bcv.R
import com.project.bcv.core.di.ProfileModule
import com.project.bcv.core.ui.ScreenMotion
import com.project.bcv.core.ui.request
import com.project.bcv.core.ui.setUpDetailScreen
import com.project.bcv.databinding.ActivityStatsBinding
import com.project.bcv.feature.profile.data.ProfileStats
import java.text.NumberFormat
import java.util.Locale

/** Personal statistics: confirmed steps, places opened, cards owned and this cycle's quest progress. */
class StatsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStatsBinding
    private val profile by lazy { ProfileModule.repository(this) }
    private var shown: ProfileStats? = null
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setUpDetailScreen(binding.root)
        binding.topBar.screenTitle.text = "Thống kê cá nhân"
        binding.topBar.backButton.setOnClickListener { finish() }
        binding.state.stateRetry.setOnClickListener { load() }
        with(binding.historyLink) {
            rowIcon.setImageResource(R.drawable.ic_history)
            rowTitle.text = "Lịch sử hành trình"
            rowSubtitle.text = "Xem điểm đi, điểm đến và trạng thái"
            root.setOnClickListener { startActivity(Intent(this@StatsActivity, JourneyHistoryActivity::class.java)) }
        }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce) load()
        resumedOnce = true
    }

    private fun load() {
        if (shown == null) with(binding.state) {
            binding.content.visibility = View.GONE
            root.visibility = View.VISIBLE
            stateProgress.visibility = View.VISIBLE
            stateText.text = "Đang tải thống kê…"
            stateRetry.visibility = View.GONE
        }
        request(profile::stats, { message ->
            if (shown != null) return@request
            binding.state.stateProgress.visibility = View.GONE
            binding.state.stateText.text = message
            binding.state.stateRetry.visibility = View.VISIBLE
        }, ::show)
    }

    private fun show(stats: ProfileStats) = with(binding) {
        val first = shown == null
        shown = stats
        state.root.visibility = View.GONE
        content.visibility = View.VISIBLE
        val number = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
        totalSteps.text = number.format(stats.totalSteps)
        stepsCard.contentDescription = "Tổng ${number.format(stats.totalSteps)} bước chân hợp lệ đã ghi nhận"
        placesCount.text = stats.discoveredPlaces.toString()
        placesTile.contentDescription = "${stats.discoveredPlaces} địa danh đã mở"
        cardsCount.text = stats.ownedCards.toString()
        cardsTile.contentDescription = "${stats.ownedCards} thẻ đã sở hữu"
        dailyValue.text = "${stats.daily.completed} / ${stats.daily.total}"
        dailyRow.contentDescription = "Nhiệm vụ hằng ngày: hoàn thành ${stats.daily.completed} trên ${stats.daily.total}"
        dailyProgress.max = stats.daily.total.coerceAtLeast(1)
        dailyProgress.setProgressCompat(stats.daily.completed, !first)
        weeklyValue.text = "${stats.weekly.completed} / ${stats.weekly.total}"
        weeklyRow.contentDescription = "Nhiệm vụ hằng tuần: hoàn thành ${stats.weekly.completed} trên ${stats.weekly.total}"
        weeklyProgress.max = stats.weekly.total.coerceAtLeast(1)
        weeklyProgress.setProgressCompat(stats.weekly.completed, !first)
        if (first) ScreenMotion.reveal(content)
    }
}
