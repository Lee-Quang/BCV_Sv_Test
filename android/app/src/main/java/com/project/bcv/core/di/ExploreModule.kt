package com.project.bcv.core.di

import android.content.Context
import com.project.bcv.BuildConfig
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.auth.data.local.SessionPreferences
import com.project.bcv.feature.discovery.data.ExploreRepositoryImpl
import com.project.bcv.feature.discovery.data.HealthConnectStepSource
import com.project.bcv.feature.discovery.data.SensorStepSource
import com.project.bcv.feature.discovery.domain.StepSource
import com.project.bcv.core.steps.DeviceStepTracker

object ExploreModule {
    fun repository(context: Context) = ExploreRepositoryImpl(
        JsonApiClient(context, BuildConfig.API_BASE_URL), SessionPreferences(context)
    )

    fun stepSource(context: Context): StepSource = if (DeviceStepTracker.isSupported(context))
        SensorStepSource(context) else HealthConnectStepSource(context)

    fun addressLookup() = StartingPointModule.addressLookup()
}
