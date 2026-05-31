package com.shaurma.mvp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MenuDao {
    @Query("SELECT * FROM menu_categories WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeCategories(): Flow<List<MenuCategoryEntity>>

    @Query("SELECT * FROM menu_items WHERE isActive = 1 ORDER BY sortOrder, id")
    fun observeItems(): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE categoryId = :categoryId AND isActive = 1 ORDER BY sortOrder, id")
    fun observeItemsForCategory(categoryId: Int): Flow<List<MenuItemEntity>>

    @Query("SELECT * FROM menu_items WHERE id = :itemId LIMIT 1")
    fun observeItem(itemId: Int): Flow<MenuItemEntity?>

    @Query("SELECT * FROM menu_additions WHERE menuItemId = :itemId AND isActive = 1 ORDER BY id")
    fun observeAdditions(itemId: Int): Flow<List<AdditionEntity>>

    @Query("SELECT * FROM menu_removals WHERE menuItemId = :itemId AND isActive = 1 ORDER BY id")
    fun observeRemovals(itemId: Int): Flow<List<RemovalEntity>>

    @Query("SELECT id FROM menu_items WHERE isActive = 1")
    suspend fun activeMenuItemIds(): List<Int>

    @Query("SELECT id FROM menu_additions WHERE isActive = 1")
    suspend fun activeAdditionIds(): List<Int>

    @Query("SELECT id FROM menu_removals WHERE isActive = 1")
    suspend fun activeRemovalIds(): List<Int>

    @Query("DELETE FROM menu_categories")
    suspend fun clearCategories()

    @Query("DELETE FROM menu_items")
    suspend fun clearItems()

    @Query("DELETE FROM menu_additions")
    suspend fun clearAdditions()

    @Query("DELETE FROM menu_removals")
    suspend fun clearRemovals()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<MenuCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<MenuItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdditions(additions: List<AdditionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRemovals(removals: List<RemovalEntity>)

    @Transaction
    suspend fun replaceMenu(
        categories: List<MenuCategoryEntity>,
        items: List<MenuItemEntity>,
        additions: List<AdditionEntity>,
        removals: List<RemovalEntity>,
    ) {
        clearRemovals()
        clearAdditions()
        clearItems()
        clearCategories()
        insertCategories(categories)
        insertItems(items)
        insertAdditions(additions)
        insertRemovals(removals)
    }
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items ORDER BY createdAt, id")
    fun observeCart(): Flow<List<CartItemEntity>>

    @Query("SELECT * FROM cart_items ORDER BY createdAt, id")
    suspend fun cartItems(): List<CartItemEntity>

    @Insert
    suspend fun insert(item: CartItemEntity)

    @Query("DELETE FROM cart_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM cart_items")
    suspend fun clear()
}

@Dao
interface OrdersDao {
    @Query("SELECT * FROM orders ORDER BY createdAt DESC, remoteId DESC")
    fun observeOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM order_items ORDER BY orderPublicId, remoteItemId")
    fun observeOrderItems(): Flow<List<OrderItemEntity>>

    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM orders")
    suspend fun maxUpdatedAt(): Long

    @Query("SELECT COALESCE(MAX(remoteId), 0) FROM orders WHERE updatedAt = :updatedAt")
    suspend fun maxIdForUpdatedAt(updatedAt: Long): Long

    @Upsert
    suspend fun upsertOrders(orders: List<OrderEntity>)

    @Query("DELETE FROM order_items WHERE orderPublicId IN (:publicIds)")
    suspend fun deleteItemsFor(publicIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<OrderItemEntity>)

    @Transaction
    suspend fun upsertOrderGraph(orders: List<OrderEntity>, items: List<OrderItemEntity>) {
        if (orders.isEmpty()) return
        upsertOrders(orders)
        deleteItemsFor(orders.map { it.publicId })
        insertItems(items)
    }
}
