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
import com.duckduckgo.networkprotection.api.NetPWaitlistInvitedScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionManagementScreenNoParams
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.JoinedWaitlist
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.PendingInviteCode
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.state.NetPFeatureRemover
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import logcat.logcat

@ContributesBinding(AppScope::class)
class NetworkProtectionWaitlistImpl @Inject constructor(
    private val netPRemoteFeature: NetPRemoteFeatureWrapper,
    private val appBuildConfig: AppBuildConfig,
    private val netPWaitlistRepository: NetPWaitlistRepository,
    private val networkProtectionState: NetworkProtectionState,
    private val networkProtectionPixels: NetworkProtectionPixels,
) : NetworkProtectionWaitlist {
    override fun getState(): NetPWaitlistState {
        if (isTreated()) {
            networkProtectionPixels.waitlistBetaIsEnabled()

            return if (didJoinBeta()) {
                InBeta(netPWaitlistRepository.didAcceptWaitlistTerms())
            } else if (didJoinWaitlist()) {
                JoinedWaitlist
            } else {
                PendingInviteCode
            }
        }

        return NotUnlocked
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
            return netPRemoteFeature.isWaitlistActive()
        }

        // Both NetP and the waitlist features need to be enabled
        return netPRemoteFeature.isWaitlistEnabled() && netPRemoteFeature.isWaitlistActive()
    }

    private fun didJoinBeta(): Boolean = netPWaitlistRepository.getAuthenticationToken() != null

    private fun didJoinWaitlist(): Boolean {
        return runBlocking { netPWaitlistRepository.getWaitlistToken() != null }
    }
}

/**
 * This class is a wrapper around the [NetPRemoteFeature] just to enclose some of the logic we need to do while
 * checking the feature and sub-feature flags
 */
class NetPRemoteFeatureWrapper @Inject constructor(
    private val netPRemoteFeature: NetPRemoteFeature,
    private val netPFeatureRemover: NetPFeatureRemover,
    private val appBuildConfig: AppBuildConfig,
    private val coroutineScope: CoroutineScope,
) {
    /**
     * @return `true` if the waitlist beta is active. This is different that having waitlist enabled and they are
     * independent states.
     * If/when this method returns `false` (ie. waitlist beta is no longer active) we'll wipe out all internal NetP
     * data so that users cannot use NetP anymore
     */
    fun isWaitlistActive(): Boolean {
        return if (appBuildConfig.isInternalBuild()) {
            true
        } else if (netPRemoteFeature.waitlistBetaActive().isEnabled()) {
            true
        } else {
            // waitlistBetaActive == false means the waitlist beta period has ended, ie. wipe out NetP
            // Skip for Internal users
            coroutineScope.launch {
                logcat { "NetP waitlist beta ended, wiping out everything" }
                netPFeatureRemover.removeFeature()
            }
            return false
        }
    }

    /**
     * @return `true` when NetP waitlist beta is enabled, `false` otherwise
     */
    fun isWaitlistEnabled(): Boolean {
        return netPRemoteFeature.self().isEnabled() && netPRemoteFeature.waitlist().isEnabled()
    }
}
