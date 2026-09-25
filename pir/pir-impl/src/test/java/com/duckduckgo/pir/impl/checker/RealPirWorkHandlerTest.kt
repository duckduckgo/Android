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
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirRepository
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent
import com.duckduckgo.pir.impl.wideevents.PirScanWideEvent.CancellationReason
import com.duckduckgo.subscriptions.api.Product
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import com.duckduckgo.subscriptions.api.model.Entitlement
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
    private val freemiumToggle: Toggle = mock()
    private val pirFreemiumDataStore: PirFreemiumDataStore = mock()
    private val pirEntitlement = Entitlement(name = "plus", product = Product.PIR.value)
    private val netPEntitlement = Entitlement(name = "plus", product = Product.NetP.value)

    private lateinit var pirWorkHandler: RealPirWorkHandler

    @Before
    fun setUp() = runTest {
        whenever(pirRemoteFeatures.pirBeta()).thenReturn(pirBetaToggle)
        whenever(pirRemoteFeatures.freemium()).thenReturn(freemiumToggle)
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
            pirFreemiumDataStore = pirFreemiumDataStore,
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
        givenSubscription(SubscriptionStatus.UNKNOWN)

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenPirBetaEnabledAndPirEntitledAndAutoRenewableThenCanRunPirEnabled() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            givenSubscription(SubscriptionStatus.AUTO_RENEWABLE, setOf(pirEntitlement))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledAndNotAutoRenewableThenCanRunPirEnabled() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            givenSubscription(SubscriptionStatus.NOT_AUTO_RENEWABLE, setOf(pirEntitlement))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledAndGracePeriodThenCanRunPirEnabled() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            givenSubscription(SubscriptionStatus.GRACE_PERIOD, setOf(pirEntitlement))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButUnknownStatusThenCanRunPirDisabledWithSubscriptionExpired() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            givenSubscription(SubscriptionStatus.UNKNOWN, setOf(pirEntitlement))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButInactiveStatusThenCanRunPirDisabledWithSubscriptionExpired() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            givenSubscription(SubscriptionStatus.INACTIVE, setOf(pirEntitlement))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButExpiredStatusThenCanRunPirDisabledWithSubscriptionExpired() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            givenSubscription(SubscriptionStatus.EXPIRED, setOf(pirEntitlement))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndPirEntitledButWaitingStatusThenCanRunPirDisabledWithSubscriptionExpired() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            givenSubscription(SubscriptionStatus.WAITING, setOf(pirEntitlement))

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndAutoRenewableButNotPirEntitledThenCanRunPirDisabledWithEntitlementLost() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            givenSubscription(SubscriptionStatus.AUTO_RENEWABLE, setOf(netPEntitlement)) // Different product

            pirWorkHandler.canRunPir().test {
                assertEquals(PirEligibility.Disabled(DisabledReason.ENTITLEMENT_LOST), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenPirBetaEnabledAndAutoRenewableButNoEntitlementsThenCanRunPirDisabledWithEntitlementLost() =
        runTest {
            whenever(pirBetaToggle.isEnabled()).thenReturn(true)
            givenSubscription(SubscriptionStatus.AUTO_RENEWABLE)

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
            whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

            val entitlementFlow = MutableStateFlow(setOf(pirEntitlement))
            whenever(subscriptions.getEntitlements()).thenReturn(entitlementFlow)
            whenever(subscriptions.getCurrentEntitlements()).thenAnswer { entitlementFlow.value }

            pirWorkHandler.canRunPir().test {
                // Initially enabled
                assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT), awaitItem())

                // Remove PIR entitlement
                entitlementFlow.value = emptySet()
                assertEquals(PirEligibility.Disabled(DisabledReason.ENTITLEMENT_LOST), awaitItem())

                // Add PIR entitlement back
                entitlementFlow.value = setOf(pirEntitlement)
                assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whenSameValueEmittedMultipleTimesThenDistinctUntilChangedWorksCorrectly() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        val entitlementFlow = MutableStateFlow(setOf(pirEntitlement))
        whenever(subscriptions.getEntitlements()).thenReturn(entitlementFlow)
        whenever(subscriptions.getCurrentEntitlements()).thenAnswer { entitlementFlow.value }

        pirWorkHandler.canRunPir().test {
            // Initially enabled
            assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT), awaitItem())

            // Emit same value multiple times - should only get one emission due to distinctUntilChanged
            entitlementFlow.value = setOf(pirEntitlement)
            entitlementFlow.value = setOf(pirEntitlement)
            entitlementFlow.value = setOf(pirEntitlement)

            // No new emissions should occur since value hasn't changed
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenRepositoryNotAvailableThenCanRunPirDisabledWithRepositoryUnavailable() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        givenSubscription(SubscriptionStatus.AUTO_RENEWABLE, setOf(pirEntitlement))
        whenever(pirRepository.isRepositoryAvailable()).thenReturn(false)

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.REPOSITORY_UNAVAILABLE), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFreemiumEnabledAndActivatedAndNoSubscriptionThenCanRunPirEnabledWithScanOnly() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(freemiumToggle.isEnabled()).thenReturn(true)
        whenever(pirFreemiumDataStore.didActivate).thenReturn(true)
        givenSubscription(SubscriptionStatus.INACTIVE)

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_ONLY), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFreemiumEnabledAndActivatedButAlsoPirEntitledThenCanRunPirEnabledWithScanAndOptOut() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(freemiumToggle.isEnabled()).thenReturn(true)
        whenever(pirFreemiumDataStore.didActivate).thenReturn(true)
        givenSubscription(SubscriptionStatus.AUTO_RENEWABLE, setOf(pirEntitlement))

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFreemiumEnabledAndActivatedAndSubscribedButNotPirEntitledThenCanRunPirDisabledWithEntitlementLost() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(freemiumToggle.isEnabled()).thenReturn(true)
        whenever(pirFreemiumDataStore.didActivate).thenReturn(true)
        givenSubscription(SubscriptionStatus.AUTO_RENEWABLE, setOf(netPEntitlement))

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.ENTITLEMENT_LOST), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFreemiumDisabledButActivatedAndNoSubscriptionThenCanRunPirDisabledWithSubscriptionExpired() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(freemiumToggle.isEnabled()).thenReturn(false)
        whenever(pirFreemiumDataStore.didActivate).thenReturn(true)
        givenSubscription(SubscriptionStatus.INACTIVE)

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFreemiumEnabledButNotActivatedAndNoSubscriptionThenCanRunPirDisabledWithSubscriptionExpired() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(freemiumToggle.isEnabled()).thenReturn(true)
        whenever(pirFreemiumDataStore.didActivate).thenReturn(false)
        givenSubscription(SubscriptionStatus.INACTIVE)

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenFreemiumEnabledAndActivatedButRepositoryNotAvailableThenCanRunPirDisabledWithRepositoryUnavailable() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(freemiumToggle.isEnabled()).thenReturn(true)
        whenever(pirFreemiumDataStore.didActivate).thenReturn(true)
        whenever(pirRepository.isRepositoryAvailable()).thenReturn(false)
        givenSubscription(SubscriptionStatus.INACTIVE)

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.REPOSITORY_UNAVAILABLE), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenPirBetaDisabledAndFreemiumActivatedThenCanRunPirDisabledWithFeatureDisabled() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(false)
        whenever(freemiumToggle.isEnabled()).thenReturn(true)
        whenever(pirFreemiumDataStore.didActivate).thenReturn(true)

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.FEATURE_DISABLED), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun whenReplayedStateIsStaleAndStoreHasPirSubscriptionThenCanRunPirEnabledWithScanAndOptOut() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        whenever(freemiumToggle.isEnabled()).thenReturn(true)
        whenever(pirFreemiumDataStore.didActivate).thenReturn(true)
        // The flows replay what this process cached before the user subscribed in another process
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.UNKNOWN))
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(emptySet()))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getCurrentEntitlements()).thenReturn(setOf(pirEntitlement))

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenReplayedStateIsStaleAndStoreHasExpiredSubscriptionThenCanRunPirDisabledWithSubscriptionExpired() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        // The flows replay what this process cached before the subscription expired
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(SubscriptionStatus.AUTO_RENEWABLE))
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(pirEntitlement)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.EXPIRED)
        whenever(subscriptions.getCurrentEntitlements()).thenReturn(emptySet())

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Disabled(DisabledReason.SUBSCRIPTION_EXPIRED), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun whenTriggerChangesButStoredStateResolvesToSameEligibilityThenCanRunPirDoesNotReEmit() = runTest {
        whenever(pirBetaToggle.isEnabled()).thenReturn(true)
        val statusFlow = MutableStateFlow(SubscriptionStatus.UNKNOWN)
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(statusFlow)
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(setOf(pirEntitlement)))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)
        whenever(subscriptions.getCurrentEntitlements()).thenReturn(setOf(pirEntitlement))

        pirWorkHandler.canRunPir().test {
            assertEquals(PirEligibility.Enabled(PirRunMode.SCAN_AND_OPT_OUT), awaitItem())

            // A stale cached value followed by the refreshed one, as a new collection can see
            statusFlow.value = SubscriptionStatus.AUTO_RENEWABLE

            expectNoEvents()
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

    private suspend fun givenSubscription(
        status: SubscriptionStatus,
        entitlements: Set<Entitlement> = emptySet(),
    ) {
        whenever(subscriptions.getSubscriptionStatusFlow()).thenReturn(flowOf(status))
        whenever(subscriptions.getEntitlements()).thenReturn(flowOf(entitlements))
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(status)
        whenever(subscriptions.getCurrentEntitlements()).thenReturn(entitlements)
    }
}
