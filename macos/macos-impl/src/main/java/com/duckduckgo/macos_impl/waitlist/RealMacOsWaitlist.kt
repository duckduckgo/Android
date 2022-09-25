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

package com.duckduckgo.macos_impl.waitlist

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.macos_api.MacOsWaitlist
import com.duckduckgo.macos_api.MacWaitlistState
import com.duckduckgo.macos_store.MacOsWaitlistState.InBeta
import com.duckduckgo.macos_store.MacOsWaitlistState.JoinedWaitlist
import com.duckduckgo.macos_store.MacOsWaitlistState.NotJoinedQueue
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealMacOsWaitlist @Inject constructor(private val macOsWaitlistManager: MacOsWaitlistManager) : MacOsWaitlist {

    override fun getWaitlistState(): MacWaitlistState {
        return when (macOsWaitlistManager.getState()) {
            is InBeta -> MacWaitlistState.InBeta
            is JoinedWaitlist -> MacWaitlistState.JoinedWaitlist
            is NotJoinedQueue -> MacWaitlistState.NotJoinedQueue
        }
    }
}
