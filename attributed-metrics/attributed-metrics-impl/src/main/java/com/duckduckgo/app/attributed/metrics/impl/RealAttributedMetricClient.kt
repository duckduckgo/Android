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

package com.duckduckgo.app.attributed.metrics.impl

import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class, AtbLifecyclePlugin::class)
@ContributesBinding(AppScope::class, AttributedMetricClient::class)
@SingleInstanceIn(AppScope::class)
class RealAttributedMetricClient @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val appBuildConfig: AppBuildConfig,
): AttributedMetricClient, AtbLifecyclePlugin {

    override fun onAppAtbInitialized() {
        appCoroutineScope.launch {
            if (appBuildConfig.isAppReinstall()) {
                // Do not start metrics for returning users
                return@launch
            } else {
                // enable collecting events and emitting metrics
            }
        }
    }

    override fun collectEvent(eventName: String) {
        TODO("Not yet implemented")
    }

    override suspend fun getEventStats(
        eventName: String,
        days: Int
    ): EventStats {
        TODO("Not yet implemented")
    }

    override fun emitMetric(metric: AttributedMetric) {
        TODO("Not yet implemented")
    }
}
