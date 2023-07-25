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

package com.duckduckgo.networkprotection.impl.waitlist

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams
import com.duckduckgo.networkprotection.api.NetPInviteCodeScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@ContributesBinding(AppScope::class)
class NetworkProtectionWaitlistImpl @Inject constructor(
    private val netPRemoteFeature: NetPRemoteFeature,
    private val appBuildConfig: AppBuildConfig,
    private val netPWaitlistRepository: NetPWaitlistRepository,
) : NetworkProtectionWaitlist {
    override fun getState(): NetPWaitlistState {
        if (isTreated()) {
            return if (didJoinBeta()) {
                InBeta
            } else if (didJoinWaitlist()) {
                JoinedWaitlist
            } else {
                PendingInviteCode
            }
        }

        return NotUnlocked
    }

    override fun getScreenForCurrentState(): ActivityParams {
        return when (getState()) {
            InBeta -> {
                if (netPWaitlistRepository.didAcceptWaitlistTerms()) {
                    NetworkProtectionManagementScreenNoParams
                } else {
                    NetPInviteCodeScreenNoParams
                }
            }
            JoinedWaitlist, NotUnlocked, PendingInviteCode -> NetPWaitlistScreenNoParams
        }
    }

    private fun isTreated(): Boolean {
        if (appBuildConfig.isInternalBuild()) {
            // internal users are always treated
            return true
        }
        // User is in beta already
        if (netPWaitlistRepository.getAuthenticationToken() != null) {
            return true
        }
        // Both NetP and the waitlist features need to be enabled
        return netPRemoteFeature.self().isEnabled() && netPRemoteFeature.waitlist().isEnabled()
    }

    private fun didJoinBeta(): Boolean = netPWaitlistRepository.getAuthenticationToken() != null

    private fun didJoinWaitlist(): Boolean {
        return runBlocking { netPWaitlistRepository.getWaitlistToken() != null }
    }
}
