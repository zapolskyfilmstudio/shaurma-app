package com.shaurma.mvp.data.repository

import android.content.Context
import com.google.gson.Gson
import com.shaurma.mvp.data.api.MenuResponse
import com.shaurma.mvp.data.api.ShaurmaApi
import com.shaurma.mvp.data.local.AdditionEntity
import com.shaurma.mvp.data.local.DevicePreferences
import com.shaurma.mvp.data.local.MenuCategoryEntity
import com.shaurma.mvp.data.local.MenuDao
import com.shaurma.mvp.data.local.MenuItemEntity
import com.shaurma.mvp.data.local.RemovalEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface MenuRepository {
    val categories: Flow<List<MenuCategoryEntity>>
    val allItems: Flow<List<MenuItemEntity>>
    fun itemsForCategory(categoryId: Int): Flow<List<MenuItemEntity>>
    fun item(itemId: Int): Flow<MenuItemEntity?>
    fun additions(itemId: Int): Flow<List<AdditionEntity>>
    fun removals(itemId: Int): Flow<List<RemovalEntity>>
    suspend fun refresh()
}

class ServerMenuRepository @Inject constructor(
    private val api: ShaurmaApi,
    private val menuDao: MenuDao,
    private val cartRepository: CartRepository,
    private val devicePreferences: DevicePreferences,
) : MenuRepository {
    override val categories: Flow<List<MenuCategoryEntity>> = menuDao.observeCategories()
    override val allItems: Flow<List<MenuItemEntity>> = menuDao.observeItems()

    override fun itemsForCategory(categoryId: Int): Flow<List<MenuItemEntity>> =
        menuDao.observeItemsForCategory(categoryId)

    override fun item(itemId: Int): Flow<MenuItemEntity?> = menuDao.observeItem(itemId)

    override fun additions(itemId: Int): Flow<List<AdditionEntity>> = menuDao.observeAdditions(itemId)

    override fun removals(itemId: Int): Flow<List<RemovalEntity>> = menuDao.observeRemovals(itemId)

    override suspend fun refresh() {
        val response = api.menu()
        devicePreferences.saveServerTime(response.serverTime)
        cacheMenuAndValidate(response, menuDao, cartRepository)
    }
}

class AssetMenuRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson,
    private val menuDao: MenuDao,
    private val cartRepository: CartRepository,
    private val devicePreferences: DevicePreferences,
) : MenuRepository {
    private val delegate = object : MenuRepository {
        override val categories = menuDao.observeCategories()
        override val allItems = menuDao.observeItems()
        override fun itemsForCategory(categoryId: Int) = menuDao.observeItemsForCategory(categoryId)
        override fun item(itemId: Int) = menuDao.observeItem(itemId)
        override fun additions(itemId: Int) = menuDao.observeAdditions(itemId)
        override fun removals(itemId: Int) = menuDao.observeRemovals(itemId)
        override suspend fun refresh() = Unit
    }

    override val categories: Flow<List<MenuCategoryEntity>> = delegate.categories
    override val allItems: Flow<List<MenuItemEntity>> = delegate.allItems
    override fun itemsForCategory(categoryId: Int): Flow<List<MenuItemEntity>> =
        delegate.itemsForCategory(categoryId)

    override fun item(itemId: Int): Flow<MenuItemEntity?> = delegate.item(itemId)
    override fun additions(itemId: Int): Flow<List<AdditionEntity>> = delegate.additions(itemId)
    override fun removals(itemId: Int): Flow<List<RemovalEntity>> = delegate.removals(itemId)

    override suspend fun refresh() {
        val response = context.assets.open("menu.json").bufferedReader().use {
            gson.fromJson(it, MenuResponse::class.java)
        }
        devicePreferences.saveServerTime(response.serverTime)
        cacheMenuAndValidate(response, menuDao, cartRepository)
    }
}

private suspend fun cacheMenuAndValidate(
    response: MenuResponse,
    menuDao: MenuDao,
    cartRepository: CartRepository,
) {
    menuDao.replaceMenu(
        categories = response.categories.map {
            MenuCategoryEntity(
                id = it.id,
                name = it.name,
                sortOrder = it.sortOrder,
                isActive = it.isActive,
                isGrill = it.isGrill,
            )
        },
        items = response.categories.flatMap { category ->
            category.items.map {
                MenuItemEntity(
                    id = it.id,
                    categoryId = it.categoryId,
                    name = it.name,
                    description = it.description,
                    price = it.price,
                    weight = it.weight,
                    cookingTime = it.cookingTime,
                    imageUrl = it.imageUrl,
                    sortOrder = it.sortOrder,
                    isActive = it.isActive && category.isActive,
                )
            }
        },
        additions = response.categories.flatMap { category ->
            category.items.flatMap { item ->
                item.additions.map {
                    AdditionEntity(
                        id = it.id,
                        menuItemId = it.menuItemId,
                        name = it.name,
                        price = it.price,
                        weight = it.weight,
                        isActive = it.isActive && item.isActive && category.isActive,
                    )
                }
            }
        },
        removals = response.categories.flatMap { category ->
            category.items.flatMap { item ->
                item.removals.map {
                    RemovalEntity(
                        id = it.id,
                        menuItemId = it.menuItemId,
                        name = it.name,
                        isActive = it.isActive && item.isActive && category.isActive,
                    )
                }
            }
        },
    )
    cartRepository.removeInvalidEntries(
        activeMenuItemIds = menuDao.activeMenuItemIds().toSet(),
        activeAdditionIds = menuDao.activeAdditionIds().toSet(),
        activeRemovalIds = menuDao.activeRemovalIds().toSet(),
    )
}
