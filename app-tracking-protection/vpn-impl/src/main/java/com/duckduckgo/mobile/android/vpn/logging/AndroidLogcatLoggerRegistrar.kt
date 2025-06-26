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

package com.duckduckgo.mobile.android.vpn.logging

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.lifecycle.VpnProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.mobile.android.vpn.feature.AppTpLocalFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import logcat.AndroidLogcatLogger
import logcat.LogPriority
import logcat.LogcatLogger
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = VpnProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class AndroidLogcatLoggerRegistrar @Inject constructor(
    private val appTpLocalFeature: AppTpLocalFeature,
) : VpnProcessLifecycleObserver, MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        onVpnProcessCreated()
    }

    override fun onVpnProcessCreated() {
        if (appTpLocalFeature.verboseLogging().isEnabled()) {
            LogcatLogger.install(AndroidLogcatLogger(LogPriority.VERBOSE))
            logcat { "Registering LogcatLogger" }
        }
    }
}
