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

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.duckduckgo.common.test.CoroutineTestRule
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

class PaywallNotSeenSchedulerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val workManager: WorkManager = mock()
    private val paywallMetricsManager: PaywallMetricsManager = mock()
    private lateinit var scheduler: PaywallNotSeenScheduler

    @Before
    fun setup() {
        scheduler = PaywallNotSeenScheduler(
            workManager = workManager,
            paywallMetricsManager = paywallMetricsManager,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
        whenever(paywallMetricsManager.delayUntilMilestone(any())).thenReturn(0L)
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
