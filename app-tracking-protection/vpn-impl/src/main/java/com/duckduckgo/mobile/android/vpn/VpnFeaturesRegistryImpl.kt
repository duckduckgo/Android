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
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import java.util.UUID
import logcat.logcat

private const val PREFS_FILENAME = "com.duckduckgo.mobile.android.vpn.feature.registry.v1"
private const val IS_INITIALIZED = "IS_INITIALIZED"

internal class VpnFeaturesRegistryImpl(
    private val vpnServiceWrapper: VpnServiceWrapper,
    private val sharedPreferencesProvider: VpnSharedPreferencesProvider,
) : VpnFeaturesRegistry {

    private val preferences: SharedPreferences by lazy {
        sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = true, migrate = false)
    }

    @Synchronized
    override fun registerFeature(feature: VpnFeature) {
        logcat { "registerFeature: $feature" }
        preferences.edit(commit = true) {
            // we use random UUID to force change listener to be called
            putString(feature.featureName, UUID.randomUUID().toString())
        }
        vpnServiceWrapper.restartVpnService(forceRestart = true)
    }

    @Synchronized
    override fun unregisterFeature(feature: VpnFeature) {
        if (!preferences.contains(feature.featureName)) return

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

    override fun isFeatureRegistered(feature: VpnFeature): Boolean {
        return registeredFeatures().contains(feature.featureName) && vpnServiceWrapper.isServiceRunning()
    }

    override fun isAnyFeatureRegistered(): Boolean {
        return registeredFeatures().isNotEmpty() && vpnServiceWrapper.isServiceRunning()
    }

    override suspend fun refreshFeature(feature: VpnFeature) {
        vpnServiceWrapper.restartVpnService(forceRestart = false)
    }

    override fun getRegisteredFeatures(): List<VpnFeature> {
        return registeredFeatures().keys.map { VpnFeature { it } }
    }

    private fun registeredFeatures(): Map<String, Any?> {
        return preferences.all.filter { it.key != IS_INITIALIZED }
    }
}

/**
 * This class is here purely to wrap TrackerBlockingVpnService static calls that deal with enable/disable VPN, so that we can
 * unit test the [VpnFeaturesRegistryImpl] class.
 *
 * The class is marked as open to be able to mock it in tests.
 */
internal open class VpnServiceWrapper(private val context: Context) {
    open fun restartVpnService(forceRestart: Boolean) {
        TrackerBlockingVpnService.restartVpnService(context, forceRestart = forceRestart)
    }

    open fun stopService() {
        TrackerBlockingVpnService.stopService(context)
    }

    open fun startService() {
        TrackerBlockingVpnService.startService(context)
    }

    open fun isServiceRunning(): Boolean {
        return TrackerBlockingVpnService.isServiceRunning(context)
    }
}
