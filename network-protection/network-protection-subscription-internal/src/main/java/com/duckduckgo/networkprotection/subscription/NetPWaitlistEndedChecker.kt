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

package com.duckduckgo.networkprotection.subscription

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.prefs.VpnSharedPreferencesProvider
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesMultibinding(VpnScope::class)
@SingleInstanceIn(VpnScope::class)
class NetPWaitlistEndedChecker @Inject constructor(
    private val subscriptions: Subscriptions,
    private val netpSubscriptionManager: NetpSubscriptionManager,
    private val dispatcherProvider: DispatcherProvider,
    private val networkProtectionState: NetworkProtectionState,
    private val vpnSharedPreferencesProvider: VpnSharedPreferencesProvider,
) : VpnServiceCallbacks {

    private val preferences: SharedPreferences by lazy {
        vpnSharedPreferencesProvider.getSharedPreferences("com.duckduckgo.networkprotection.subscription.vpnWaitlistChecker")
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (!preferences.isAlreadyChecked() && networkProtectionState.isEnabled() && subscriptions.isEnabled()) {
                preferences.checkDone()
                var hasEntitlement = false
                // I know, I don't like it either, but it seems we can't ensure a race otherwise
                for (retries in 1..5) {
                    hasEntitlement = netpSubscriptionManager.hasValidEntitlement()
                    if (hasEntitlement) return@launch
                    delay(200)
                }
                if (!hasEntitlement) {
                    logcat { "VPN enabled and privacy pro enabled but no entitlements, stopping VPN..." }
                    networkProtectionState.stop()
                }
            }
        }
    }

    private fun SharedPreferences.checkDone() {
        this.edit(commit = true) { putBoolean("done", true) }
    }

    private fun SharedPreferences.isAlreadyChecked(): Boolean {
        return this.getBoolean("done", false)
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
    }
}
