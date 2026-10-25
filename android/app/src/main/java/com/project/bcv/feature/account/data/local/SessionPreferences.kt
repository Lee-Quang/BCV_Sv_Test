package com.project.bcv.feature.account.data.local

import android.content.Context

class SessionPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("bcv_session", Context.MODE_PRIVATE)

    var token: String?
        get() = preferences.getString("token", null)
        set(value) {
            preferences.edit().apply {
                if (value == null) remove("token") else putString("token", value)
            }.apply()
        }
}
