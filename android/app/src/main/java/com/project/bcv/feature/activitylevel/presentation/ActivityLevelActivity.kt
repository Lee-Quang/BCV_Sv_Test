package com.project.bcv.feature.activitylevel.presentation

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.card.MaterialCardView
import com.project.bcv.R
import com.project.bcv.core.di.ActivityLevelModule
import com.project.bcv.databinding.ActivityActivityLevelBinding
import com.project.bcv.feature.activitylevel.domain.ActivityLevel
import com.project.bcv.feature.healthconnect.presentation.HealthConnectActivity
import com.project.bcv.feature.startingpoint.presentation.StartingPointActivity

class ActivityLevelActivity : AppCompatActivity() {
    private lateinit var binding: ActivityActivityLevelBinding
    private lateinit var model: ActivityLevelViewModel
    private val cards = mutableMapOf<ActivityLevel, MaterialCardView>()
    private var orbitAnimator: ObjectAnimator? = null
    private var healthConnectLaunched = false
    private var healthConnectCompleted = false
    private var startingPointLaunched = false
    private val healthConnectResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        healthConnectLaunched = false
        if (result.resultCode == Activity.RESULT_OK) {
            healthConnectCompleted = true
            launchStartingPoint()
        } else {
            binding.continueButton.text = "Tiếp tục"
            binding.continueButton.isEnabled = true
        }
    }
    private val startingPointResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        startingPointLaunched = false
        if (result.resultCode == Activity.RESULT_OK) {
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            binding.continueButton.text = "Tiếp tục"
            binding.continueButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        healthConnectLaunched = savedInstanceState?.getBoolean("healthConnectLaunched") ?: false
        healthConnectCompleted = savedInstanceState?.getBoolean("healthConnectCompleted") ?: false
        startingPointLaunched = savedInstanceState?.getBoolean("startingPointLaunched") ?: false
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        binding = ActivityActivityLevelBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val enabledStates = arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf())
        binding.continueButton.backgroundTintList = ColorStateList(
            enabledStates, intArrayOf(color(R.color.teal), 0xFFDFE8DC.toInt())
        )
        binding.continueButton.setTextColor(ColorStateList(
            enabledStates, intArrayOf(color(R.color.white), 0xFF809482.toInt())
        ))
        binding.continueButton.iconTint = ColorStateList(
            enabledStates, intArrayOf(color(R.color.white), 0xFF809482.toInt())
        )

        model = ViewModelProvider(
            this, ActivityLevelViewModelFactory(ActivityLevelModule.repository(this))
        )[ActivityLevelViewModel::class.java]
        createCards()
        model.state.observe(this, ::render)
        binding.backButton.setOnClickListener { finish() }
        binding.continueButton.setOnClickListener {
            if (model.state.value?.saved == true) {
                if (healthConnectCompleted) launchStartingPoint() else launchHealthConnect()
            } else model.save()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("healthConnectLaunched", healthConnectLaunched)
        outState.putBoolean("healthConnectCompleted", healthConnectCompleted)
        outState.putBoolean("startingPointLaunched", startingPointLaunched)
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val orbit = binding.orbitDecoration
        orbitAnimator = ObjectAnimator.ofFloat(orbit, View.ROTATION, orbit.rotation, orbit.rotation + 360f).apply {
            duration = 18_000L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    override fun onPause() {
        orbitAnimator?.cancel()
        orbitAnimator = null
        super.onPause()
    }

    private fun createCards() {
        val details = listOf(
            Option(ActivityLevel.LOW, "Ít vận động", "Phần lớn thời gian ngồi,\nít đi bộ hoặc vận động.",
                R.drawable.ic_chair_alt, 0xFFFDF0DE.toInt(), 0xFFA97037.toInt()),
            Option(ActivityLevel.MEDIUM, "Trung bình", "Thường xuyên đi bộ,\nvận động ở mức vừa phải.",
                R.drawable.ic_directions_walk, 0xFFE8F3E7.toInt(), color(R.color.teal)),
            Option(ActivityLevel.HIGH, "Năng động", "Duy trì đi bộ hoặc vận động\nvới tần suất cao.",
                R.drawable.ic_directions_run, 0xFFE5F0E9.toInt(), 0xFF496E60.toInt())
        )
        details.forEachIndexed { index, option ->
            val card = layoutInflater.inflate(R.layout.item_activity_level, binding.activityLevelRows, false) as MaterialCardView
            card.findViewById<TextView>(R.id.activityName).text = option.name
            card.findViewById<TextView>(R.id.activityDescription).text = option.description
            card.findViewById<FrameLayout>(R.id.activityIconBox).backgroundTintList = ColorStateList.valueOf(option.iconBackground)
            card.findViewById<ImageView>(R.id.activityIcon).apply {
                setImageResource(option.icon)
                imageTintList = ColorStateList.valueOf(option.iconColor)
            }
            card.setOnClickListener { model.select(option.level) }
            binding.activityLevelRows.addView(card, LinearLayout.LayoutParams(-1, dp(100)).apply {
                if (index < details.lastIndex) bottomMargin = dp(12)
            })
            cards[option.level] = card
        }
        binding.activityLevelRows.alpha = 0f
        binding.activityLevelRows.translationY = dp(10).toFloat()
        binding.activityLevelRows.animate().alpha(1f).translationY(0f).setDuration(280L).start()
    }

    private fun render(state: ActivityLevelUiState) {
        if (state.saved) {
            // Edited from the profile: saving is the whole task, the sign-up steps that follow do not apply.
            if (intent.getBooleanExtra(EXTRA_EDIT_ONLY, false)) {
                setResult(Activity.RESULT_OK)
                finish()
            } else if (healthConnectCompleted) {
                if (!startingPointLaunched) launchStartingPoint()
            } else if (!healthConnectLaunched) launchHealthConnect()
            return
        }
        cards.forEach { (level, card) ->
            val selected = level == state.selected
            card.strokeColor = color(if (selected) R.color.teal else R.color.field_line)
            card.strokeWidth = dp(if (selected) 2 else 1)
            card.setCardBackgroundColor(color(if (selected) R.color.field_bg else R.color.white))
            card.findViewById<ImageView>(R.id.activitySelectionIndicator).apply {
                setBackgroundResource(if (selected) R.drawable.interest_selection_checked else R.drawable.interest_selection)
                setImageResource(if (selected) R.drawable.ic_check else 0)
                imageTintList = ColorStateList.valueOf(color(R.color.white))
            }
            card.isEnabled = !state.loading && !state.saving
            card.contentDescription = "${card.findViewById<TextView>(R.id.activityName).text}, " +
                if (selected) "đã chọn" else "chưa chọn"
        }
        binding.selectionHint.text = when {
            state.loading -> "Đang tải lựa chọn..."
            state.selected == null -> "Chọn 1 mức để tiếp tục."
            else -> "Đã chọn ${cards[state.selected]?.findViewById<TextView>(R.id.activityName)?.text}."
        }
        binding.continueButton.isEnabled = !state.loading && !state.saving && state.selected != null
        binding.continueButton.text = if (state.saving) "Đang lưu..." else "Lưu và tiếp tục"
        binding.requestError.text = state.error
        binding.requestError.visibility = if (state.error == null) View.GONE else View.VISIBLE
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun launchHealthConnect() {
        if (healthConnectLaunched) return
        healthConnectLaunched = true
        val transition = ActivityOptionsCompat.makeCustomAnimation(
            this, R.anim.interests_enter, R.anim.account_to_interests_exit
        )
        healthConnectResult.launch(Intent(this, HealthConnectActivity::class.java), transition)
    }

    private fun launchStartingPoint() {
        if (startingPointLaunched) return
        startingPointLaunched = true
        val transition = ActivityOptionsCompat.makeCustomAnimation(
            this, R.anim.interests_enter, R.anim.account_to_interests_exit
        )
        startingPointResult.launch(Intent(this, StartingPointActivity::class.java), transition)
    }

    private data class Option(
        val level: ActivityLevel,
        val name: String,
        val description: String,
        val icon: Int,
        val iconBackground: Int,
        val iconColor: Int
    )

    companion object {
        const val EXTRA_EDIT_ONLY = "edit_only"
    }
}
