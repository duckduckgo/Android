/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.vpn.internal.feature.health

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.health.AppHealthMonitor
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class InternalAppHealthMonitor @Inject constructor(private val appHealthMonitor: AppHealthMonitor) :
    VpnServiceCallbacks {

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        appHealthMonitor.startMonitoring()
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason
    ) {
        appHealthMonitor.stopMonitoring()
    }
}
