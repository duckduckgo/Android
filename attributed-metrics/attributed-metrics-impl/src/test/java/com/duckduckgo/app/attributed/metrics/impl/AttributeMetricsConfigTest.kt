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

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.attributed.metrics.AttributedMetricsConfigFeature
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class AttributeMetricsConfigTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricsConfigFeature: AttributedMetricsConfigFeature =
        FakeFeatureToggleFactory.create(AttributedMetricsConfigFeature::class.java).apply {
            self().setRawStoredState(State(true))
        }
    private val featureTogglesInventory: FeatureTogglesInventory = mock()
    private val moshi = Moshi.Builder().build()
    private val attributedMetricToggle = attributedMetricsConfigFeature.self()

    private lateinit var testee: AttributeMetricsConfig

    @Before
    fun setup() {
        testee = AttributeMetricsConfig(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            attributedMetricsConfigFeature = attributedMetricsConfigFeature,
            featureTogglesInvestory = featureTogglesInventory,
            moshi = moshi,
        )
    }

    @Test
    fun whenFeatureDisabledThenReturnEmptyToggles() = runTest {
        attributedMetricsConfigFeature.self().setRawStoredState(State(false))

        val toggles = testee.metricsToggles()

        assertEquals(emptyList<Toggle>(), toggles)
        verify(featureTogglesInventory, never()).getAllTogglesForParent(any())
    }

    @Test
    fun whenFeatureEnabledThenReturnToggles() = runTest {
        val expectedToggles = listOf<Toggle>(mock(), mock())
        whenever(featureTogglesInventory.getAllTogglesForParent(any())).thenReturn(expectedToggles)

        val toggles = testee.metricsToggles()

        assertEquals(expectedToggles, toggles)
    }

    @Test
    fun whenFeatureDisabledThenReturnEmptyBucketConfig() = runTest {
        val settings = """
            {
                "user_active_past_week": {
                    "buckets": [2, 4],
                    "version": 0
                },
                "user_average_searches_past_week": {
                    "buckets": [5, 9],
                    "version": 1
                }
            }
        """.trimIndent()
        attributedMetricToggle.setRawStoredState(
            State(
                remoteEnableState = false,
                settings = settings,
            ),
        )
        val config = testee.getBucketConfiguration()

        assertEquals(emptyMap<String, MetricBucket>(), config)
    }

    @Test
    fun whenFeatureEnabledButNoSettingsThenReturnEmptyBucketConfig() = runTest {
        attributedMetricToggle.setRawStoredState(
            State(
                remoteEnableState = true,
                settings = null,
            ),
        )

        val config = testee.getBucketConfiguration()

        assertEquals(emptyMap<String, MetricBucket>(), config)
    }

    @Test
    fun whenFeatureEnabledAndValidSettingsThenReturnBucketConfig() = runTest {
        val settings = """
            {
                "user_active_past_week": {
                    "buckets": [2, 4],
                    "version": 0
                },
                "user_average_searches_past_week": {
                    "buckets": [5, 9],
                    "version": 1
                }
            }
        """.trimIndent()
        attributedMetricToggle.setRawStoredState(
            State(
                remoteEnableState = true,
                settings = settings,
            ),
        )

        val config = testee.getBucketConfiguration()

        assertEquals(
            mapOf(
                "user_active_past_week" to MetricBucket(
                    buckets = listOf(2, 4),
                    version = 0,
                ),
                "user_average_searches_past_week" to MetricBucket(
                    buckets = listOf(5, 9),
                    version = 1,
                ),
            ),
            config,
        )
    }

    @Test
    fun whenFeatureEnabledAndInvalidSettingsThenReturnEmptyBucketConfig() = runTest {
        val invalidSettings = """
            invalid json
        """.trimIndent()

        attributedMetricToggle.setRawStoredState(
            State(
                remoteEnableState = true,
                settings = invalidSettings,
            ),
        )

        val config = testee.getBucketConfiguration()

        assertEquals(emptyMap<String, MetricBucket>(), config)
    }
}
