package com.project.bcv.feature.healthconnect.domain

enum class HealthConnectAvailability { AVAILABLE, INSTALL_OR_UPDATE, UNSUPPORTED }

interface HealthConnectRepository {
    fun availability(): HealthConnectAvailability
    suspend fun hasReadStepsPermission(): Boolean
    fun wasGrantedBefore(): Boolean
    fun markGranted()
    fun deniedRequestCount(): Int
    fun recordDeniedRequest(): Int
}
