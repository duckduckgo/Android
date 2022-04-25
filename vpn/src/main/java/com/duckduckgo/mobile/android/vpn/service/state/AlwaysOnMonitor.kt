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

package com.duckduckgo.mobile.android.vpn.service.state

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.mobile.android.vpn.ui.onboarding.VpnStore
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class
)
class AlwaysOnMonitor @Inject constructor(
    private val vpnStore: VpnStore,
    private val appBuildConfig: AppBuildConfig,
    private val vpnService: Provider<TrackerBlockingVpnService>
) : VpnServiceCallbacks {

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        // this works for 85% of our users as of April 2022
        if (appBuildConfig.sdkInt >= 29) {
            try {
                val isAlwaysOnEnabled = vpnService.get().isAlwaysOn
                Timber.i("AlwaysOnMonitor, Always On Enabled: ${isAlwaysOnEnabled}")
                vpnStore.setAlwaysOn(isAlwaysOnEnabled)
            } catch (e: Exception) {
                vpnStore.setAlwaysOn(false)
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        // no-op
    }
}
