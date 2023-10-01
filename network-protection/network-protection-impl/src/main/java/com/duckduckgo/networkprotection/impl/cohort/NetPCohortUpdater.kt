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

package com.duckduckgo.networkprotection.impl.cohort

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.service.VpnServiceCallbacks
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.threeten.bp.LocalDate

@ContributesMultibinding(
    scope = VpnScope::class,
    boundType = VpnServiceCallbacks::class,
)
class NetPCohortUpdater @Inject constructor(
    private val networkProtectionState: NetworkProtectionState,
    private val cohortStore: NetpCohortStore,
    private val dispatcherProvider: DispatcherProvider,
) : VpnServiceCallbacks {
    override fun onVpnStarted(coroutineScope: CoroutineScope) {
        attemptToSetNetPCohort(coroutineScope)
    }

    override fun onVpnReconfigured(coroutineScope: CoroutineScope) {
        attemptToSetNetPCohort(coroutineScope)
    }

    private fun attemptToSetNetPCohort(coroutineScope: CoroutineScope) {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (networkProtectionState.isEnabled()) {
                // skip if already stored
                cohortStore.cohortLocalDate?.let { return@launch }

                cohortStore.cohortLocalDate = LocalDate.now()
            }
        }
    }

    override fun onVpnStopped(
        coroutineScope: CoroutineScope,
        vpnStopReason: VpnStopReason,
    ) {
        // noop
    }
}
