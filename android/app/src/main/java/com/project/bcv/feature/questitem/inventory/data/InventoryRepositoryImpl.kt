package com.project.bcv.feature.questitem.inventory.data

import com.project.bcv.core.network.ApiFailure
import com.project.bcv.core.network.JsonApiClient
import com.project.bcv.feature.account.auth.data.local.SessionPreferences
import com.project.bcv.feature.questitem.inventory.domain.InventoryItem
import com.project.bcv.feature.questitem.inventory.domain.InventoryOverview
import com.project.bcv.feature.questitem.inventory.domain.InventoryRepository

class InventoryRepositoryImpl(
    private val remote: JsonApiClient,
    private val session: SessionPreferences
) : InventoryRepository {
    override fun load(): InventoryOverview {
        val token = session.token
            ?: throw ApiFailure("UNAUTHORIZED", "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.")
        val json = remote.get("api/inventory", token)
        val array = json.optJSONArray("items")
        val items = (0 until (array?.length() ?: 0)).map { index ->
            array!!.getJSONObject(index).let { item ->
                InventoryItem(
                    item.getLong("itemId"), item.getString("name"), item.getString("itemType"),
                    item.optString("description").takeIf { it.isNotBlank() && it != "null" },
                    if (item.isNull("effectValue")) null else item.getDouble("effectValue"),
                    if (item.isNull("effectiveRange")) null else item.getInt("effectiveRange"),
                    if (item.isNull("usageLimit")) null else item.getInt("usageLimit"),
                    item.getBoolean("active"), item.getInt("quantity")
                )
            }
        }
        return InventoryOverview(json.getInt("totalQuantity"), json.getBoolean("hasActiveJourney"),
            json.optBoolean("boostActive"), items)
    }
}
