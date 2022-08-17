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

package com.duckduckgo.mobile.android.vpn.bug

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.apps.VpnExclusionList
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetectorInterceptor
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class BrowserTrafficTransparencyInterceptor @Inject constructor() : VpnTrackerDetectorInterceptor {

    override fun interceptTrackerRequest(
        hostname: String,
        packageId: String
    ): RequestTrackerType? {
        return when {
            // we should never really block anything in our app, even if/when we pass its traffic through the VPN
            VpnExclusionList.isDdgApp(packageId) -> {
                Timber.v("Transparency mode for DDG app: Not Tracker returned for $packageId / $hostname")
                RequestTrackerType.NotTracker(hostname)
            }
            else -> {
                Timber.v("Not intercepting for $packageId / $hostname")
                null
            }
        }
    }
}
