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
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistManager
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.networkprotection.subscription.NetpAuthManager.NetpAuthorizationStatus
import com.duckduckgo.networkprotection.subscription.NetpAuthManager.NetpAuthorizationStatus.NoValidPAT
import com.duckduckgo.networkprotection.subscription.NetpAuthManager.NetpAuthorizationStatus.Success
import com.duckduckgo.networkprotection.subscription.NetpAuthManager.NetpAuthorizationStatus.UnableToAuthorize
import com.duckduckgo.networkprotection.subscription.NetpAuthManager.NetpAuthorizationStatus.Unknown
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import logcat.logcat

interface NetpAuthManager {
    suspend fun authorize()
    fun getState(): Flow<NetpAuthorizationStatus>

    sealed class NetpAuthorizationStatus {
        object Unknown : NetpAuthorizationStatus()
        object Success : NetpAuthorizationStatus()
        object NoValidPAT : NetpAuthorizationStatus()
        data class UnableToAuthorize(val message: String) : NetpAuthorizationStatus()
    }
}

@ContributesBinding(ActivityScope::class)
class RealNetpAuthManager @Inject constructor(
    private val service: NetworkProtectionAuthService,
    private val dispatcherProvider: DispatcherProvider,
    private val netPWaitlistRepository: NetPWaitlistRepository,
    private val netPWaitlistManager: NetPWaitlistManager,
    private val netpSubscriptionManager: NetpSubscriptionManager,
) : NetpAuthManager {
    private val state: MutableStateFlow<NetpAuthorizationStatus> = MutableStateFlow(Unknown)

    override fun getState(): Flow<NetpAuthorizationStatus> {
        return state.asStateFlow()
    }

    override suspend fun authorize() {
        withContext(dispatcherProvider.io()) {
            try {
                val accessToken = netpSubscriptionManager.getToken()
                if (accessToken != null) {
                    service.authorize(NetPAuthorizeRequest(accessToken)).also {
                        netPWaitlistRepository.setAuthenticationToken(it.token)
                        logcat { "Netp auth: Token received" }
                        netPWaitlistManager.upsertState()
                    }
                    state.emit(Success)
                } else {
                    state.emit(NoValidPAT)
                }
            } catch (e: Exception) {
                logcat { "Netp auth: error in authorize $e" }
                state.emit(UnableToAuthorize(e.toString()))
            }
        }
    }
}
