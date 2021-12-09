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

package com.duckduckgo.vpn.internal.feature.waitlist

import com.duckduckgo.di.scopes.AppObjectGraph
import com.duckduckgo.mobile.android.vpn.waitlist.AppTrackingProtectionWaitlistDataStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(
    scope = AppObjectGraph::class,
    priority = ContributesBinding.Priority.HIGHEST
)
class InternalAppTrackingProtectionWailistDataStore @Inject constructor() : AppTrackingProtectionWaitlistDataStore {

    override var inviteCode: String?
        get() = "internaltoken"
        set(value) {}
    override var waitlistTimestamp: Int
        get() = 0
        set(value) {}
    override var waitlistToken: String?
        get() = ""
        set(value) {}
    override var sendNotification: Boolean
        get() = false
        set(value) {}
    override var lastUsedDate: String?
        get() = ""
        set(value) {}

    override fun canUseEncryption(): Boolean {
        return false
    }

}
