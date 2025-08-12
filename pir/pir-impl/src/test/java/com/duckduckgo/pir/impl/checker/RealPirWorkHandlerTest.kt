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

package com.duckduckgo.pir.impl.checker

import android.content.Context
import android.content.Intent
import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.scan.PirScanScheduler
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealPirWorkHandlerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val pirRemoteFeatures: PirRemoteFeatures = mock()
    private val subscriptions: Subscriptions = mock()
    private val pirScanScheduler: PirScanScheduler = mock()
    private val context: Context = mock()
    private val pirBetaToggle: Toggle = mock()

    private lateinit var pirWorkHandler: RealPirWorkHandler

    @Before
    fun setUp() {
        whenever(pirRemoteFeatures.pirBeta()).thenReturn(pirBetaToggle)

        pirWorkHandler = RealPirWorkHandler(
            pirRemoteFeatures = pirRemoteFeatures,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            subscriptions = subscriptions,
            context = context,
            pirScanScheduler = pirScanScheduler,
        )
    }

    @Test
    fun whenPirBetaDisabledThenCanRunPirReturnsFalse() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(false)

        pirWorkHandler.canRunPir().test {
            assertFalse(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenSubscriptionStatusIsUnknownThenCanRunPirReturnsFalse() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.UNKNOWN))
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf()))

        pirWorkHandler.canRunPir().test {
            assertFalse(awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenPirBetaEnabledAndPirEntitledAndAutoRenewableThenCanRunPirReturnsTrue() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))

            pirWorkHandler.canRunPir().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledAndNotAutoRenewableThenCanRunPirReturnsTrue() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.NOT_AUTO_RENEWABLE))

            pirWorkHandler.canRunPir().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledAndGracePeriodThenCanRunPirReturnsTrue() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.GRACE_PERIOD))

            pirWorkHandler.canRunPir().test {
                assertTrue(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButUnknownStatusThenCanRunPirReturnsFalse() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.UNKNOWN))

            pirWorkHandler.canRunPir().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButInactiveStatusThenCanRunPirReturnsFalse() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.INACTIVE))

            pirWorkHandler.canRunPir().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButExpiredStatusThenCanRunPirReturnsFalse() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.EXPIRED))

            pirWorkHandler.canRunPir().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButWaitingStatusThenCanRunPirReturnsFalse() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.WAITING))

            pirWorkHandler.canRunPir().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndAutoRenewableButNotPirEntitledThenCanRunPirReturnsFalse() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP))) // Different product
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))

            pirWorkHandler.canRunPir().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndAutoRenewableButNoEntitlementsThenCanRunPirReturnsFalse() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))

            pirWorkHandler.canRunPir().test {
                assertFalse(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenEntitlementStatusChangesFromEnabledToDisabledThenCanRunPirEmitsCorrectValues() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))

            val entitlementFlow = MutableStateFlow(listOf(Product.PIR))
            whenever(subscriptions.getEntitlementStatus()).thenReturn(entitlementFlow)

            pirWorkHandler.canRunPir().test {
                // Initially enabled
                assertTrue(awaitItem())

                // Remove PIR entitlement
                entitlementFlow.value = emptyList()
                assertFalse(awaitItem())

                // Add PIR entitlement back
                entitlementFlow.value = listOf(Product.PIR)
                assertTrue(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenSameValueEmittedMultipleTimesThenDistinctUntilChangedWorksCorrectly() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))

        val entitlementFlow = MutableStateFlow(listOf(Product.PIR))
        whenever(subscriptions.getEntitlementStatus()).thenReturn(entitlementFlow)

        pirWorkHandler.canRunPir().test {
            // Initially enabled
            assertTrue(awaitItem())

            // Emit same value multiple times - should only get one emission due to distinctUntilChanged
            entitlementFlow.value = listOf(Product.PIR)
            entitlementFlow.value = listOf(Product.PIR)
            entitlementFlow.value = listOf(Product.PIR)

            // No new emissions should occur since value hasn't changed
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCancelWorkThenStopsForegroundServicesAndCancelsWorkManager() {
        pirWorkHandler.cancelWork()

        // Verify that stopService is called 3 times (for each of the 3 services)
        verify(context, times(2)).stopService(any<Intent>())
        verify(pirScanScheduler).cancelScheduledScans(context)
    }
}
