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

package com.duckduckgo.subscriptions.impl

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.subscriptions.api.SubscriptionPurchaseSuccessPlugin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubscriptionPurchaseSuccessObserverTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val subscriptionsManager: SubscriptionsManager = mock()
    private val purchaseState = MutableSharedFlow<CurrentPurchase>(replay = 0, extraBufferCapacity = 1)

    private class CountingPlugin : SubscriptionPurchaseSuccessPlugin {
        var invocations = 0
        override suspend fun onSubscriptionPurchaseSuccess() {
            invocations++
        }
    }

    private class ThrowingPlugin : SubscriptionPurchaseSuccessPlugin {
        override suspend fun onSubscriptionPurchaseSuccess() = throw RuntimeException("plugin blew up")
    }

    private val plugin = CountingPlugin()

    private fun observer(
        plugins: List<SubscriptionPurchaseSuccessPlugin> = listOf(plugin),
    ): SubscriptionPurchaseSuccessObserver {
        whenever(subscriptionsManager.currentPurchaseState).thenReturn(purchaseState)
        return SubscriptionPurchaseSuccessObserver(
            subscriptionsManager = subscriptionsManager,
            plugins = object : PluginPoint<SubscriptionPurchaseSuccessPlugin> {
                override fun getPlugins(): Collection<SubscriptionPurchaseSuccessPlugin> = plugins
            },
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenPurchaseSucceedsThenPluginsAreInvoked() = runTest {
        observer().onCreate(mock())
        advanceUntilIdle()

        purchaseState.emit(CurrentPurchase.Success(isFreeTrial = false))
        advanceUntilIdle()

        assertEquals(1, plugin.invocations)
    }

    @Test
    fun whenOnePluginThrowsThenTheOthersStillRun() = runTest {
        observer(plugins = listOf(ThrowingPlugin(), plugin)).onCreate(mock())
        advanceUntilIdle()

        purchaseState.emit(CurrentPurchase.Success(isFreeTrial = false))
        advanceUntilIdle()

        assertEquals(1, plugin.invocations)
    }

    @Test
    fun whenAPluginThrowsThenALaterPurchaseStillInvokesPlugins() = runTest {
        observer(plugins = listOf(ThrowingPlugin(), plugin)).onCreate(mock())
        advanceUntilIdle()

        purchaseState.emit(CurrentPurchase.Success(isFreeTrial = false))
        advanceUntilIdle()
        purchaseState.emit(CurrentPurchase.Success(isFreeTrial = false))
        advanceUntilIdle()

        assertEquals(2, plugin.invocations)
    }

    @Test
    fun whenPurchaseStateIsAnythingElseThenPluginsAreNotInvoked() = runTest {
        observer().onCreate(mock())
        advanceUntilIdle()

        listOf(
            CurrentPurchase.PreFlowInProgress,
            CurrentPurchase.PreFlowFinished,
            CurrentPurchase.InProgress,
            CurrentPurchase.Waiting,
            CurrentPurchase.Recovered,
            CurrentPurchase.Canceled,
            CurrentPurchase.Failure("boom"),
        ).forEach { purchaseState.emit(it) }
        advanceUntilIdle()

        assertEquals(0, plugin.invocations)
    }
}
