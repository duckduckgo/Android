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

package com.duckduckgo.networkprotection.impl.waitlist.store

import com.duckduckgo.networkprotection.impl.waitlist.NetPWaitlistState

interface NetPWaitlistRepository {
    fun getAuthenticationToken(): String?
    fun setAuthenticationToken(authToken: String)
    fun getState(isInternalBuild: Boolean): NetPWaitlistState
}

class RealNetPWaitlistRepository(
    private val dataStore: NetPWaitlistDataStore,
) : NetPWaitlistRepository {

    override fun getAuthenticationToken(): String? = dataStore.authToken

    override fun setAuthenticationToken(authToken: String) {
        dataStore.authToken = authToken
    }

    override fun getState(isInternalBuild: Boolean): NetPWaitlistState {
        if (isInternalBuild) {
            return if (didJoinBeta()) {
                // internal users bypass easter egg
                NetPWaitlistState.InBeta
            } else {
                NetPWaitlistState.PendingInviteCode
            }
        }

        if (didJoinBeta()) {
            return NetPWaitlistState.InBeta
        }

        return NetPWaitlistState.NotUnlocked
    }

    private fun didJoinBeta(): Boolean = dataStore.authToken != null
}
