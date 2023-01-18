package com.duckduckgo.sync.impl.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.ActivityScope
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
    val appBuildConfig: AppBuildConfig,
) : SyncDeviceIds {
    override fun userId(): String = UUID.randomUUID().toString()

    override fun deviceName(): String = "${Build.BRAND} ${Build.MODEL}"

    @SuppressLint("HardwareIds")
    override fun deviceId(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN"
    }
}
