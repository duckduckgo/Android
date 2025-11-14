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

import android.annotation.SuppressLint
import androidx.lifecycle.LifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.DefaultFeatureValue
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SyncDevicesAttributeMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val attributedMetricConfig: AttributedMetricConfig = mock()
    private val connectedDevicesObserver: ConnectedDevicesObserver = mock()
    private val syncToggle = FakeFeatureToggleFactory.create(FakeSyncMetricsConfigFeature::class.java)
    private val lifecycleOwner: LifecycleOwner = mock()
    private val connectedDevicesFlow = MutableStateFlow(0)

    private lateinit var testee: SyncDevicesAttributeMetric

    @Before
    fun setup() = runTest {
        syncToggle.syncDevices().setRawStoredState(State(true))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(listOf(syncToggle.syncDevices()))
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "attributed_metric_synced_device" to MetricBucket(
                    buckets = listOf(1),
                    version = 0,
                ),
            ),
        )
        whenever(connectedDevicesObserver.observeConnectedDevicesCount()).thenReturn(connectedDevicesFlow)

        testee = SyncDevicesAttributeMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            attributedMetricClient = attributedMetricClient,
            attributedMetricConfig = attributedMetricConfig,
            connectedDevicesObserver = connectedDevicesObserver,
        )
    }

    @Test
    fun whenPixelNameRequestedThenReturnCorrectName() {
        assertEquals("attributed_metric_synced_device", testee.getPixelName())
    }

    @Test
    fun whenOnCreateAndFFDisabledThenDoNotEmitMetric() = runTest {
        syncToggle.syncDevices().setRawStoredState(State(false))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(listOf(syncToggle.syncDevices()))
        connectedDevicesFlow.emit(1)

        testee.onCreate(lifecycleOwner)

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenOnCreateAndNoDevicesThenDoNotEmitMetric() = runTest {
        connectedDevicesFlow.emit(0)

        testee.onCreate(lifecycleOwner)

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenOnCreateAndHasDevicesThenEmitMetric() = runTest {
        connectedDevicesFlow.emit(1)

        testee.onCreate(lifecycleOwner)

        verify(attributedMetricClient).emitMetric(testee)
    }

    @Test
    fun whenGetMetricParametersThenReturnCorrectBucketValue() = runTest {
        // Map of device count to expected bucket
        val deviceCountExpectedBuckets = mapOf(
            1 to 0, // 1 device -> bucket 0
            2 to 1, // 2 devices -> bucket 1
            3 to 1, // 3 devices -> bucket 1
            5 to 1, // 5 devices -> bucket 1
        )

        deviceCountExpectedBuckets.forEach { (devices, bucket) ->
            connectedDevicesFlow.emit(devices)

            val realbucket = testee.getMetricParameters()["device_count"]

            assertEquals(
                "For $devices devices, should return bucket $bucket",
                bucket.toString(),
                realbucket,
            )
        }
    }

    @Test
    fun whenGetTagThenReturnCorrectBucketValue() = runTest {
        // Map of device count to expected bucket
        val deviceCountExpectedBuckets = mapOf(
            1 to "0", // 1 device -> bucket 0
            2 to "1", // 2 devices -> bucket 1
            3 to "1", // 3 devices -> bucket 1
            5 to "1", // 5 devices -> bucket 1
        )

        deviceCountExpectedBuckets.forEach { (devices, bucket) ->
            connectedDevicesFlow.emit(devices)

            val tag = testee.getTag()

            assertEquals(
                "For $devices devices, should return bucket $bucket",
                bucket,
                tag,
            )
        }
    }

    @Test
    fun whenGetMetricParametersThenReturnVersion() = runTest {
        connectedDevicesFlow.emit(1)

        val version = testee.getMetricParameters()["version"]

        assertEquals("0", version)
    }
}

interface FakeSyncMetricsConfigFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun syncDevices(): Toggle
}
