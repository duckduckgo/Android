/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.macos_store

interface MacOsWaitlistRepository {
    fun joinWaitlist(timestamp: Int, token: String)
    fun notifyOnJoinedWaitlist()
    fun getToken(): String?
    fun getTimestamp(): Int
    fun getInviteCode(): String?
    fun setInviteCode(inviteCode: String)
    fun getState(): MacOsWaitlistState
    fun isNotificationEnabled(): Boolean
}

class RealMacOsWaitlistRepository(
    private val dataStore: MacOsWaitlistDataStore
) : MacOsWaitlistRepository {

    override fun joinWaitlist(
        timestamp: Int,
        token: String
    ) {
        if (dataStore.waitlistTimestamp == -1) {
            dataStore.waitlistTimestamp = timestamp
        }
        if (dataStore.waitlistToken == null) {
            dataStore.waitlistToken = token
        }
    }

    override fun notifyOnJoinedWaitlist() {
        dataStore.sendNotification = true
    }

    override fun getToken(): String? = dataStore.waitlistToken

    override fun getTimestamp(): Int = dataStore.waitlistTimestamp

    override fun getInviteCode(): String? = dataStore.inviteCode

    override fun setInviteCode(inviteCode: String) {
        dataStore.inviteCode = inviteCode
    }

    override fun getState(): MacOsWaitlistState {
        dataStore.inviteCode?.let { inviteCode ->
            return MacOsWaitlistState.InBeta(inviteCode)
        }
        if (didJoinWaitlist()) {
            return MacOsWaitlistState.JoinedWaitlist(dataStore.sendNotification)
        }
        return MacOsWaitlistState.NotJoinedQueue
    }

    override fun isNotificationEnabled(): Boolean = dataStore.sendNotification

    private fun didJoinWaitlist(): Boolean = dataStore.waitlistTimestamp != -1 && dataStore.waitlistToken != null
}

sealed class MacOsWaitlistState {
    object NotJoinedQueue : MacOsWaitlistState()
    data class JoinedWaitlist(val notify: Boolean = false) : MacOsWaitlistState()
    data class InBeta(val inviteCode: String) : MacOsWaitlistState()
}
