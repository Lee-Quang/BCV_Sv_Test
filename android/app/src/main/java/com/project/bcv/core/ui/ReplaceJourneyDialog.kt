package com.project.bcv.core.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.Window
import com.project.bcv.databinding.DialogReplaceJourneyBinding

/**
 * "Chuyển sang hành trình mới?": asked when a destination is chosen while another journey is in
 * progress. [onReplace] runs for "Chuyển hành trình"; [onKeep] for "Giữ hành trình cũ", back or
 * a tap outside.
 */
fun Activity.confirmReplaceJourney(onReplace: () -> Unit, onKeep: () -> Unit = {}) {
    val binding = DialogReplaceJourneyBinding.inflate(layoutInflater)
    var replaced = false
    val dialog = Dialog(this).apply {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(binding.root)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val margin = (20 * resources.displayMetrics.density).toInt()
            setLayout(resources.displayMetrics.widthPixels - 2 * margin, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        setOnDismissListener { if (!replaced) onKeep() }
    }
    binding.replaceButton.setOnClickListener {
        replaced = true
        dialog.dismiss()
        onReplace()
    }
    binding.keepButton.setOnClickListener { dialog.dismiss() }
    dialog.show()
}
