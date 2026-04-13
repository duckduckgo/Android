/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.subscriptions.impl.pixels

import android.annotation.SuppressLint
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.subscriptions.impl.SubscriptionsFeature
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class PaywallNotSeenSchedulerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val workManager: WorkManager = mock()
    private val paywallMetricsManager: PaywallMetricsManager = mock()
    private val subscriptionFeature = FakeFeatureToggleFactory.create(SubscriptionsFeature::class.java)
    private lateinit var scheduler: PaywallNotSeenScheduler

    @Before
    fun setup() = runTest {
        subscriptionFeature.schedulePaywallNotSeenPixels().setRawStoredState(State(enable = true))
        scheduler = PaywallNotSeenScheduler(
            workManager = workManager,
            paywallMetricsManager = paywallMetricsManager,
            subscriptionsFeature = subscriptionFeature,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
        whenever(paywallMetricsManager.delayUntilMilestone(any())).thenReturn(0L)
    }

    @Test
    fun `when feature flag is disabled then no workers are scheduled`() = runTest {
        subscriptionFeature.schedulePaywallNotSeenPixels().setRawStoredState(State(enable = false))
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired(any())).thenReturn(false)

        scheduler.onStart(mock())
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(workManager, never()).enqueueUniqueWork(
            any<String>(),
            any<ExistingWorkPolicy>(),
            any<OneTimeWorkRequest>(),
        )
    }

    @Test
    fun `when paywall already seen then no workers are scheduled`() = runTest {
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(true)

        scheduler.onStart(mock())
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(workManager, never()).enqueueUniqueWork(
            any<String>(),
            any<ExistingWorkPolicy>(),
            any<OneTimeWorkRequest>(),
        )
    }

    @Test
    fun `when paywall not seen then all milestone workers are scheduled`() = runTest {
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired(any())).thenReturn(false)

        scheduler.onStart(mock())
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(workManager, times(PaywallNotSeenScheduler.MILESTONES.size)).enqueueUniqueWork(
            any<String>(),
            any<ExistingWorkPolicy>(),
            any<OneTimeWorkRequest>(),
        )
    }

    @Test
    fun `when all milestones are in the past then no workers are scheduled`() = runTest {
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired(any())).thenReturn(false)
        whenever(paywallMetricsManager.delayUntilMilestone(any())).thenReturn(null)

        scheduler.onStart(mock())
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(workManager, never()).enqueueUniqueWork(
            any<String>(),
            any<ExistingWorkPolicy>(),
            any<OneTimeWorkRequest>(),
        )
    }

    @Test
    fun `when some milestones are in the past then only future milestones are scheduled`() = runTest {
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired(any())).thenReturn(false)
        whenever(paywallMetricsManager.delayUntilMilestone(any())).thenReturn(null)
        whenever(paywallMetricsManager.delayUntilMilestone(PaywallNotSeenScheduler.MILESTONES["d7"]!!)).thenReturn(0L)
        whenever(paywallMetricsManager.delayUntilMilestone(PaywallNotSeenScheduler.MILESTONES["d14"]!!)).thenReturn(1000L)
        whenever(paywallMetricsManager.delayUntilMilestone(PaywallNotSeenScheduler.MILESTONES["d30"]!!)).thenReturn(2000L)

        scheduler.onStart(mock())
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(workManager, times(3)).enqueueUniqueWork(
            any<String>(),
            any<ExistingWorkPolicy>(),
            any<OneTimeWorkRequest>(),
        )
    }

    @Test
    fun `when a day bucket was already fired then that worker is not scheduled`() = runTest {
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired(any())).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired("d0")).thenReturn(true)

        scheduler.onStart(mock())
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        verify(workManager, never()).enqueueUniqueWork(
            eq(PaywallNotSeenScheduler.workName("d0")),
            any<ExistingWorkPolicy>(),
            any<OneTimeWorkRequest>(),
        )
    }

    @Test
    fun `workers are enqueued with correct unique work names`() = runTest {
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired(any())).thenReturn(false)

        scheduler.onStart(mock())
        coroutineRule.testScope.testScheduler.advanceUntilIdle()

        PaywallNotSeenScheduler.MILESTONES.keys.forEach { dayBucket ->
            verify(workManager).enqueueUniqueWork(
                eq(PaywallNotSeenScheduler.workName(dayBucket)),
                any<ExistingWorkPolicy>(),
                any<OneTimeWorkRequest>(),
            )
        }
    }
}
