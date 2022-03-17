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

package com.duckduckgo.mobile.android.vpn.dns

import android.content.Context
import android.content.SharedPreferences
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.TrackerBlockingVpnService
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.squareup.anvil.annotations.ContributesMultibinding
import dummy.ui.VpnPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(VpnScope::class)
class PrivateDnsSettingMonitor @Inject constructor(
    private val context: Context,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val vpnPreferences: VpnPreferences,
) : VpnServiceCallbacks {

    private val vpnSharedPreferencesListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (vpnPreferences.isPrivateDnsKey(key)) {
            appCoroutineScope.launch {
                TrackerBlockingVpnService.restartVpnService(context)
            }
        }
    }

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        vpnPreferences.registerOnSharedPreferenceChangeListener(vpnSharedPreferencesListener)
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStateMonitor.VpnStopReason) {
        vpnPreferences.unregisterOnSharedPreferenceChangeListener(vpnSharedPreferencesListener)
    }
}
