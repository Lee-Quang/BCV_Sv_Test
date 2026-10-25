package com.project.bcv.core.ui

import android.animation.ValueAnimator
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

/** Short content entrance for state changes within one Activity. */
object ScreenMotion {
    fun reveal(view: View) {
        view.animate().cancel()
        if (!ValueAnimator.areAnimatorsEnabled() || !view.isLaidOut || view.visibility != View.VISIBLE) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0.84f
        view.translationY = 8f * view.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setDuration(220L)
            .setInterpolator(FastOutSlowInInterpolator()).start()
    }
}
