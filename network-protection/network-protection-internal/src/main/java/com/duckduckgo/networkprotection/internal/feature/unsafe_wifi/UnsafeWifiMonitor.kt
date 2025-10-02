/*
 * Copyright (c) 2024 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.networkprotection.internal.feature.unsafe_wifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.net.wifi.WifiInfo.SECURITY_TYPE_OPEN
import android.net.wifi.WifiInfo.SECURITY_TYPE_WEP
import android.net.wifi.WifiManager
import android.os.Build.VERSION_CODES
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.notification.checkPermissionAndNotify
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.notification.NetPDisabledNotificationBuilder
import com.duckduckgo.networkprotection.impl.notification.NetPDisabledNotificationScheduler
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class UnsafeWifiMonitor @Inject constructor(
    private val context: Context,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val networkProtectionState: NetworkProtectionState,
    private val netPDisabledNotificationBuilder: NetPDisabledNotificationBuilder,
    private val notificationManager: NotificationManagerCompat,
    private val dispatcherProvider: DispatcherProvider,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val appBuildConfig: AppBuildConfig,
) : MainProcessLifecycleObserver {

    private var networkObserver: NetworkObserver? = null
    private val prefs: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = true, migrate = false)
    }
    private val job = ConflatedJob()

    internal fun enable() {
        job += coroutineScope.launch(dispatcherProvider.io()) {
            runCatching {
                stopObserver()
                startObserver()
            }.onSuccess {
                prefs.setEnabled()
            }
        }
    }

    internal fun disable() {
        job.cancel()
        coroutineScope.launch(dispatcherProvider.io()) {
            runCatching { stopObserver() }.onSuccess { prefs.setDisabled() }
        }
    }

    internal suspend fun isEnabled(): Boolean {
        return prefs.isEnabled()
    }

    @SuppressLint("NewApi")
    override fun onCreate(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(dispatcherProvider.io()) {
            if (prefs.isEnabled()) {
                enable()
            }
        }
    }

    private suspend fun startObserver() {
        this.networkObserver = NetworkObserver(context) { isOnline ->
            if (isOnline) {
                logcat { "onConnectivityChange: The device is online." }
                if (!context.isSafeWifi()) {
                    coroutineScope.launch(dispatcherProvider.io()) {
                        if (!networkProtectionState.isRunning()) {
                            logcat { "onConnectivityChange: Unsecure WiFI." }
                            showUnsafeWifiWarningNotification()
                        } else {
                            logcat { "onConnectivityChange: Unsecure WiFI, VPN already enabled." }
                        }
                    }
                } else {
                    dismissUnsafeWifiWarningNotification()
                }
            } else {
                logcat { "onConnectivityChange: The device is offline." }
            }
        }
    }

    private fun stopObserver() {
        networkObserver?.shutdown()
    }

    private fun showUnsafeWifiWarningNotification() {
        notificationManager.checkPermissionAndNotify(
            context,
            NetPDisabledNotificationScheduler.NETP_REMINDER_NOTIFICATION_ID,
            netPDisabledNotificationBuilder.buildUnsafeWifiWithoutVpnNotification(context),
        )
    }

    private fun dismissUnsafeWifiWarningNotification() {
        notificationManager.cancel(NetPDisabledNotificationScheduler.NETP_REMINDER_NOTIFICATION_ID)
    }

    private fun SharedPreferences.isEnabled(): Boolean {
        return getBoolean(KEY_ENABLED, false)
    }

    private fun SharedPreferences.setEnabled() {
        edit { putBoolean(KEY_ENABLED, true) }
    }

    private fun SharedPreferences.setDisabled() {
        edit { putBoolean(KEY_ENABLED, false) }
    }

    @SuppressLint("NewApi") // appBuildConfig not detected by lint
    private fun Context.isSafeWifi(): Boolean {
        return runCatching {
            val wifiService = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            if (appBuildConfig.sdkInt >= VERSION_CODES.S) {
                return when (wifiService?.connectionInfo?.currentSecurityType) {
                    SECURITY_TYPE_OPEN, SECURITY_TYPE_WEP -> false
                    else -> true
                }
            } else {
                return true
            }
        }.getOrNull() ?: true
    }

    companion object {
        private const val PREFS_FILENAME = "com.duckduckgo.vpn.internal.feature.unsafe.wifi.v1"
        private const val KEY_ENABLED = "enabled"
    }
}
