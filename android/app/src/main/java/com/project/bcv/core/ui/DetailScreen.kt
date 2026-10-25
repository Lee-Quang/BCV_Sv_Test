package com.project.bcv.core.ui

import android.content.Intent
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.project.bcv.core.network.ApiFailure
import com.project.bcv.feature.account.data.local.SessionPreferences
import com.project.bcv.feature.account.presentation.AccountActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Edge-to-edge with light bars and the content padded clear of the system bars, as on every non-tab screen. */
fun AppCompatActivity.setUpDetailScreen(root: View) {
    enableEdgeToEdge()
    WindowCompat.getInsetsController(window, window.decorView).apply {
        isAppearanceLightStatusBars = true
        isAppearanceLightNavigationBars = true
    }
    setContentView(root)
    ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}

/** The token was rejected: forget it and start over at sign-in. */
fun AppCompatActivity.openSignIn() {
    SessionPreferences(this).token = null
    startActivity(Intent(this, AccountActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
}

/**
 * Runs [call] off the main thread and hands back the result or a user-facing message.
 * An expired session goes straight to sign-in and calls neither callback.
 */
fun <T> AppCompatActivity.request(call: () -> T, onError: (String) -> Unit, onSuccess: (T) -> Unit) {
    lifecycleScope.launch {
        val result = try {
            Result.success(withContext(Dispatchers.IO) { call() })
        } catch (error: Exception) {
            Result.failure(error)
        }
        result.onSuccess(onSuccess).onFailure { error ->
            when {
                error is ApiFailure && error.code == "UNAUTHORIZED" -> openSignIn()
                error is ApiFailure -> onError(error.message)
                error is IOException -> onError("Không kết nối được máy chủ. Kiểm tra Wi-Fi rồi thử lại.")
                else -> onError("Chưa tải được dữ liệu. Vui lòng thử lại.")
            }
        }
    }
}
