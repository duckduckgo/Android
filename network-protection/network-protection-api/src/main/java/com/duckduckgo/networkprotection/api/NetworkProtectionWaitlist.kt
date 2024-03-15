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

package com.duckduckgo.networkprotection.api

import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

interface NetworkProtectionWaitlist {

    /**
     * Returns the state of the NetP waitlist
     * The caller DOES NOT need to specify the dispatcher when calling this method
     */
    suspend fun getState(): NetPWaitlistState

    /**
     * Call this method to get the [ActivityParams] corresponding to the activity to launch for the current
     * state of the waitlist beta
     * The caller DOES NOT need to specify the dispatcher when calling this method
     *
     * @return the [ActivityParams] for the activity to launch that corresponds to the current waitlist beta or `null` if no screen is available
     * for that state
     */
    suspend fun getScreenForCurrentState(): ActivityParams?

    sealed class NetPWaitlistState {
        object NotUnlocked : NetPWaitlistState()
        object JoinedWaitlist : NetPWaitlistState()
        object PendingInviteCode : NetPWaitlistState()
        data class InBeta(val termsAccepted: Boolean) : NetPWaitlistState()
    }
}
