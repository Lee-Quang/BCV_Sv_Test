package com.project.bcv.feature.explore.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.project.bcv.feature.explore.domain.StepSource
import java.time.Instant

class HealthConnectStepSource(context: Context) : StepSource {
    private val appContext = context.applicationContext
    private val permission = HealthPermission.getReadPermission(StepsRecord::class)

    override suspend fun hasPermission(): Boolean =
        HealthConnectClient.getSdkStatus(appContext) == HealthConnectClient.SDK_AVAILABLE &&
            HealthConnectClient.getOrCreate(appContext).permissionController
                .getGrantedPermissions().contains(permission)

    override suspend fun totalStepsSince(startedAtEpochSeconds: Long): Long {
        val now = Instant.now()
        val startedAt = Instant.ofEpochSecond(startedAtEpochSeconds)
        if (!startedAt.isBefore(now)) return 0
        val result = HealthConnectClient.getOrCreate(appContext).aggregate(
            AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), TimeRangeFilter.between(startedAt, now))
        )
        return result[StepsRecord.COUNT_TOTAL] ?: 0L
    }
}
