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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestWorkerBuilder
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class PaywallNotSeenWorkerTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val pixelSender: SubscriptionPixelSender = mock()
    private val paywallMetricsManager: PaywallMetricsManager = mock()

    private fun buildWorker(dayBucket: String?): PaywallNotSeenWorker {
        val inputData = dayBucket?.let {
            androidx.work.workDataOf(PaywallNotSeenWorker.KEY_DAY_BUCKET to it)
        } ?: androidx.work.Data.EMPTY

        return TestWorkerBuilder
            .from(ApplicationProvider.getApplicationContext(), PaywallNotSeenWorker::class.java)
            .setInputData(inputData)
            .build()
            .also { worker ->
                worker.pixelSender = pixelSender
                worker.paywallMetricsManager = paywallMetricsManager
            }
    }

    @Test
    fun `when day bucket is missing then worker returns failure`() = runTest {
        val result = buildWorker(dayBucket = null).doWork()
        assertEquals(Result.failure(), result)
    }

    @Test
    fun `when paywall was already seen then pixel is not fired`() = runTest {
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(true)

        val result = buildWorker("d0").doWork()

        assertEquals(Result.success(), result)
        verify(pixelSender, never()).reportPaywallNotSeen("d0")
    }

    @Test
    fun `when pixel was already fired for this day then pixel is not fired again`() = runTest {
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired("d3")).thenReturn(true)

        val result = buildWorker("d3").doWork()

        assertEquals(Result.success(), result)
        verify(pixelSender, never()).reportPaywallNotSeen("d3")
    }

    @Test
    fun `when paywall not seen and pixel not fired then fires pixel and marks day as fired`() = runTest {
        whenever(paywallMetricsManager.paywallEverSeen).thenReturn(false)
        whenever(paywallMetricsManager.isNotSeenDayFired("d7")).thenReturn(false)

        val result = buildWorker("d7").doWork()

        assertEquals(Result.success(), result)
        verify(pixelSender).reportPaywallNotSeen("d7")
        verify(paywallMetricsManager).markNotSeenDayFired("d7")
    }
}