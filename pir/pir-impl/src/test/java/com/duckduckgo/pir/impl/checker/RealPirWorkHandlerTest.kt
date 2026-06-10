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
import com.duckduckgo.pir.impl.notifications.PirNotificationManager
import com.duckduckgo.pir.impl.scan.PirScanScheduler
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent.CancellationReason
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
    private val pirRepository: PirRepository = mock()
    private val pirNotificationManager: PirNotificationManager = mock()
    private val pirScanWideEvent: PirScanWideEvent = mock()

    private lateinit var pirWorkHandler: RealPirWorkHandler

    @Before
    fun setUp() = runTest {
        whenever(pirRemoteFeatures.pirBeta()).thenReturn(pirBetaToggle)
        whenever(pirRepository.isRepositoryAvailable()).thenReturn(true)

        pirWorkHandler = RealPirWorkHandler(
            pirRemoteFeatures = pirRemoteFeatures,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            subscriptions = subscriptions,
            context = context,
            pirScanScheduler = pirScanScheduler,
            pirRepository = pirRepository,
            pirNotificationManager = pirNotificationManager,
            pirScanWideEvent = pirScanWideEvent,
        )
    }

    @Test
    fun whenPirBetaDisabledThenCanRunPirDisabledWithFeatureDisabled() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(false)

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.FEATURE_DISABLED), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenSubscriptionStatusIsUnknownThenCanRunPirDisabledWithSubscriptionExpired() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.UNKNOWN))
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf()))

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenPirBetaEnabledAndPirEntitledAndAutoRenewableThenCanRunPirEnabled() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Enabled, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledAndNotAutoRenewableThenCanRunPirEnabled() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.NOT_AUTO_RENEWABLE))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Enabled, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledAndGracePeriodThenCanRunPirEnabled() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.GRACE_PERIOD))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Enabled, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButUnknownStatusThenCanRunPirDisabledWithSubscriptionExpired() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.UNKNOWN))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButInactiveStatusThenCanRunPirDisabledWithSubscriptionExpired() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.INACTIVE))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButExpiredStatusThenCanRunPirDisabledWithSubscriptionExpired() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.EXPIRED))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButWaitingStatusThenCanRunPirDisabledWithSubscriptionExpired() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.WAITING))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndAutoRenewableButNotPirEntitledThenCanRunPirDisabledWithEntitlementLost() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.NetP))) // Different product
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.ENTITLEMENT_LOST), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndAutoRenewableButNoEntitlementsThenCanRunPirDisabledWithEntitlementLost() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(emptyList()))
            whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.ENTITLEMENT_LOST), awaitItem())
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
                assertEquals(PirEligibility.Enabled, awaitItem())

                // Remove PIR entitlement
                entitlementFlow.value = emptyList()
                assertEquals(PirEligibility.Disabled(DisabledReason.ENTITLEMENT_LOST), awaitItem())

                // Add PIR entitlement back
                entitlementFlow.value = listOf(Product.PIR)
                assertEquals(PirEligibility.Enabled, awaitItem())

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
            assertEquals(PirEligibility.Enabled, awaitItem())

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
    fun whenRepositoryNotAvailableThenCanRunPirDisabledWithRepositoryUnavailable() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(subscriptions.getEntitlementStatus()).thenReturn(flowOf(listOf(Product.PIR)))
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
        whenever(pirRepository.isRepositoryAvailable()).thenReturn(false)

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.REPOSITORY_UNAVAILABLE), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenCancelWorkThenFinalizesWideEventAndStopsForegroundServicesAndCancelsWorkManager() = runTest {
        pirWorkHandler.cancelWork(CancellationReason.PROFILE_DELETED)

        // Wide event must be finalized before the services hosting the run are torn down
        verify(pirScanWideEvent).onWorkCancelled(CancellationReason.PROFILE_DELETED)
        // Verify that stopService is called for each of the 2 foreground services
        verify(context, times(2)).stopService(any<Intent>())
        verify(pirScanScheduler).cancelScheduledScans(context)
        verify(pirNotificationManager).cancelNotifications()
    }
}
