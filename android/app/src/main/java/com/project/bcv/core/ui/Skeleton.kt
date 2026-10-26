package com.project.bcv.core.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import com.project.bcv.R

/**
 * Placeholder shaped like the content it stands in for (same cards, rows, image and text sizes),
 * pulsing gently until the data arrives. Blocks use the Skeleton.* styles.
 */
object Skeleton {
    /** Shows [skeleton] in place of [content] and starts its pulse; [others] (e.g. an error panel) are hidden. */
    fun show(skeleton: View, content: View, vararg others: View) {
        content.visibility = View.GONE
        others.forEach { it.visibility = View.GONE }
        skeleton.visibility = View.VISIBLE
        if (skeleton.getTag(R.id.skeleton_pulse) != null || !ValueAnimator.areAnimatorsEnabled()) return
        val pulse = ObjectAnimator.ofFloat(skeleton, View.ALPHA, 1f, 0.5f).apply {
            duration = 850L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
        skeleton.setTag(R.id.skeleton_pulse, pulse)
        if (skeleton.getTag(R.id.skeleton_detach) == null) {
            // A destroyed screen must not keep the animator running; registered once per view.
            val listener = object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit
                override fun onViewDetachedFromWindow(view: View) = hide(view)
            }
            skeleton.addOnAttachStateChangeListener(listener)
            skeleton.setTag(R.id.skeleton_detach, listener)
        }
    }

    /** Stops the pulse and hides [skeleton]; the caller shows the content or error itself. */
    fun hide(skeleton: View) {
        (skeleton.getTag(R.id.skeleton_pulse) as? ValueAnimator)?.cancel()
        skeleton.setTag(R.id.skeleton_pulse, null)
        skeleton.alpha = 1f
        skeleton.visibility = View.GONE
    }
}
