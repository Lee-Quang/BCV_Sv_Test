package com.project.bcv.feature.account.healthconnect.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import com.project.bcv.feature.account.healthconnect.domain.HealthConnectAvailability
import com.project.bcv.feature.account.healthconnect.domain.HealthConnectRepository

class HealthConnectRepositoryImpl(context: Context) : HealthConnectRepository {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences("health_connect_permission", Context.MODE_PRIVATE)
    private val readStepsPermission = HealthPermission.getReadPermission(StepsRecord::class)

    override fun availability() = when (HealthConnectClient.getSdkStatus(appContext)) {
        HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
        HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.INSTALL_OR_UPDATE
        else -> HealthConnectAvailability.UNSUPPORTED
    }

    override suspend fun hasReadStepsPermission(): Boolean =
        HealthConnectClient.getOrCreate(appContext).permissionController
            .getGrantedPermissions().contains(readStepsPermission)

    override fun wasGrantedBefore() = preferences.getBoolean("ever_granted", false)

    override fun markGranted() {
        preferences.edit().putBoolean("ever_granted", true).putInt("denied_requests", 0).apply()
    }

    override fun deniedRequestCount() = preferences.getInt("denied_requests", 0)

    override fun recordDeniedRequest(): Int {
        val count = deniedRequestCount() + 1
        preferences.edit().putInt("denied_requests", count).apply()
        return count
    }
}
