package com.project.bcv.feature.account.auth.data.local

import android.content.Context
import com.project.bcv.core.steps.DeviceStepTracker

class SessionPreferences(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext
        .getSharedPreferences("bcv_session", Context.MODE_PRIVATE)

    var token: String?
        get() = preferences.getString("token", null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("token") else putString("token", value)
            }.apply()
            if (value == null) DeviceStepTracker.stop(appContext)
        }
}
