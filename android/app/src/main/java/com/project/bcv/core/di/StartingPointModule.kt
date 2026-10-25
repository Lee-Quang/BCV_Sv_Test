package com.project.bcv.core.di

import android.content.Context
import com.project.bcv.BuildConfig
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.data.local.SessionPreferences
import com.project.bcv.feature.startingpoint.data.AndroidDeviceLocationSource
import com.project.bcv.feature.startingpoint.data.GoongAddressLookup
import com.project.bcv.feature.startingpoint.data.StartingPointRepositoryImpl
import com.project.bcv.feature.startingpoint.domain.StartingPointRepository

object StartingPointModule {
    fun repository(context: Context): StartingPointRepository = StartingPointRepositoryImpl(
        JsonApiClient(context, BuildConfig.API_BASE_URL), SessionPreferences(context)
    )

    fun locationSource(context: Context) = AndroidDeviceLocationSource(context)

    fun addressLookup() = GoongAddressLookup(BuildConfig.GOONG_API_KEY)
}
