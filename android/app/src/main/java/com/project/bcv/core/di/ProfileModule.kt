package com.project.bcv.core.di

import android.content.Context
import com.project.bcv.BuildConfig
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.data.local.SessionPreferences
import com.project.bcv.feature.inventory.data.ItemUseRepository
import com.project.bcv.feature.profile.data.ProfileRepository

object ProfileModule {
    fun repository(context: Context) = ProfileRepository(
        JsonApiClient(context, BuildConfig.API_BASE_URL), SessionPreferences(context)
    )

    fun items(context: Context) = ItemUseRepository(
        JsonApiClient(context, BuildConfig.API_BASE_URL), SessionPreferences(context)
    )
}
