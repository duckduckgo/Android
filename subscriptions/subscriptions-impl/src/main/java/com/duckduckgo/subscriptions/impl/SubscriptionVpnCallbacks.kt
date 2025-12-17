/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl

import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.subscriptions.impl.wideevents.FreeTrialConversionWideEvent
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles VPN lifecycle callbacks for the subscriptions module.
 *
 * This class bridges VPN events to subscription-related functionality,
 * such as reporting VPN activation for free trial conversion analysis.
 */
@ContributesMultibinding(VpnScope::class)
class SubscriptionVpnCallbacks @Inject constructor(
    private val freeTrialConversionWideEvent: FreeTrialConversionWideEvent,
) : VpnServiceCallbacks {

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            freeTrialConversionWideEvent.onVpnActivatedSuccessfully()
        }
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        // noop
    }

    override fun onVpnStartFailed(coroutineScope: CoroutineScope) {
        // noop
    }

    override fun onVpnStopped(coroutineScope: CoroutineScope, vpnStopReason: VpnStopReason) {
        // noop
    }
}
