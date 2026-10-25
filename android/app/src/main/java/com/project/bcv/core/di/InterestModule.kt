package com.project.bcv.core.di

import android.content.Context
import com.project.bcv.BuildConfig
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.data.local.SessionPreferences
import com.project.bcv.feature.interests.data.InterestRepositoryImpl
import com.project.bcv.feature.interests.domain.InterestRepository

object InterestModule {
    fun repository(context: Context): InterestRepository = InterestRepositoryImpl(
        JsonApiClient(context, BuildConfig.API_BASE_URL), SessionPreferences(context)
    )
}
