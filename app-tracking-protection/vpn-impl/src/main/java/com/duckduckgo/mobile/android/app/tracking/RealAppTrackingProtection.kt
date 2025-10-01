/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.app.tracking

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.AppTpVpnFeature
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealAppTrackingProtection @Inject constructor(
    private val vpnStore: VpnStore,
    private val vpnFeaturesRegistry: VpnFeaturesRegistry,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val trackingProtectionAppsRepository: TrackingProtectionAppsRepository,
) : AppTrackingProtection {
    override suspend fun isOnboarded(): Boolean = withContext(dispatcherProvider.io()) {
        return@withContext vpnStore.didShowOnboarding()
    }

    override suspend fun isEnabled(): Boolean {
        return vpnFeaturesRegistry.isFeatureRegistered(AppTpVpnFeature.APPTP_VPN)
    }

    override suspend fun isRunning(): Boolean {
        return vpnFeaturesRegistry.isFeatureRunning(AppTpVpnFeature.APPTP_VPN)
    }

    override fun restart() {
        coroutineScope.launch(dispatcherProvider.io()) {
            vpnFeaturesRegistry.refreshFeature(AppTpVpnFeature.APPTP_VPN)
        }
    }

    override fun stop() {
        coroutineScope.launch {
            vpnFeaturesRegistry.unregisterFeature(AppTpVpnFeature.APPTP_VPN)
        }
    }

    override suspend fun getExcludedApps(): List<String> = withContext(dispatcherProvider.io()) {
        trackingProtectionAppsRepository.getExclusionAppsList()
    }
}
