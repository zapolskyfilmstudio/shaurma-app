package com.shaurma.mvp.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "menu_categories")
data class MenuCategoryEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val sortOrder: Int,
    val isActive: Boolean,
    val isGrill: Boolean,
)

@Entity(tableName = "menu_items")
data class MenuItemEntity(
    @PrimaryKey val id: Int,
    val categoryId: Int,
    val name: String,
    val description: String?,
    val price: Int,
    val weight: Int,
    val cookingTime: Int,
    val imageUrl: String?,
    val sortOrder: Int,
    val isActive: Boolean,
)

@Entity(tableName = "menu_additions")
data class AdditionEntity(
    @PrimaryKey val id: Int,
    val menuItemId: Int,
    val name: String,
    val price: Int,
    val weight: Int,
    val isActive: Boolean,
)

@Entity(tableName = "menu_removals")
data class RemovalEntity(
    @PrimaryKey val id: Int,
    val menuItemId: Int,
    val name: String,
    val isActive: Boolean,
)

@Entity(tableName = "cart_items")
data class CartItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val menuItemId: Int,
    val name: String,
    val basePrice: Int,
    val weight: Int,
    val cookingTime: Int,
    val additionIds: String,
    val removalIds: String,
    val additionNames: String,
    val removalNames: String,
    val additionsPrice: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "orders")
data class OrderEntity(
    @PrimaryKey val publicId: String,
    val remoteId: Long,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val requestedTime: Long,
    val cookingStartTime: Long,
    val totalPrice: Int,
    val generalComment: String?,
)

@Entity(tableName = "order_items", primaryKeys = ["orderPublicId", "remoteItemId"])
data class OrderItemEntity(
    val orderPublicId: String,
    val remoteItemId: Long,
    val menuItemId: Int,
    val nameSnapshot: String,
    val priceSnapshot: Int,
    val weightSnapshot: Int,
    val additionsSnapshot: String,
    val removalsSnapshot: String,
)

fun List<Int>.toIdCsv(): String = joinToString(",")

fun String.toIdList(): List<Int> =
    split(",")
        .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toIntOrNull() }
