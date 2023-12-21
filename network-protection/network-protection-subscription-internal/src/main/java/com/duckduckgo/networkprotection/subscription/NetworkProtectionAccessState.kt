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

package com.duckduckgo.networkprotection.subscription

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetPWaitlistInvitedScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.VerifySubscription
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.networkprotection.subscription.ui.NetpVerifySubscriptionParams
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesBinding.Priority.HIGHEST
import javax.inject.Inject
import kotlinx.coroutines.withContext

@ContributesBinding(
    AppScope::class,
    priority = HIGHEST, // binding for internal-testing build wins
)
class NetworkProtectionAccessState @Inject constructor(
    private val netPWaitlistRepository: NetPWaitlistRepository,
    private val networkProtectionState: NetworkProtectionState,
    private val appBuildConfig: AppBuildConfig,
    private val dispatcherProvider: DispatcherProvider,
    private val netpSubscriptionManager: NetpSubscriptionManager,
    private val networkProtectionRepository: NetworkProtectionRepository,
) : NetworkProtectionWaitlist {

    override suspend fun getState(): NetPWaitlistState = withContext(dispatcherProvider.io()) {
        if (isTreated()) {
            val hasValidEntitlementResult = netpSubscriptionManager.hasValidEntitlement()
            return@withContext if (!hasValidEntitlementResult.isSuccess || !hasValidEntitlementResult.getOrDefault(false)) {
                if (hasValidEntitlementResult.isSuccess) {
                    handleVPNAccessState(hasValidEntitlementResult.getOrDefault(false))
                }
                NotUnlocked
            } else if (netPWaitlistRepository.getAuthenticationToken() == null) {
                VerifySubscription
            } else {
                InBeta(netPWaitlistRepository.didAcceptWaitlistTerms())
            }
        }
        return@withContext NotUnlocked
    }

    private suspend fun handleVPNAccessState(hasValidEntitlement: Boolean) {
        if (networkProtectionState.isEnabled()) {
            if (hasValidEntitlement) {
                networkProtectionRepository.vpnAccessRevoked = false
            } else {
                networkProtectionRepository.vpnAccessRevoked = true
                networkProtectionState.stop()
            }
        }
    }

    override suspend fun getScreenForCurrentState(): ActivityParams {
        return when (getState()) {
            is InBeta -> {
                if (netPWaitlistRepository.didAcceptWaitlistTerms() || networkProtectionState.isOnboarded()) {
                    NetworkProtectionManagementScreenNoParams
                } else {
                    NetPWaitlistInvitedScreenNoParams
                }
            }

            JoinedWaitlist, NotUnlocked, PendingInviteCode, VerifySubscription -> NetpVerifySubscriptionParams
        }
    }

    private fun isTreated(): Boolean = appBuildConfig.isDebug
}
