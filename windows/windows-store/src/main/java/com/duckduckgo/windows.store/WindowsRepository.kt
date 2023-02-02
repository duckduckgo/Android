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

package com.duckduckgo.windows.store

import com.duckduckgo.windows.api.WindowsWaitlistState

interface WindowsWaitlistRepository {
    fun joinWaitlist(timestamp: Int, token: String)
    fun getToken(): String?
    fun getTimestamp(): Int
    fun getInviteCode(): String?
    fun setInviteCode(inviteCode: String)
    fun getState(): WindowsWaitlistState
}

class RealWindowsWaitlistRepository(
    private val dataStore: WindowsWaitlistDataStore,
) : WindowsWaitlistRepository {

    override fun joinWaitlist(
        timestamp: Int,
        token: String,
    ) {
        if (dataStore.waitlistTimestamp == -1) {
            dataStore.waitlistTimestamp = timestamp
        }
        if (dataStore.waitlistToken == null) {
            dataStore.waitlistToken = token
        }
    }

    override fun getToken(): String? = dataStore.waitlistToken

    override fun getTimestamp(): Int = dataStore.waitlistTimestamp

    override fun getInviteCode(): String? = dataStore.inviteCode

    override fun setInviteCode(inviteCode: String) {
        dataStore.inviteCode = inviteCode
    }

    override fun getState(): WindowsWaitlistState {
        dataStore.inviteCode?.let { inviteCode ->
            return WindowsWaitlistState.InBeta(inviteCode)
        }
        if (didJoinWaitlist()) {
            return WindowsWaitlistState.JoinedWaitlist
        }
        return WindowsWaitlistState.NotJoinedQueue
    }

    private fun didJoinWaitlist(): Boolean = dataStore.waitlistTimestamp != -1 && dataStore.waitlistToken != null
}
