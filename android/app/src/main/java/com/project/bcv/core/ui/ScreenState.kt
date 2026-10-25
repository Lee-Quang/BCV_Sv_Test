package com.project.bcv.core.ui

import android.view.View
import com.project.bcv.databinding.ViewScreenStateBinding

/** First load in progress: the state block replaces [content]. */
fun ViewScreenStateBinding.showLoading(content: View, text: String) {
    content.visibility = View.GONE
    root.visibility = View.VISIBLE
    stateProgress.visibility = View.VISIBLE
    stateText.text = text
    stateRetry.visibility = View.GONE
}

/** First load failed: the message and a retry button replace [content]. */
fun ViewScreenStateBinding.showError(content: View, message: String) {
    content.visibility = View.GONE
    root.visibility = View.VISIBLE
    stateProgress.visibility = View.GONE
    stateText.text = message
    stateRetry.visibility = View.VISIBLE
}

fun ViewScreenStateBinding.showContent(content: View) {
    root.visibility = View.GONE
    content.visibility = View.VISIBLE
}
