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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenAndEnable
import com.duckduckgo.networkprotection.api.NetworkProtectionScreens.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.waitlist.NetworkProtectionWaitlistImpl
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class)
class NetworkProtectionState @Inject constructor(
    private val waitlistState: NetworkProtectionWaitlistImpl,
    private val subscriptionState: NetworkProtectionAccessState,
    private val subscriptions: Subscriptions,
) : NetworkProtectionWaitlist {

    override suspend fun getState(): NetPWaitlistState {
        return if (subscriptions.isEnabled()) {
            subscriptionState.getState()
        } else {
            waitlistState.getState()
        }
    }

    override suspend fun getScreenForCurrentState(): ActivityParams? {
        return if (subscriptions.isEnabled()) {
            subscriptionState.getScreenForCurrentState()
        } else {
            waitlistState.getScreenForCurrentState()
        }
    }
}

// TODO after Privacy pro launch this will become the NetworkProtectionState
class NetworkProtectionAccessState @Inject constructor(
    private val netPWaitlistRepository: NetPWaitlistRepository,
    private val networkProtectionState: NetworkProtectionState,
    private val dispatcherProvider: DispatcherProvider,
    private val netpSubscriptionManager: NetpSubscriptionManager,
    private val networkProtectionRepository: NetworkProtectionRepository,
    private val subscriptions: Subscriptions,
) : NetworkProtectionWaitlist {

    override suspend fun getState(): NetPWaitlistState = withContext(dispatcherProvider.io()) {
        if (isTreated()) {
            return@withContext if (!netpSubscriptionManager.hasValidEntitlement()) {
                // if entitlement check succeeded and no entitlement, reset state and hide access.
                handleRevokedVPNState()
                NotUnlocked
            } else {
                InBeta(netPWaitlistRepository.didAcceptWaitlistTerms())
            }
        }
        return@withContext NotUnlocked
    }

    private suspend fun handleRevokedVPNState() {
        if (networkProtectionState.isEnabled()) {
            networkProtectionRepository.vpnAccessRevoked = true
            networkProtectionState.stop()
        }
    }

    override suspend fun getScreenForCurrentState(): ActivityParams? {
        return when (val state = getState()) {
            is InBeta -> {
                if (netPWaitlistRepository.didAcceptWaitlistTerms() || networkProtectionState.isOnboarded()) {
                    NetworkProtectionManagementScreenNoParams
                } else {
                    NetworkProtectionManagementScreenAndEnable(false)
                }
            }

            JoinedWaitlist, NotUnlocked, PendingInviteCode -> null
        }
    }

    private suspend fun isTreated(): Boolean = subscriptions.isEnabled()
}
