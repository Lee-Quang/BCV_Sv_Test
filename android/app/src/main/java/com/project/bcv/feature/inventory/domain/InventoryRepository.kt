package com.project.bcv.feature.inventory.domain

/** An item type and how many the user holds (0 when not owned). */
data class InventoryItem(
    val id: Long,
    val name: String,
    val type: String,
    val description: String?,
    val effectValue: Double?,
    val effectiveRange: Int?,
    val usageLimit: Int?,
    val active: Boolean,
    val quantity: Int
)

data class InventoryOverview(
    val totalQuantity: Int,
    val hasActiveJourney: Boolean,
    /** A speed-shoe boost is still running on the current journey, so another cannot start. */
    val boostActive: Boolean,
    val items: List<InventoryItem>
)

interface InventoryRepository {
    fun load(): InventoryOverview
}
