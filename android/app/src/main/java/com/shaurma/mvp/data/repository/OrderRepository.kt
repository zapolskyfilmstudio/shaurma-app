package com.shaurma.mvp.data.repository

import com.shaurma.mvp.BuildConfig
import com.shaurma.mvp.data.api.CreateOrderItemRequest
import com.shaurma.mvp.data.api.CreateOrderRequest
import com.shaurma.mvp.data.api.OrderDto
import com.shaurma.mvp.data.api.OrderStatus
import com.shaurma.mvp.data.api.ShaurmaApi
import com.shaurma.mvp.data.local.CartDao
import com.shaurma.mvp.data.local.OrderEntity
import com.shaurma.mvp.data.local.OrderItemEntity
import com.shaurma.mvp.data.local.OrdersDao
import com.shaurma.mvp.data.local.toIdList
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class OrderRepository @Inject constructor(
    private val api: ShaurmaApi,
    private val cartDao: CartDao,
    private val ordersDao: OrdersDao,
) {
    val orders: Flow<List<OrderEntity>> = ordersDao.observeOrders()
    val orderItems: Flow<List<OrderItemEntity>> = ordersDao.observeOrderItems()

    suspend fun createOrder(requestedTime: Long, generalComment: String?) {
        val cartItems = cartDao.cartItems()
        require(cartItems.isNotEmpty()) { "Корзина пуста" }

        if (BuildConfig.IS_TEST_MODE) {
            val now = System.currentTimeMillis()
            val publicId = "TEST-${UUID.randomUUID().toString().take(8).uppercase()}"
            ordersDao.upsertOrderGraph(
                orders = listOf(
                    OrderEntity(
                        publicId = publicId,
                        remoteId = now,
                        status = OrderStatus.NEW.name,
                        createdAt = now,
                        updatedAt = now,
                        requestedTime = requestedTime,
                        cookingStartTime = requestedTime,
                        totalPrice = cartItems.sumOf { it.totalPrice },
                        generalComment = generalComment?.takeIf { it.isNotBlank() },
                    )
                ),
                items = cartItems.map {
                    OrderItemEntity(
                        orderPublicId = publicId,
                        remoteItemId = it.id,
                        menuItemId = it.menuItemId,
                        nameSnapshot = it.name,
                        priceSnapshot = it.totalPrice,
                        weightSnapshot = it.weight,
                        additionsSnapshot = it.additionNames,
                        removalsSnapshot = it.removalNames,
                    )
                },
            )
            cartDao.clear()
            return
        }

        val response = api.createOrder(
            CreateOrderRequest(
                requestedTime = requestedTime,
                generalComment = generalComment?.takeIf { it.isNotBlank() },
                items = cartItems.map {
                    CreateOrderItemRequest(
                        menuItemId = it.menuItemId,
                        additionsIds = it.additionIds.toIdList(),
                        removalsIds = it.removalIds.toIdList(),
                    )
                },
            )
        )
        val now = System.currentTimeMillis()
        ordersDao.upsertOrderGraph(
            orders = listOf(
                OrderEntity(
                    publicId = response.publicId,
                    remoteId = 0,
                    status = response.status.name,
                    createdAt = now,
                    updatedAt = response.updatedAt,
                    requestedTime = requestedTime,
                    cookingStartTime = requestedTime,
                    totalPrice = cartItems.sumOf { it.totalPrice },
                    generalComment = generalComment?.takeIf { it.isNotBlank() },
                )
            ),
            items = emptyList(),
        )
        cartDao.clear()
        syncOrders()
    }

    suspend fun syncOrders() {
        if (BuildConfig.IS_TEST_MODE) return

        val sinceUpdatedAt = ordersDao.maxUpdatedAt()
        val sinceId = if (sinceUpdatedAt == 0L) 0L else ordersDao.maxIdForUpdatedAt(sinceUpdatedAt)
        val response = api.myOrders(sinceUpdatedAt = sinceUpdatedAt, sinceId = sinceId)
        val publicIds = response.orders.map { it.publicId }.toSet()
        val deduped = response.orders.distinctBy { it.publicId }.filter { it.publicId in publicIds }
        ordersDao.upsertOrderGraph(
            orders = deduped.map { it.toEntity() },
            items = deduped.flatMap { order ->
                order.items.map {
                    OrderItemEntity(
                        orderPublicId = order.publicId,
                        remoteItemId = it.id,
                        menuItemId = it.menuItemId,
                        nameSnapshot = it.nameSnapshot,
                        priceSnapshot = it.priceSnapshot,
                        weightSnapshot = it.weightSnapshot,
                        additionsSnapshot = it.additionsSnapshot.joinToString { addition ->
                            "${addition.name} +${addition.price} ₽"
                        },
                        removalsSnapshot = it.removalsSnapshot.joinToString { removal -> removal.name },
                    )
                }
            },
        )
    }
}

private fun OrderDto.toEntity(): OrderEntity =
    OrderEntity(
        publicId = publicId,
        remoteId = id,
        status = status.name,
        createdAt = createdAt,
        updatedAt = updatedAt,
        requestedTime = requestedTime,
        cookingStartTime = cookingStartTime,
        totalPrice = totalPrice,
        generalComment = generalComment,
    )
