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

package com.duckduckgo.sync.impl.metrics

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesMultibinding(AppScope::class, AttributedMetric::class)
@ContributesMultibinding(AppScope::class, MainProcessLifecycleObserver::class)
@SingleInstanceIn(AppScope::class)
class SyncDevicesAttributeMetric @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val attributedMetricClient: AttributedMetricClient,
    private val attributedMetricConfig: AttributedMetricConfig,
    private val connectedDevicesObserver: ConnectedDevicesObserver,
) : AttributedMetric, MainProcessLifecycleObserver {

    companion object {
        private const val PIXEL_NAME = "attributed_metric_synced_device"
        private const val FEATURE_TOGGLE_NAME = "syncDevices"
    }

    private val isEnabled: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val bucketConfig: Deferred<MetricBucket> = appCoroutineScope.async(start = LAZY) {
        attributedMetricConfig.getBucketConfiguration()[PIXEL_NAME] ?: MetricBucket(
            buckets = listOf(1),
            version = 0,
        )
    }

    override fun onCreate(owner: LifecycleOwner) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (isEnabled.await()) {
                connectedDevicesObserver.observeConnectedDevicesCount().collect { deviceCount ->
                    if (deviceCount > 0) {
                        attributedMetricClient.emitMetric(this@SyncDevicesAttributeMetric)
                    }
                }
            }
        }
    }

    override fun getPixelName(): String = PIXEL_NAME

    override suspend fun getMetricParameters(): Map<String, String> {
        val connectedDevices = connectedDevicesObserver.observeConnectedDevicesCount().value
        val params = mutableMapOf(
            "device_count" to getBucketValue(connectedDevices).toString(),
            "version" to bucketConfig.await().version.toString(),
        )
        return params
    }

    override suspend fun getTag(): String {
        val connectedDevices = connectedDevicesObserver.observeConnectedDevicesCount().value
        return getBucketValue(connectedDevices).toString()
    }

    private suspend fun getBucketValue(number: Int): Int {
        val buckets = bucketConfig.await().buckets
        return buckets.indexOfFirst { bucket -> number <= bucket }.let { index ->
            if (index == -1) buckets.size else index
        }
    }

    private suspend fun getToggle(toggleName: String) =
        attributedMetricConfig.metricsToggles().firstOrNull { toggle ->
            toggle.featureName().name == toggleName
        }
}
