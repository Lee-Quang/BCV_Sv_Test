package com.project.bcv.core.di

import android.content.Context
import com.project.bcv.BuildConfig
import com.project.bcv.feature.account.data.AccountRepositoryImpl
import com.project.bcv.feature.account.data.local.SessionPreferences
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.domain.AccountRepository

/** Manual composition root for the Account feature. */
object AccountModule {
    fun repository(context: Context): AccountRepository = AccountRepositoryImpl(
        JsonApiClient(context, BuildConfig.API_BASE_URL),
        SessionPreferences(context)
    )
}
