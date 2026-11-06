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
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.ELIGIBLE
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.NOT_ELIGIBLE
import com.duckduckgo.pir.impl.freemium.PirFreemiumState.USED
import com.duckduckgo.pir.impl.store.PirFreemiumDataStore
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult.MATCHES_FOUND
import com.duckduckgo.pir.impl.store.PirFreemiumFirstScanResult.NO_MATCHES
import com.duckduckgo.subscriptions.api.Product.ITR
import com.duckduckgo.subscriptions.api.Product.NetP
import com.duckduckgo.subscriptions.api.Product.PIR
import com.duckduckgo.subscriptions.api.Product.ROW_ITR
import com.duckduckgo.subscriptions.api.SubscriptionStatus
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealPirFreemiumTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val pirRemoteFeatures = FakeFeatureToggleFactory.create(PirRemoteFeatures::class.java)
    private val subscriptions: Subscriptions = mock()
    private val dataStore: PirFreemiumDataStore = mock()
    private val debugSettings: PirFreemiumDebugSettings = mock()

    private lateinit var testee: RealPirFreemium

    @Before
    fun setUp() = runTest {
        pirRemoteFeatures.freemium().setRawStoredState(State(enable = true))
        whenever(subscriptions.isSignedIn()).thenReturn(false)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(subscriptions.isEligible()).thenReturn(true)
        whenever(subscriptions.getPurchasableProducts()).thenReturn(setOf(NetP, PIR, ITR))
        whenever(dataStore.firstScanResult).thenReturn(null)

        testee = RealPirFreemium(
            pirRemoteFeatures = pirRemoteFeatures,
            subscriptions = subscriptions,
            pirFreemiumDataStore = dataStore,
            pirFreemiumDebugSettings = debugSettings,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenAllGatesPassAndNoScanCompletedThenEligible() = runTest {
        assertEquals(ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenFreemiumFlagIsOffThenNotEligible() = runTest {
        pirRemoteFeatures.freemium().setRawStoredState(State(enable = false))

        assertEquals(NOT_ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenUserCannotPurchaseSubscriptionThenNotEligible() = runTest {
        whenever(subscriptions.isEligible()).thenReturn(false)

        assertEquals(NOT_ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenOfferedPlansDoNotIncludePirThenNotEligible() = runTest {
        whenever(subscriptions.getPurchasableProducts()).thenReturn(setOf(NetP, ROW_ITR))

        assertEquals(NOT_ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenEligibilityForcedThenEligibleWherePirCannotBePurchased() = runTest {
        whenever(debugSettings.isEligibilityForced).thenReturn(true)
        whenever(subscriptions.isEligible()).thenReturn(false)
        whenever(subscriptions.getPurchasableProducts()).thenReturn(setOf(NetP, ROW_ITR))

        assertEquals(ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenEligibilityForcedAfterAFreeScanThenUsed() = runTest {
        whenever(debugSettings.isEligibilityForced).thenReturn(true)
        whenever(subscriptions.getPurchasableProducts()).thenReturn(setOf(NetP, ROW_ITR))
        whenever(dataStore.firstScanResult).thenReturn(NO_MATCHES)

        assertEquals(USED, testee.getPirFreemiumState())
    }

    @Test
    fun whenEligibilityForcedButUserHasSubscriptionThenNotEligible() = runTest {
        whenever(debugSettings.isEligibilityForced).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        assertEquals(NOT_ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenEligibilityForcedButFreemiumFlagIsOffThenNotEligible() = runTest {
        whenever(debugSettings.isEligibilityForced).thenReturn(true)
        pirRemoteFeatures.freemium().setRawStoredState(State(enable = false))

        assertEquals(NOT_ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenUserIsSignedInThenNotEligible() = runTest {
        whenever(subscriptions.isSignedIn()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        assertEquals(NOT_ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenSignedInWithNoSubscriptionThenStillEligible() = runTest {
        // A failed purchase leaves an account behind: SubscriptionsManager.purchase() creates one
        // before launching the Play billing flow and nothing removes it when that flow fails. The
        // user is signed in with no subscription, so the paid PIR row is hidden too — without this,
        // they lose every PIR entry point until app data is cleared.
        whenever(subscriptions.isSignedIn()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)

        assertEquals(ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenSignedInWithNoSubscriptionAfterAFreeScanThenResultsStillReachable() = runTest {
        whenever(subscriptions.isSignedIn()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.UNKNOWN)
        whenever(dataStore.firstScanResult).thenReturn(NO_MATCHES)

        assertEquals(USED, testee.getPirFreemiumState())
    }

    @Test
    fun whenSubscriptionIsLapsedThenNotEligible() = runTest {
        // Deliberate: freemium is not re-offered to someone who already had a subscription.
        listOf(SubscriptionStatus.EXPIRED, SubscriptionStatus.INACTIVE, SubscriptionStatus.WAITING).forEach { status ->
            whenever(subscriptions.isSignedIn()).thenReturn(true)
            whenever(subscriptions.getSubscriptionStatus()).thenReturn(status)

            assertEquals("expected NOT_ELIGIBLE for $status", NOT_ELIGIBLE, testee.getPirFreemiumState())
        }
    }

    @Test
    fun whenScanFoundMatchesThenUsed() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(MATCHES_FOUND)

        assertEquals(USED, testee.getPirFreemiumState())
    }

    @Test
    fun whenScanFoundNothingThenStillUsed() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(NO_MATCHES)

        assertEquals(USED, testee.getPirFreemiumState())
    }

    @Test
    fun whenScanCompletedButUserSubscribedSinceThenNotEligible() = runTest {
        whenever(dataStore.firstScanResult).thenReturn(MATCHES_FOUND)
        whenever(subscriptions.isSignedIn()).thenReturn(true)
        whenever(subscriptions.getSubscriptionStatus()).thenReturn(SubscriptionStatus.AUTO_RENEWABLE)

        assertEquals(NOT_ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenSubscriptionStatusCheckThrowsThenNotEligible() = runTest {
        whenever(subscriptions.getSubscriptionStatus()).thenThrow(RuntimeException("backend unavailable"))

        assertEquals(NOT_ELIGIBLE, testee.getPirFreemiumState())
    }

    @Test
    fun whenFirstScanResultReadThrowsThenNotEligible() = runTest {
        whenever(dataStore.firstScanResult).thenThrow(RuntimeException("corrupted preferences"))

        assertEquals(NOT_ELIGIBLE, testee.getPirFreemiumState())
    }
}
