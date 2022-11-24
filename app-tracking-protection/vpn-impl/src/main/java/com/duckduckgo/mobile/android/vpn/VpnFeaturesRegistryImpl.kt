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
import com.duckduckgo.mobile.android.vpn.service.VpnServicePreStartupManager
import java.util.UUID
import kotlinx.coroutines.flow.*
import timber.log.Timber

private const val PREFS_FILENAME = "com.duckduckgo.mobile.android.vpn.feature.registry.v1"
private const val IS_INITIALIZED = "IS_INITIALIZED"

internal class VpnFeaturesRegistryImpl(
    private val vpnServiceWrapper: VpnServiceWrapper,
    private val sharedPreferencesProvider: VpnSharedPreferencesProvider,
    private val vpnServicePreStartupManager: VpnServicePreStartupManager,
) : VpnFeaturesRegistry, SharedPreferences.OnSharedPreferenceChangeListener {

    private val preferences: SharedPreferences
        get() = sharedPreferencesProvider.getSharedPreferences(PREFS_FILENAME, multiprocess = true, migrate = false)

    private val registryInitialValue = Pair("", false)
    private val _registry = MutableStateFlow(registryInitialValue)

    private var isInitialized: Boolean
        get() = preferences.getBoolean(IS_INITIALIZED, false)
        set(value) = preferences.edit(commit = true) { putBoolean(IS_INITIALIZED, value) }

    init {
        // we don't need to unregister the listener
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    @Synchronized
    override fun registerFeature(feature: VpnFeature) {
        if (!isInitialized) {
            Timber.d("Initializing VpnFeaturesRegistry")
            isInitialized = true
        }

        if (!vpnServiceWrapper.isServiceRunning()) {
            vpnServicePreStartupManager.initiatePreStartup(feature) {
                Timber.d("no features registered, start the service")
                // there's not a registered feature, so we need to start the VPN too
                vpnServiceWrapper.startService()
            }
        }
        Timber.d("(re)registering feature")
        preferences.edit(commit = true) {
            // we use random UUID to force change listener to be called
            putString(feature.featureName, UUID.randomUUID().toString())
        }
    }

    @Synchronized
    override fun unregisterFeature(feature: VpnFeature) {
        if (!isInitialized) {
            Timber.d("Initializing VpnFeaturesRegistry, auto-registering AppTP")
            isInitialized = true
            vpnServiceWrapper.stopService()
            return
        }

        if (registeredFeatures().size == 1 && preferences.contains(feature.featureName)) {
            Timber.d("$feature is the last registered feature, stopping VPN")
            // this is the last feature registered for VPN usage, stop the service
            vpnServiceWrapper.stopService()
        }

        Timber.d("$feature removal")
        preferences.edit(commit = true) {
            remove(feature.featureName)
        }
    }

    override fun isFeatureRegistered(feature: VpnFeature): Boolean {
        val isRegistered = registeredFeatures().contains(feature.featureName) ||
            (!isInitialized && vpnServiceWrapper.isServiceRunning())

        if (isRegistered && !isInitialized) {
            Timber.v("isFeatureRegistered($feature) - should be registered as we're not initialised ")
            preferences.edit(commit = true) {
                // we use random UUID to force change listener to be called
                putString(feature.featureName, UUID.randomUUID().toString())
            }
            isInitialized = true
        }

        Timber.v("isFeatureRegistered($feature) = $isRegistered")
        return isRegistered && vpnServiceWrapper.isServiceRunning()
    }

    override suspend fun refreshFeature(feature: VpnFeature) {
        if (!isInitialized) {
            Timber.d("Initializing VpnFeaturesRegistry, attempting to restart VPN service")
            isInitialized = true

            vpnServiceWrapper.restartVpnService()
        } else if (!preferences.all.contains(feature.featureName)) {
            Timber.e("The $feature is not registered for VPN usage")
        } else {
            vpnServiceWrapper.restartVpnService()
        }
    }

    override fun registryChanges(): Flow<Pair<String, Boolean>> {
        return _registry.asStateFlow()
            .filter {
                Timber.v("change $it")
                it != registryInitialValue && it.first != IS_INITIALIZED
            }
    }

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences,
        key: String,
    ) {
        Timber.d("onSharedPreferenceChanged($key)")
        _registry.update { Pair(key, sharedPreferences.contains(key)) }
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
internal open class VpnServiceWrapper(
    private val context: Context,
) {
    open suspend fun restartVpnService() {
        TrackerBlockingVpnService.restartVpnService(context)
    }

    open fun stopService() {
        TrackerBlockingVpnService.stopService(context)
    }

    open fun startService() {
        Timber.d("KL starting")
        TrackerBlockingVpnService.startService(context)
    }

    open fun isServiceRunning(): Boolean {
        return TrackerBlockingVpnService.isServiceRunning(context)
    }
}
