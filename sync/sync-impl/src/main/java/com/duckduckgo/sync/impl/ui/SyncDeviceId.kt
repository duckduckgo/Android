package com.duckduckgo.sync.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import timber.log.Timber
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
        private val syncStore: SyncStore,
) : SyncDeviceIds {
    override fun userId(): String {
        var userId = syncStore.userId
        if (userId != null) return userId

        userId = UUID.randomUUID().toString()
        syncStore.userId = userId

        return userId
    }

    override fun deviceName(): String {
        Timber.i("SYNC: ${Build.BRAND}, ${Build.DEVICE}, ${Build.MODEL}, ${Build.DISPLAY}, ${Build.MANUFACTURER}, ${Build.PRODUCT}")
        var deviceName = syncStore.deviceName
        if (deviceName != null) return deviceName

        deviceName = "${Build.BRAND} ${Build.MODEL}"
        syncStore.deviceName = deviceName
        return deviceName
    }

    @SuppressLint("HardwareIds")
    override fun deviceId(): String {
        var deviceName = syncStore.deviceId
        if (deviceName != null) return deviceName

        deviceName = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
        syncStore.deviceId = deviceName
        return deviceName
    }
}
