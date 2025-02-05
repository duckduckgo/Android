/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.pir.internal.brokers

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pir.internal.PirRemoteFeatures
import com.duckduckgo.subscriptions.api.Subscriptions
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class PirDataUpdateObserver @Inject constructor(
    @AppCoroutineScope private val coroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val brokerJsonUpdater: BrokerJsonUpdater,
    private val subscriptions: Subscriptions,
    private val pirRemoteFeatures: PirRemoteFeatures,
) : MainProcessLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        coroutineScope.launch(dispatcherProvider.io()) {
            if (pirRemoteFeatures.allowPirRun().isEnabled() && subscriptions.getAccessToken() != null) {
                logcat { "PIR-update: Attempting to update all broker data" }
                if (brokerJsonUpdater.update()) {
                    logcat { "PIR-update: Update successfully completed." }
                } else {
                    logcat { "PIR-update: Failed to complete." }
                }
            }
        }
    }
}
