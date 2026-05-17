package com.shaurma.mvp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        MenuCategoryEntity::class,
        MenuItemEntity::class,
        AdditionEntity::class,
        RemovalEntity::class,
        CartItemEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun menuDao(): MenuDao
    abstract fun cartDao(): CartDao
    abstract fun ordersDao(): OrdersDao
}
