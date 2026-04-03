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

import com.duckduckgo.subscriptions.impl.store.PaywallMetricsDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.concurrent.TimeUnit

class PaywallMetricsManagerTest {

    private val store: PaywallMetricsDataStore = mock()
    private lateinit var manager: PaywallMetricsManager

    @Before
    fun setup() {
        manager = PaywallMetricsManager(store)
    }

    @Test
    fun `paywallEverSeen delegates to store`() {
        whenever(store.paywallEverSeen).thenReturn(true)
        assertTrue(manager.paywallEverSeen)

        whenever(store.paywallEverSeen).thenReturn(false)
        assertFalse(manager.paywallEverSeen)
    }

    @Test
    fun `recordFirstPaywallSeen returns null when paywall already seen`() {
        whenever(store.paywallEverSeen).thenReturn(true)

        assertNull(manager.recordFirstPaywallSeen())
        verify(store, never()).paywallEverSeen = true
    }

    @Test
    fun `recordFirstPaywallSeen marks paywall as seen in store`() {
        whenever(store.paywallEverSeen).thenReturn(false)
        whenever(store.firstInstallTimestamp).thenReturn(System.currentTimeMillis())

        manager.recordFirstPaywallSeen()

        verify(store).paywallEverSeen = true
    }

    @Test
    fun `recordFirstPaywallSeen returns d0 when installed today`() {
        whenever(store.paywallEverSeen).thenReturn(false)
        whenever(store.firstInstallTimestamp).thenReturn(System.currentTimeMillis())

        assertEquals("d0", manager.recordFirstPaywallSeen())
    }

    @Test
    fun `recordFirstPaywallSeen returns d1_to_d3 when installed 2 days ago`() {
        whenever(store.paywallEverSeen).thenReturn(false)
        whenever(store.firstInstallTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(2))

        assertEquals("d1_to_d3", manager.recordFirstPaywallSeen())
    }

    @Test
    fun `recordFirstPaywallSeen returns d4_to_d7 when installed 5 days ago`() {
        whenever(store.paywallEverSeen).thenReturn(false)
        whenever(store.firstInstallTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(5))

        assertEquals("d4_to_d7", manager.recordFirstPaywallSeen())
    }

    @Test
    fun `recordFirstPaywallSeen returns d8_to_d14 when installed 10 days ago`() {
        whenever(store.paywallEverSeen).thenReturn(false)
        whenever(store.firstInstallTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10))

        assertEquals("d8_to_d14", manager.recordFirstPaywallSeen())
    }

    @Test
    fun `recordFirstPaywallSeen returns d15_to_d30 when installed 20 days ago`() {
        whenever(store.paywallEverSeen).thenReturn(false)
        whenever(store.firstInstallTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(20))

        assertEquals("d15_to_d30", manager.recordFirstPaywallSeen())
    }

    @Test
    fun `recordFirstPaywallSeen returns d30_plus when installed more than 30 days ago`() {
        whenever(store.paywallEverSeen).thenReturn(false)
        whenever(store.firstInstallTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(40))

        assertEquals("d30_plus", manager.recordFirstPaywallSeen())
    }

    @Test
    fun `isNotSeenDayFired delegates to store`() {
        whenever(store.isNotSeenDayFired("d0")).thenReturn(true)
        assertTrue(manager.isNotSeenDayFired("d0"))

        whenever(store.isNotSeenDayFired("d3")).thenReturn(false)
        assertFalse(manager.isNotSeenDayFired("d3"))
    }

    @Test
    fun `markNotSeenDayFired delegates to store`() {
        manager.markNotSeenDayFired("d7")
        verify(store).markNotSeenDayFired("d7")
    }

    @Test
    fun `delayUntilMilestone returns 0 when milestone is in the past`() {
        whenever(store.firstInstallTimestamp).thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10))

        assertEquals(0L, manager.delayUntilMilestone(checkAfterDays = 4))
    }

    @Test
    fun `delayUntilMilestone returns positive delay when milestone is in the future`() {
        whenever(store.firstInstallTimestamp).thenReturn(System.currentTimeMillis())

        assertTrue(manager.delayUntilMilestone(checkAfterDays = 4) > 0)
    }

    @Test
    fun `privacyDashboardEverOpened delegates to store`() {
        whenever(store.privacyDashboardEverOpened).thenReturn(true)
        assertTrue(manager.privacyDashboardEverOpened)

        whenever(store.privacyDashboardEverOpened).thenReturn(false)
        assertFalse(manager.privacyDashboardEverOpened)
    }

    @Test
    fun `onPrivacyDashboardOpened sets flag in store`() = runTest {
        manager.onPrivacyDashboardOpened()
        verify(store).privacyDashboardEverOpened = true
    }
}
