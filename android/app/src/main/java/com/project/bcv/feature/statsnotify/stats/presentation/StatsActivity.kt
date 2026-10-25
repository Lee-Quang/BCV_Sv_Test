package com.project.bcv.feature.statsnotify.stats.presentation

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.project.bcv.R
import androidx.lifecycle.ViewModelProvider
import com.project.bcv.core.di.StatsNotifyModule
import com.project.bcv.core.presentation.Remote
import com.project.bcv.core.presentation.viewModelFactory
import com.project.bcv.core.ui.ScreenMotion
import com.project.bcv.core.ui.openSignIn
import com.project.bcv.core.ui.setUpDetailScreen
import com.project.bcv.core.ui.showContent
import com.project.bcv.core.ui.showError
import com.project.bcv.core.ui.showLoading
import com.project.bcv.databinding.ActivityStatsBinding
import com.project.bcv.feature.statsnotify.stats.domain.ProfileStats
import com.project.bcv.feature.journey.presentation.JourneyHistoryActivity
import java.text.NumberFormat
import java.util.Locale

/** Personal statistics: confirmed steps, places opened, cards owned and this cycle's quest progress. */
class StatsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStatsBinding
    private lateinit var model: StatsViewModel
    private var shown: ProfileStats? = null
    private var resumedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setUpDetailScreen(binding.root)
        binding.topBar.screenTitle.text = "Thống kê cá nhân"
        binding.topBar.backButton.setOnClickListener { finish() }
        model = ViewModelProvider(this, viewModelFactory { StatsViewModel(StatsNotifyModule.stats(this)) })[
            StatsViewModel::class.java]
        binding.state.stateRetry.setOnClickListener { model.load() }
        with(binding.historyLink) {
            rowIcon.setImageResource(R.drawable.ic_history)
            rowTitle.text = "Lịch sử hành trình"
            rowSubtitle.text = "Xem điểm đi, điểm đến và trạng thái"
            root.setOnClickListener { startActivity(Intent(this@StatsActivity, JourneyHistoryActivity::class.java)) }
        }
        model.state.observe(this, ::render)
    }

    override fun onResume() {
        super.onResume()
        if (resumedOnce) model.load()
        resumedOnce = true
    }

    /** Numbers already shown stay while a refresh runs or fails; only the first load shows its state. */
    private fun render(state: Remote<ProfileStats>) {
        when (state) {
            Remote.Loading -> if (shown == null) binding.state.showLoading(binding.content, "Đang tải thống kê…")
            is Remote.Failed -> if (shown == null) binding.state.showError(binding.content, state.message)
            Remote.SessionExpired -> openSignIn()
            is Remote.Ready -> show(state.value)
        }
    }

    private fun show(stats: ProfileStats) = with(binding) {
        val first = shown == null
        shown = stats
        state.showContent(content)
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
