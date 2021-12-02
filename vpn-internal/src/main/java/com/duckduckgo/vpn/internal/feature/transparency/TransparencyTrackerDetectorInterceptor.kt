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

package com.duckduckgo.vpn.internal.feature.transparency

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.VpnTrackerDetectorInterceptor
import com.squareup.anvil.annotations.ContributesMultibinding
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import dagger.SingleInstanceIn

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class TransparencyTrackerDetectorInterceptor @Inject constructor() : VpnTrackerDetectorInterceptor {

    private val enable = AtomicBoolean(false)

    fun setEnable(enable: Boolean) = this.enable.set(enable)

    override fun interceptTrackerRequest(hostname: String, packageId: String): RequestTrackerType? {
        return if (enable.get()) {
            Timber.v("Transparency mode: Not Tracker returned for $packageId / $hostname")
            RequestTrackerType.NotTracker(hostname)
        } else {
            Timber.v("Transparency mode: Not intercepting for $packageId / $hostname")
            null
        }
    }
}
