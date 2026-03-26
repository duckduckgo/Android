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

package com.duckduckgo.subscriptions.impl.metrics

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
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.impl.SubscriptionsManager
import com.duckduckgo.subscriptions.impl.repository.AuthRepository
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
import java.time.Instant
import java.time.ZoneId

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SubscriptionStatusAttributedMetricTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val attributedMetricClient: AttributedMetricClient = mock()
    private val authRepository: AuthRepository = mock()
    private val attributedMetricConfig: AttributedMetricConfig = mock()
    private val subscriptionsManager: SubscriptionsManager = mock()
    private val subscriptionToggle = FakeFeatureToggleFactory.create(
        FakeSubscriptionMetricsConfigFeature::class.java,
    )
    private val lifecycleOwner: LifecycleOwner = mock()
    private val subscriptionStatusFlow = MutableStateFlow(SubscriptionStatus.UNKNOWN)

    private lateinit var testee: SubscriptionStatusAttributedMetric

    @Before
    fun setup() = runTest {
        givenFFStatus(metricEnabled = true, canEmitMetric = true)
        whenever(attributedMetricConfig.getBucketConfiguration()).thenReturn(
            mapOf(
                "attributed_metric_subscribed" to MetricBucket(
                    buckets = listOf(0, 1),
                    version = 0,
                ),
            ),
        )
        whenever(subscriptionsManager.subscriptionStatus).thenReturn(subscriptionStatusFlow)

        testee = SubscriptionStatusAttributedMetric(
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            attributedMetricClient = attributedMetricClient,
            authRepository = authRepository,
            attributedMetricConfig = attributedMetricConfig,
            subscriptionsManager = subscriptionsManager,
        )
    }

    @Test
    fun whenPixelNameRequestedThenReturnCorrectName() {
        assertEquals("attributed_metric_subscribed", testee.getPixelName())
    }

    @Test
    fun whenOnCreateAndFFDisabledThenDoNotEmitMetric() = runTest {
        givenFFStatus(metricEnabled = false)
        givenDaysSinceSubscribed(0)

        testee.onCreate(lifecycleOwner)
        subscriptionStatusFlow.emit(SubscriptionStatus.AUTO_RENEWABLE)

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenOnCreateAndEmitDisabledThenDoNotEmitMetric() = runTest {
        givenFFStatus(metricEnabled = true, canEmitMetric = false)
        givenDaysSinceSubscribed(0)

        testee.onCreate(lifecycleOwner)
        subscriptionStatusFlow.emit(SubscriptionStatus.AUTO_RENEWABLE)

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenOnCreateAndSubscriptionNotActiveThenDoNotEmitMetric() = runTest {
        whenever(authRepository.getStatus()).thenReturn(SubscriptionStatus.INACTIVE)

        testee.onCreate(lifecycleOwner)
        subscriptionStatusFlow.emit(SubscriptionStatus.INACTIVE)

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenOnCreateAndSubscriptionAutoRenewableThenEmitMetric() = runTest {
        testee.onCreate(lifecycleOwner)

        givenDaysSinceSubscribed(0)
        whenever(authRepository.getStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        subscriptionStatusFlow.emit(SubscriptionStatus.AUTO_RENEWABLE)

        verify(attributedMetricClient).emitMetric(testee)
    }

    @Test
    fun whenOnCreateAndNonAutoRenewableSubscriptionThenEmitMetric() = runTest {
        testee.onCreate(lifecycleOwner)

        givenDaysSinceSubscribed(0)
        whenever(authRepository.getStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        subscriptionStatusFlow.emit(SubscriptionStatus.NOT_AUTO_RENEWABLE)

        verify(attributedMetricClient).emitMetric(testee)
    }

    @Test
    fun whenOnCreateAndNotEnrolledThenDoNotEmitMetric() = runTest {
        whenever(authRepository.getStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(authRepository.getLocalPurchasedAt()).thenReturn(null)

        testee.onCreate(lifecycleOwner)
        subscriptionStatusFlow.emit(SubscriptionStatus.AUTO_RENEWABLE)

        verify(attributedMetricClient, never()).emitMetric(testee)
    }

    @Test
    fun whenGetMetricParametersAndOnTrialThenReturnBucketZero() = runTest {
        givenDaysSinceSubscribed(7)
        whenever(authRepository.isFreeTrialActive()).thenReturn(true)

        val monthBucket = testee.getMetricParameters()["month"]

        assertEquals("0", monthBucket)
    }

    @Test
    fun whenGetMetricParametersThenReturnVersion() = runTest {
        givenDaysSinceSubscribed(7)
        whenever(authRepository.isFreeTrialActive()).thenReturn(false)

        val version = testee.getMetricParameters()["version"]

        assertEquals("0", version)
    }

    @Test
    fun whenGetMetricParametersThenReturnCorrectBucketValue() = runTest {
        whenever(authRepository.isFreeTrialActive()).thenReturn(false)

        // Map of days subscribed to expected bucket
        val daysSubscribedExpectedBuckets = mapOf(
            0 to "1", // 0-27 days -> month 1 -> bucket 1
            13 to "1", // middle of month 1 -> bucket 1
            27 to "1", // end of month 1 -> bucket 1
            28 to "2", // 28-55 days -> month 2 -> bucket 2
            41 to "2", // middle of month 2 -> bucket 2
            55 to "2", // end of month 2 -> bucket 2
            56 to "2", // 56-83 days -> month 3 -> bucket 2
            69 to "2", // middle of month 3 -> bucket 2
            83 to "2", // end of month 3 -> bucket 2
            84 to "2", // 84-111 days -> month 4 -> bucket 2
            97 to "2", // middle of month 4 -> bucket 2
            111 to "2", // end of month 4 -> bucket 2
        )

        daysSubscribedExpectedBuckets.forEach { (days, expectedBucket) ->
            givenDaysSinceSubscribed(days)

            val realMonthBucket = testee.getMetricParameters()["month"]

            assertEquals(
                "For $days days subscribed, should return bucket $expectedBucket",
                expectedBucket,
                realMonthBucket,
            )
        }
    }

    @Test
    fun whenGetTagAndOnTrialThenReturnBucketZero() = runTest {
        givenDaysSinceSubscribed(7)
        whenever(authRepository.isFreeTrialActive()).thenReturn(true)

        val tag = testee.getTag()

        assertEquals("0", tag)
    }

    @Test
    fun whenGetTagThenReturnCorrectBucketValue() = runTest {
        whenever(authRepository.isFreeTrialActive()).thenReturn(false)

        // Map of days subscribed to expected bucket
        val daysSubscribedExpectedBuckets = mapOf(
            0 to "1", // 0-27 days -> month 1 -> bucket 1
            13 to "1", // middle of month 1 -> bucket 1
            27 to "1", // end of month 1 -> bucket 1
            28 to "2", // 28-55 days -> month 2 -> bucket 2
            41 to "2", // middle of month 2 -> bucket 2
            55 to "2", // end of month 2 -> bucket 2
            56 to "2", // 56-83 days -> month 3 -> bucket 2
            69 to "2", // middle of month 3 -> bucket 2
            83 to "2", // end of month 3 -> bucket 2
            84 to "2", // 84-111 days -> month 4 -> bucket 2
            97 to "2", // middle of month 4 -> bucket 2
            111 to "2", // end of month 4 -> bucket 2
        )

        daysSubscribedExpectedBuckets.forEach { (days, bucket) ->
            givenDaysSinceSubscribed(days)

            val tag = testee.getTag()

            assertEquals(
                "For $days days subscribed, should return bucket $bucket",
                bucket,
                tag,
            )
        }
    }

    private suspend fun givenDaysSinceSubscribed(days: Int) {
        val etZone = ZoneId.of("America/New_York")
        val now = Instant.now()
        val nowInEt = now.atZone(etZone)
        val purchasedAt = nowInEt.minusDays(days.toLong())
        whenever(authRepository.getLocalPurchasedAt()).thenReturn(
            purchasedAt.toInstant().toEpochMilli(),
        )
        whenever(authRepository.getStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
    }

    private suspend fun givenFFStatus(metricEnabled: Boolean = true, canEmitMetric: Boolean = true) {
        subscriptionToggle.subscriptionRetention().setRawStoredState(State(metricEnabled))
        subscriptionToggle.canEmitSubscriptionRetention().setRawStoredState(State(canEmitMetric))
        whenever(attributedMetricConfig.metricsToggles()).thenReturn(
            listOf(
                subscriptionToggle.subscriptionRetention(),
                subscriptionToggle.canEmitSubscriptionRetention(),
            ),
        )
    }
}

interface FakeSubscriptionMetricsConfigFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun subscriptionRetention(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun canEmitSubscriptionRetention(): Toggle
}
