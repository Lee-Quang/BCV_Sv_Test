package com.project.bcv.feature.statsnotify.stats.domain

data class Tally(val completed: Int, val total: Int)

/** Confirmed steps, places opened, cards owned and this cycle's quest completion. */
data class ProfileStats(val totalSteps: Long, val discoveredPlaces: Int, val ownedCards: Int, val daily: Tally, val weekly: Tally)

interface StatsRepository {
    fun stats(): ProfileStats
}
