package com.project.bcv.core.di

import android.content.Context
import com.project.bcv.BuildConfig
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.data.local.SessionPreferences
import com.project.bcv.feature.activitylevel.data.ActivityLevelRepositoryImpl
import com.project.bcv.feature.activitylevel.domain.ActivityLevelRepository

object ActivityLevelModule {
    fun repository(context: Context): ActivityLevelRepository = ActivityLevelRepositoryImpl(
        JsonApiClient(context, BuildConfig.API_BASE_URL), SessionPreferences(context)
    )
}
