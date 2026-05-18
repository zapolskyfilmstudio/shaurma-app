package com.shaurma.mvp.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shaurma.mvp.data.api.InitResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore("device")

data class ClientProfile(
    val deviceId: String,
    val clientNumber: Int?,
    val name: String?,
    val phone: String?,
    val isBlocked: Boolean,
    val serverTimeOffsetMillis: Long,
)

@Singleton
class DevicePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store = context.deviceDataStore

    val profileFlow: Flow<ClientProfile> = store.data.map { preferences ->
        ClientProfile(
            deviceId = preferences[Keys.DEVICE_ID].orEmpty(),
            clientNumber = preferences[Keys.CLIENT_NUMBER],
            name = preferences[Keys.NAME],
            phone = preferences[Keys.PHONE],
            isBlocked = preferences[Keys.IS_BLOCKED] ?: false,
            serverTimeOffsetMillis = preferences[Keys.SERVER_TIME_OFFSET] ?: 0L,
        )
    }

    suspend fun getOrCreateDeviceId(): String {
        val existing = store.data.first()[Keys.DEVICE_ID]
        if (!existing.isNullOrBlank()) return existing

        val created = UUID.randomUUID().toString()
        store.edit { it[Keys.DEVICE_ID] = created }
        return created
    }

    fun getOrCreateDeviceIdBlocking(): String = runBlocking { getOrCreateDeviceId() }

    suspend fun saveInit(response: InitResponse) {
        store.edit { preferences ->
            preferences[Keys.CLIENT_NUMBER] = response.clientNumber
            response.name?.let { preferences[Keys.NAME] = it } ?: preferences.remove(Keys.NAME)
            response.phone?.let { preferences[Keys.PHONE] = it } ?: preferences.remove(Keys.PHONE)
            preferences[Keys.IS_BLOCKED] = response.isBlocked
            preferences[Keys.SERVER_TIME_OFFSET] = response.serverTime - System.currentTimeMillis()
        }
    }

    suspend fun saveServerTime(serverTime: Long) {
        store.edit { preferences ->
            preferences[Keys.SERVER_TIME_OFFSET] = serverTime - System.currentTimeMillis()
        }
    }

    suspend fun currentServerTimeMillis(): Long {
        val offset = store.data.first()[Keys.SERVER_TIME_OFFSET] ?: 0L
        return System.currentTimeMillis() + offset
    }

    private object Keys {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val CLIENT_NUMBER = intPreferencesKey("client_number")
        val NAME = stringPreferencesKey("name")
        val PHONE = stringPreferencesKey("phone")
        val IS_BLOCKED = booleanPreferencesKey("is_blocked")
        val SERVER_TIME_OFFSET = longPreferencesKey("server_time_offset")
    }
}
