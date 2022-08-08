/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.waitlist.store

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.waitlist.AppTrackingProtectionWaitlistDataStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface AtpWaitlistStateRepository {
    fun getState(): WaitlistState
    fun joinedAfterCuttingDate(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class WaitlistStateRepository @Inject constructor(
    private val dataStore: AppTrackingProtectionWaitlistDataStore,
) : AtpWaitlistStateRepository {

    override fun getState(): WaitlistState {
        if (didJoinBeta()) {
            return WaitlistState.InBeta
        }
        if (didJoinWaitlist()) {
            return WaitlistState.JoinedWaitlist(dataStore.sendNotification)
        }
        return WaitlistState.NotJoinedQueue
    }

    override fun joinedAfterCuttingDate(): Boolean {
        return dataStore.waitlistTimestamp > CUTOFF_DATE
    }

    private fun didJoinBeta(): Boolean = dataStore.inviteCode != null

    private fun didJoinWaitlist(): Boolean = dataStore.waitlistTimestamp != -1 && dataStore.waitlistToken != null

    companion object {
        // Cutoff date set to 1/1/2022 https://app.asana.com/0/1174433894299346/1201742199841250
        const val CUTOFF_DATE = 1641071328
    }
}

sealed class WaitlistState {
    object NotJoinedQueue : WaitlistState()
    data class JoinedWaitlist(val notify: Boolean = false) : WaitlistState()
    object InBeta : WaitlistState()
    object CodeRedeemed : WaitlistState()
}
