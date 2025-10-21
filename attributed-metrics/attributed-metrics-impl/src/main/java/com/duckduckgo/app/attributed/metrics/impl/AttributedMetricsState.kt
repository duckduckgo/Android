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

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.attributed.metrics.AttributedMetricsConfigFeature
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDataStore
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Interface for checking if attributed metrics are active.
 * The active state is determined by:
 * 1. Having an initialization date
 * 2. Being within the collection period (6 months = 24 weeks)
 * 3. Being enabled in remote config
 */
interface AttributedMetricsState {
    suspend fun isActive(): Boolean
    suspend fun canEmitMetrics(): Boolean
}

@ContributesBinding(
    scope = AppScope::class,
    boundType = AttributedMetricsState::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = AtbLifecyclePlugin::class,
)
@SingleInstanceIn(AppScope::class)
class RealAttributedMetricsState @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val dataStore: AttributedMetricsDataStore,
    private val attributedMetricsConfigFeature: AttributedMetricsConfigFeature,
    private val appBuildConfig: AppBuildConfig,
    private val attributedMetricsDateUtils: AttributedMetricsDateUtils,
) : AttributedMetricsState, MainProcessLifecycleObserver, AtbLifecyclePlugin {

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            checkCollectionPeriodAndUpdateState()
        }
    }

    // this is called when the ATB is initialized after privacy config is downloaded, only once after app is installed
    override fun onAppAtbInitialized() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat(tag = "AttributedMetrics") {
                "Detected New Install, try to initialize Attributed Metrics"
            }
            if (!isEnabled()) {
                logcat(tag = "AttributedMetrics") {
                    "Client disabled from remote config, skipping initialization"
                }
                return@launch
            }

            val initDate = dataStore.getInitializationDate()
            if (initDate == null) {
                logcat(tag = "AttributedMetrics") {
                    "Setting initialization date for Attributed Metrics"
                }
                val currentDate = attributedMetricsDateUtils.getCurrentDate()
                dataStore.setInitializationDate(currentDate)
                if (appBuildConfig.isAppReinstall()) {
                    logcat(tag = "AttributedMetrics") {
                        "App reinstall detected, attributed metrics will not be active"
                    }
                    // Do not start metrics for returning users
                    dataStore.setActive(false)
                } else {
                    logcat(tag = "AttributedMetrics") {
                        "New install detected, attributed metrics active"
                    }
                    dataStore.setActive(true)
                }
            }
            logClientStatus()
        }
    }

    override suspend fun isActive(): Boolean = isEnabled() && dataStore.isActive() && dataStore.getInitializationDate() != null

    override suspend fun canEmitMetrics(): Boolean = isActive() && emitMetricsEnabled()

    private suspend fun checkCollectionPeriodAndUpdateState() {
        val initDate = dataStore.getInitializationDate()

        if (initDate == null) {
            logcat(tag = "AttributedMetrics") {
                "Client not initialized, skipping state check"
            }
            return
        }

        val daysSinceInit = attributedMetricsDateUtils.daysSince(initDate)
        val isWithinPeriod = daysSinceInit <= COLLECTION_PERIOD_DAYS
        val newClientActiveState = isWithinPeriod && dataStore.isActive()

        logcat(tag = "AttributedMetrics") {
            "Updating client state to $newClientActiveState result of -> within period? $isWithinPeriod, client active? ${dataStore.isActive()}"
        }
        dataStore.setActive(newClientActiveState)
        logClientStatus()
    }

    private suspend fun logClientStatus() = logcat(tag = "AttributedMetrics") {
        "Client status running: ${isActive()} -> isActive: ${dataStore.isActive()}, isEnabled: ${isEnabled()}," +
            " initializationDate: ${dataStore.getInitializationDate()}"
    }

    private fun isEnabled(): Boolean = attributedMetricsConfigFeature.self().isEnabled()

    private fun emitMetricsEnabled(): Boolean = attributedMetricsConfigFeature.emitAllMetrics().isEnabled()

    companion object {
        private const val COLLECTION_PERIOD_DAYS = 168 // 24 weeks * 7 days (6 months in weeks)
    }
}
