package com.shaurma.mvp.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.shaurma.mvp.BuildConfig
import com.shaurma.mvp.data.api.ShaurmaApi
import com.shaurma.mvp.data.local.AppDatabase
import com.shaurma.mvp.data.local.CartDao
import com.shaurma.mvp.data.local.DevicePreferences
import com.shaurma.mvp.data.local.MenuDao
import com.shaurma.mvp.data.local.OrdersDao
import com.shaurma.mvp.data.repository.AssetMenuRepository
import com.shaurma.mvp.data.repository.MenuRepository
import com.shaurma.mvp.data.repository.ServerMenuRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "shaurma.db").build()

    @Provides
    fun provideMenuDao(database: AppDatabase): MenuDao = database.menuDao()

    @Provides
    fun provideCartDao(database: AppDatabase): CartDao = database.cartDao()

    @Provides
    fun provideOrdersDao(database: AppDatabase): OrdersDao = database.ordersDao()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideAuthInterceptor(devicePreferences: DevicePreferences): Interceptor =
        Interceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val builder = request.newBuilder()
            if (BuildConfig.BEARER_TOKEN.isNotBlank()) {
                builder.header("Authorization", "Bearer ${BuildConfig.BEARER_TOKEN}")
            }
            if (path == "/api/profile" || path == "/api/order" || path == "/api/orders/my") {
                builder.header("X-Device-Id", devicePreferences.getOrCreateDeviceIdBlocking())
            }
            chain.proceed(builder.build())
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .build()

    @Provides
    @Singleton
    fun provideApi(client: OkHttpClient, gson: Gson): ShaurmaApi {
        val baseUrl = BuildConfig.BASE_URL.let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ShaurmaApi::class.java)
    }

    @Provides
    @Singleton
    fun provideMenuRepository(
        server: ServerMenuRepository,
        asset: AssetMenuRepository,
    ): MenuRepository = if (BuildConfig.IS_TEST_MODE) asset else server
}
