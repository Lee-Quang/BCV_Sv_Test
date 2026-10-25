package com.project.bcv.feature.healthconnect.presentation

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.project.bcv.databinding.ActivityHealthPermissionRationaleBinding

class HealthPermissionRationaleActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val binding = ActivityHealthPermissionRationaleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left + dp(24), bars.top + dp(24), bars.right + dp(24), bars.bottom + dp(24))
            insets
        }
        binding.closeButton.setOnClickListener { finish() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
