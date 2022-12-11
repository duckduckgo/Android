package com.duckduckgo.sync.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.store.SyncEncryptedStore
import com.squareup.anvil.annotations.ContributesBinding
import java.util.*
import javax.inject.Inject

interface SyncDeviceIds {
    fun userId(): String
    fun deviceName(): String
    fun deviceId(): String
}

@ContributesBinding(ActivityScope::class)
class AppSyncDeviceIds @Inject constructor(
    val context: Context,
    private val syncEncryptedStore: SyncEncryptedStore,
) : SyncDeviceIds {
    override fun userId(): String {
        var userId = syncEncryptedStore.userId
        if (userId != null) return userId

        userId = UUID.randomUUID().toString()
        syncEncryptedStore.userId = userId

        return userId
    }

    override fun deviceName(): String {
        var deviceName = syncEncryptedStore.deviceName
        if (deviceName != null) return deviceName

        deviceName = "${Build.BRAND} ${Build.MODEL}"
        syncEncryptedStore.deviceName = deviceName
        return deviceName
    }

    @SuppressLint("HardwareIds")
    override fun deviceId(): String {
        var deviceName = syncEncryptedStore.deviceId
        if (deviceName != null) return deviceName

        deviceName = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        syncEncryptedStore.deviceId = deviceName
        return deviceName
    }
}
