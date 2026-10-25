package com.project.bcv.feature.activitylevel.domain

enum class ActivityLevel { LOW, MEDIUM, HIGH }

interface ActivityLevelRepository {
    fun load(): ActivityLevel?
    fun save(level: ActivityLevel)
}
