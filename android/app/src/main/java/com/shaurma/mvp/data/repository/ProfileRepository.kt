package com.shaurma.mvp.data.repository

import com.shaurma.mvp.BuildConfig
import com.shaurma.mvp.data.api.InitRequest
import com.shaurma.mvp.data.api.InitResponse
import com.shaurma.mvp.data.api.ProfileRequest
import com.shaurma.mvp.data.api.ShaurmaApi
import com.shaurma.mvp.data.local.ClientProfile
import com.shaurma.mvp.data.local.DevicePreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class ProfileRepository @Inject constructor(
    private val api: ShaurmaApi,
    private val devicePreferences: DevicePreferences,
) {
    val profile: Flow<ClientProfile> = devicePreferences.profileFlow

    suspend fun initDevice() {
        val deviceId = devicePreferences.getOrCreateDeviceId()
        val response = if (BuildConfig.IS_TEST_MODE) {
            InitResponse(
                clientNumber = 777,
                name = profile.first().name,
                phone = profile.first().phone,
                isBlocked = false,
                serverTime = System.currentTimeMillis(),
            )
        } else {
            api.init(InitRequest(deviceId = deviceId))
        }
        devicePreferences.saveInit(response)
    }

    suspend fun updateProfile(name: String?, phone: String?) {
        val response = if (BuildConfig.IS_TEST_MODE) {
            InitResponse(
                clientNumber = profile.first().clientNumber ?: 777,
                name = name?.takeIf { it.isNotBlank() },
                phone = phone?.takeIf { it.isNotBlank() },
                isBlocked = false,
                serverTime = System.currentTimeMillis(),
            )
        } else {
            api.updateProfile(
                ProfileRequest(
                    name = name?.takeIf { it.isNotBlank() },
                    phone = phone?.takeIf { it.isNotBlank() },
                )
            )
        }
        devicePreferences.saveInit(response)
    }
}
