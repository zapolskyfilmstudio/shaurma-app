package com.shaurma.mvp.data.api

import com.google.gson.annotations.SerializedName

data class InitRequest(
    @SerializedName("device_id") val deviceId: String,
    val platform: String = "android",
)

data class InitResponse(
    @SerializedName("client_number") val clientNumber: Int,
    val name: String?,
    val phone: String?,
    @SerializedName("is_blocked") val isBlocked: Boolean,
    @SerializedName("server_time") val serverTime: Long,
)

data class MenuResponse(
    @SerializedName("server_time") val serverTime: Long,
    val categories: List<CategoryDto>,
)

data class CategoryDto(
    val id: Int,
    val name: String,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("is_active") val isActive: Boolean,
    @SerializedName("is_grill") val isGrill: Boolean,
    val items: List<MenuItemDto>,
)

data class MenuItemDto(
    val id: Int,
    @SerializedName("category_id") val categoryId: Int,
    val name: String,
    val description: String?,
    val price: Int,
    val weight: Int,
    @SerializedName("cooking_time") val cookingTime: Int,
    @SerializedName("image_url") val imageUrl: String?,
    @SerializedName("sort_order") val sortOrder: Int,
    @SerializedName("is_active") val isActive: Boolean,
    val additions: List<AdditionDto>,
    val removals: List<RemovalDto>,
)

data class AdditionDto(
    val id: Int,
    @SerializedName("menu_item_id") val menuItemId: Int,
    val name: String,
    val price: Int,
    val weight: Int,
    @SerializedName("is_active") val isActive: Boolean,
)

data class RemovalDto(
    val id: Int,
    @SerializedName("menu_item_id") val menuItemId: Int,
    val name: String,
    @SerializedName("is_active") val isActive: Boolean,
)

data class ProfileRequest(
    val name: String?,
    val phone: String?,
)

data class CreateOrderRequest(
    @SerializedName("requested_time") val requestedTime: Long,
    @SerializedName("general_comment") val generalComment: String?,
    val items: List<CreateOrderItemRequest>,
)

data class CreateOrderItemRequest(
    @SerializedName("menu_item_id") val menuItemId: Int,
    @SerializedName("additions_ids") val additionsIds: List<Int> = emptyList(),
    @SerializedName("removals_ids") val removalsIds: List<Int> = emptyList(),
)

data class CreateOrderResponse(
    @SerializedName("public_id") val publicId: String,
    val status: OrderStatus,
    @SerializedName("updated_at") val updatedAt: Long,
)

data class OrdersResponse(
    val orders: List<OrderDto>,
)

data class OrderDto(
    val id: Long,
    @SerializedName("public_id") val publicId: String,
    val status: OrderStatus,
    @SerializedName("created_at") val createdAt: Long,
    @SerializedName("updated_at") val updatedAt: Long,
    @SerializedName("requested_time") val requestedTime: Long,
    @SerializedName("cooking_start_time") val cookingStartTime: Long,
    @SerializedName("total_price") val totalPrice: Int,
    @SerializedName("general_comment") val generalComment: String?,
    val items: List<OrderItemDto>,
)

data class OrderItemDto(
    val id: Long,
    @SerializedName("menu_item_id") val menuItemId: Int,
    @SerializedName("name_snapshot") val nameSnapshot: String,
    @SerializedName("price_snapshot") val priceSnapshot: Int,
    @SerializedName("weight_snapshot") val weightSnapshot: Int,
    @SerializedName("additions_snapshot") val additionsSnapshot: List<AdditionSnapshot>,
    @SerializedName("removals_snapshot") val removalsSnapshot: List<RemovalSnapshot>,
)

data class AdditionSnapshot(
    val id: Int,
    val name: String,
    val price: Int,
    val weight: Int,
)

data class RemovalSnapshot(
    val id: Int,
    val name: String,
)

enum class OrderStatus {
    NEW,
    CONFIRMED,
    COOKING,
    READY,
    COMPLETED,
}
