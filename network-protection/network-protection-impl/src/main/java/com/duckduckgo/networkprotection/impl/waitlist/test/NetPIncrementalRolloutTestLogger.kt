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

package com.duckduckgo.networkprotection.impl.waitlist.test

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.launch
import logcat.logcat

// This is a dry-run test, temporary, see https://app.asana.com/0/1203137811378537/1205266828096214/f
@ContributesMultibinding(AppScope::class)
class NetPIncrementalRolloutTestLogger @Inject constructor(
    private val netPIncrementalRolloutTestFeature: NetPIncrementalRolloutTestFeature,
    private val networkProtectionPixels: NetworkProtectionPixels,
    private val dispatcherProvider: DispatcherProvider,
) : MainProcessLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        owner.lifecycleScope.launch(dispatcherProvider.io()) {
            if (netPIncrementalRolloutTestFeature.rollout().isEnabled()) {
                logcat { "Test incremental rollout pixel" }
                networkProtectionPixels.waitlistIncrementalRolloutTest()
            }
        }
    }
}
