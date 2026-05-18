package com.shaurma.mvp.data.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface ShaurmaApi {
    @POST("/api/init")
    suspend fun init(@Body request: InitRequest): InitResponse

    @GET("/api/menu")
    suspend fun menu(): MenuResponse

    @POST("/api/profile")
    suspend fun updateProfile(@Body request: ProfileRequest): InitResponse

    @POST("/api/order")
    suspend fun createOrder(@Body request: CreateOrderRequest): CreateOrderResponse

    @GET("/api/orders/my")
    suspend fun myOrders(
        @Query("since_updated_at") sinceUpdatedAt: Long,
        @Query("since_id") sinceId: Long,
    ): OrdersResponse
}
