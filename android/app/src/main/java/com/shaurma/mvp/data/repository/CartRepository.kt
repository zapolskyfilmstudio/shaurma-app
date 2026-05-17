package com.shaurma.mvp.data.repository

import com.shaurma.mvp.data.local.AdditionEntity
import com.shaurma.mvp.data.local.CartDao
import com.shaurma.mvp.data.local.CartItemEntity
import com.shaurma.mvp.data.local.MenuItemEntity
import com.shaurma.mvp.data.local.RemovalEntity
import com.shaurma.mvp.data.local.toIdCsv
import com.shaurma.mvp.data.local.toIdList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class CartRepository @Inject constructor(
    private val cartDao: CartDao,
) {
    val cart: Flow<List<CartItemEntity>> = cartDao.observeCart()

    suspend fun addItem(
        item: MenuItemEntity,
        additions: List<AdditionEntity>,
        removals: List<RemovalEntity>,
    ) {
        cartDao.insert(
            CartItemEntity(
                menuItemId = item.id,
                name = item.name,
                basePrice = item.price,
                weight = item.weight + additions.sumOf { it.weight },
                cookingTime = item.cookingTime,
                additionIds = additions.map { it.id }.toIdCsv(),
                removalIds = removals.map { it.id }.toIdCsv(),
                additionNames = additions.joinToString { it.name },
                removalNames = removals.joinToString { it.name },
                additionsPrice = additions.sumOf { it.price },
            )
        )
    }

    suspend fun delete(id: Long) = cartDao.delete(id)

    suspend fun clear() = cartDao.clear()

    suspend fun currentItems(): List<CartItemEntity> = cartDao.cartItems()

    suspend fun removeInvalidEntries(
        activeMenuItemIds: Set<Int>,
        activeAdditionIds: Set<Int>,
        activeRemovalIds: Set<Int>,
    ) {
        cartDao.cartItems()
            .filter { item ->
                item.menuItemId !in activeMenuItemIds ||
                    item.additionIds.toIdList().any { it !in activeAdditionIds } ||
                    item.removalIds.toIdList().any { it !in activeRemovalIds }
            }
            .forEach { cartDao.delete(it.id) }
    }
}

val CartItemEntity.totalPrice: Int
    get() = basePrice + additionsPrice
