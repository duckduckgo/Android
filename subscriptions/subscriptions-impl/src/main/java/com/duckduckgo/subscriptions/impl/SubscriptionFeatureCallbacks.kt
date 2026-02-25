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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.subscriptions.impl.wideevents.FreeTrialConversionWideEvent
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles subscription-related callbacks from multiple sources
 *
 * Bridges these events into subscription wide events and analytics (e.g. [FreeTrialConversionWideEvent]).
 */
@ContributesMultibinding(VpnScope::class)
class SubscriptionFeatureCallbacks @Inject constructor(
    private val freeTrialConversionWideEvent: FreeTrialConversionWideEvent,
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : VpnServiceCallbacks, AtbLifecyclePlugin {

    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
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

    override fun onDuckAiRetentionAtbRefreshed(
        oldAtb: String,
        newAtb: String,
        metadata: Map<String, String?>,
    ) {
        if (metadata[KEY_MODEL_TIER] in MODEL_TIERS_PAID) {
            coroutineScope.launch(dispatcherProvider.io()) {
                freeTrialConversionWideEvent.onDuckAiPaidPromptSubmitted()
            }
        }
    }

    private companion object {
        const val KEY_MODEL_TIER = "modelTier"
        val MODEL_TIERS_PAID: Set<String> = setOf("plus")
    }
}
