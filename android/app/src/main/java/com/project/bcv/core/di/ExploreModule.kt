package com.project.bcv.core.di

import android.content.Context
import com.project.bcv.BuildConfig
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.data.local.SessionPreferences
import com.project.bcv.feature.explore.data.ExploreRepositoryImpl
import com.project.bcv.feature.explore.data.HealthConnectStepSource

object ExploreModule {
    fun repository(context: Context) = ExploreRepositoryImpl(
        JsonApiClient(context, BuildConfig.API_BASE_URL), SessionPreferences(context)
    )

    fun stepSource(context: Context) = HealthConnectStepSource(context)

    fun addressLookup() = StartingPointModule.addressLookup()
}
