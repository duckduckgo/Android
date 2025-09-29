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

import com.duckduckgo.app.attributed.metrics.AttributedMetricsConfigFeature
import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDataStore
import com.duckduckgo.app.attributed.metrics.store.DateProvider
import com.duckduckgo.app.attributed.metrics.store.EventRepository
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.privacy.config.api.PrivacyConfigCallbackPlugin
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesMultibinding(AppScope::class, AtbLifecyclePlugin::class)
@ContributesBinding(AppScope::class, AttributedMetricClient::class)
@SingleInstanceIn(AppScope::class)
class RealAttributedMetricClient @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
    private val eventRepository: EventRepository,
    private val dataStore: AttributedMetricsDataStore,
    private val dateProvider: DateProvider,
    private val attributedMetricsConfigFeature: AttributedMetricsConfigFeature,
) : AttributedMetricClient,
    AtbLifecyclePlugin,
    PrivacyConfigCallbackPlugin {

    // We only want to enable Attributed Metrics for new installations
    override fun onAppAtbInitialized() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            // atb happens after remote config is downloaded, here we should have the latest value
            if (attributedMetricsConfigFeature.self().isEnabled().not()) return@launch

            val initDate = dataStore.getInitializationDate()
            if (initDate == null) {
                val currentDate = dateProvider.getCurrentDate()
                dataStore.setInitializationDate(currentDate)
                if (appBuildConfig.isAppReinstall()) {
                    // Do not start metrics for returning users
                    return@launch
                } else {
                    dataStore.setEnabled(true)
                }
            }
        }
    }

    override fun collectEvent(eventName: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!isEnabled()) return@launch
            eventRepository.collectEvent(eventName)
        }
    }

    override suspend fun getEventStats(
        eventName: String,
        days: Int,
    ): EventStats =
        withContext(dispatcherProvider.io()) {
            if (!isEnabled()) {
                return@withContext EventStats(daysWithEvents = 0, rollingAverage = 0.0, totalEvents = 0)
            }
            eventRepository.getEventStats(eventName, days)
        }

    override fun emitMetric(metric: AttributedMetric) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!isEnabled()) return@launch
            val parameters = metric.getMetricParameters()
            // Implement metric emission logic
        }
    }

    // Check if Attributed Metrics is enabled (RemoteConfig) and initialization date is set
    private suspend fun isEnabled(): Boolean = dataStore.isEnabled() && dataStore.getInitializationDate() != null

    override fun onPrivacyConfigDownloaded() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            dataStore.setEnabled(attributedMetricsConfigFeature.self().isEnabled())
        }
    }
}
