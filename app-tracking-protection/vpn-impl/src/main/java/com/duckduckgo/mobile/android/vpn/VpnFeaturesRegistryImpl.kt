/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import logcat.logcat
import java.util.UUID
import javax.inject.Inject

private const val PREFS_FILENAME = "com.duckduckgo.mobile.android.vpn.feature.registry.v1"
private const val IS_INITIALIZED = "IS_INITIALIZED"

@ContributesBinding(
    scope = AppScope::class,
    boundType = VpnFeaturesRegistry::class,
)
@ContributesBinding(
    scope = AppScope::class,
    boundType = Vpn::class,
)
@SingleInstanceIn(AppScope::class)
class VpnFeaturesRegistryImpl @Inject constructor(
    private val vpnServiceWrapper: VpnServiceWrapper,
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val dispatcherProvider: DispatcherProvider,
) : VpnFeaturesRegistry, Vpn {

    private val mutex = Mutex()

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = true, migrate = false)
    }

    override suspend fun registerFeature(feature: VpnFeature) = withContext(dispatcherProvider.io()) {
        mutex.lock()
        try {
            logcat { "registerFeature: $feature" }
            preferences.edit(commit = true) {
                // we use random UUID to force change listener to be called
                putString(feature.featureName, UUID.randomUUID().toString())
            }
            vpnServiceWrapper.restartVpnService(forceRestart = true)
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun unregisterFeature(feature: VpnFeature) = withContext(dispatcherProvider.io()) {
        mutex.lock()
        try {
            if (preferences.contains(feature.featureName)) {
                preferences.edit(commit = true) {
                    remove(feature.featureName)
                }

                logcat { "unregisterFeature: $feature" }
                if (registeredFeatures().isNotEmpty()) {
                    vpnServiceWrapper.restartVpnService(forceRestart = true)
                } else {
                    vpnServiceWrapper.stopService()
                }
            }
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun isFeatureRunning(feature: VpnFeature): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext isFeatureRegistered(feature) && vpnServiceWrapper.isServiceRunning()
    }

    override suspend fun isFeatureRegistered(feature: VpnFeature): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext registeredFeatures().contains(feature.featureName)
    }

    override suspend fun isAnyFeatureRunning(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext isAnyFeatureRegistered() && vpnServiceWrapper.isServiceRunning()
    }

    override suspend fun isAnyFeatureRegistered(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext registeredFeatures().isNotEmpty()
    }

    override suspend fun refreshFeature(feature: VpnFeature) = withContext(dispatcherProvider.io()) {
        vpnServiceWrapper.restartVpnService(forceRestart = false)
    }

    override suspend fun getRegisteredFeatures(): List<VpnFeature> = withContext(dispatcherProvider.io()) {
        return@withContext registeredFeatures().keys.map { VpnFeature { it } }
    }

    private fun registeredFeatures(): Map<String, Any?> {
        return preferences.all.filter { it.key != IS_INITIALIZED }
    }

    override suspend fun start() = withContext(dispatcherProvider.io()) {
        vpnServiceWrapper.startService()
    }

    override suspend fun pause() {
        vpnServiceWrapper.stopService()
    }

    override suspend fun stop() {
        try {
            mutex.lock()
            // unregister all features
            getRegisteredFeatures().onEach {
                preferences.edit(commit = true) {
                    remove(it.featureName)
                }
            }
            // stop VPN
            vpnServiceWrapper.stopService()
        } finally {
            mutex.unlock()
        }
    }

    override suspend fun snooze(triggerAtMillis: Long) {
        vpnServiceWrapper.snoozeService(triggerAtMillis)
    }
}

/**
 * This class is here purely to wrap TrackerBlockingVpnService static calls that deal with enable/disable VPN, so that we can
 * unit test the [VpnFeaturesRegistryImpl] class.
 *
 * The class is marked as open to be able to mock it in tests.
 */
open class VpnServiceWrapper @Inject constructor(
    private val context: Context,
) {
    open fun restartVpnService(forceRestart: Boolean) {
        TrackerBlockingVpnService.restartVpnService(context, forceRestart = forceRestart)
    }

    open fun stopService() {
        TrackerBlockingVpnService.stopService(context)
    }

    open fun startService() {
        TrackerBlockingVpnService.startService(context)
    }

    open fun snoozeService(triggerAtMillis: Long) {
        TrackerBlockingVpnService.snoozeService(context, triggerAtMillis)
    }

    open fun isServiceRunning(): Boolean {
        return TrackerBlockingVpnService.isServiceRunning(context)
    }
}
