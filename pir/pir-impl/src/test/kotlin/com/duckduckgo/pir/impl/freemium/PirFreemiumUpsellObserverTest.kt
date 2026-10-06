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

package com.duckduckgo.pir.impl.freemium

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pir.impl.pixels.PirPixelSender
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class PirFreemiumUpsellObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptions: Subscriptions = mock()
    private val dataStore: PirFreemiumDataStore = mock()
    private val pirPixelSender: PirPixelSender = mock()
    private val subscriptionStatus = MutableStateFlow(SubscriptionStatus.UNKNOWN)

    private fun observer(): PirFreemiumUpsellObserver {
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(subscriptionStatus)
        return PirFreemiumUpsellObserver(
            subscriptions = subscriptions,
            pirFreemiumDataStore = dataStore,
            pirPixelSender = pirPixelSender,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `test reports the upsell when an activated user gains an active subscription`() = runTest {
        whenever(dataStore.didActivate).thenReturn(true)
        observer().onCreate(mock())
        advanceUntilIdle()

        subscriptionStatus.value = SubscriptionStatus.AUTO_RENEWABLE
        advanceUntilIdle()

        verify(pirPixelSender).reportFreemiumUpsell()
    }

    @Test
    fun `test reports nothing when the user never activated freemium`() = runTest {
        whenever(dataStore.didActivate).thenReturn(false)
        observer().onCreate(mock())
        advanceUntilIdle()

        subscriptionStatus.value = SubscriptionStatus.AUTO_RENEWABLE
        advanceUntilIdle()

        verify(pirPixelSender, never()).reportFreemiumUpsell()
    }

    @Test
    fun `test reports nothing while the subscription is not active`() = runTest {
        whenever(dataStore.didActivate).thenReturn(true)
        observer().onCreate(mock())
        advanceUntilIdle()

        listOf(
            SubscriptionStatus.UNKNOWN,
            SubscriptionStatus.INACTIVE,
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.WAITING,
        ).forEach {
            subscriptionStatus.value = it
            advanceUntilIdle()
        }

        verify(pirPixelSender, never()).reportFreemiumUpsell()
    }

    @Test
    fun `test reports on an already active subscription at startup`() = runTest {
        whenever(dataStore.didActivate).thenReturn(true)
        subscriptionStatus.value = SubscriptionStatus.AUTO_RENEWABLE

        observer().onCreate(mock())
        advanceUntilIdle()

        verify(pirPixelSender).reportFreemiumUpsell()
    }
}
