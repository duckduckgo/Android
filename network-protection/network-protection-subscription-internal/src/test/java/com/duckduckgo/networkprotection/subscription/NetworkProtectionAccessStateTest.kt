/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.subscription

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionWaitlist.NetPWaitlistState.NotUnlocked
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class NetworkProtectionAccessStateTest {
    private lateinit var testee: NetworkProtectionAccessState

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var netPWaitlistRepository: NetPWaitlistRepository

    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    @Mock
    private lateinit var netpSubscriptionManager: NetpSubscriptionManager

    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var subscriptions: Subscriptions

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        runBlocking { whenever(subscriptions.isEnabled()) }.thenReturn(true)

        testee = NetworkProtectionAccessState(
            netPWaitlistRepository,
            networkProtectionState,
            coroutineTestRule.testDispatcherProvider,
            netpSubscriptionManager,
            networkProtectionRepository,
            subscriptions,
        )
    }

    @Test
    fun whenSubscriptionsDisabledThenReturnNotUnlocked() = runTest {
        whenever(subscriptions.isEnabled()).thenReturn(false)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndHasNoEntitlementAndNetpDisabledThenReturnNotUnlocked() = runTest {
        whenever(netpSubscriptionManager.hasValidEntitlement()).thenReturn(Result.success(false))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
            verifyNoInteractions(networkProtectionRepository)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndHasNoEntitlementAndNetpEnabledThenReturnNotUnlockedAndResetVpnState() = runTest {
        whenever(netpSubscriptionManager.hasValidEntitlement()).thenReturn(Result.success(false))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
            verify(networkProtectionRepository).vpnAccessRevoked = true
            verify(networkProtectionState).stop()
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndHasEntitlementAndHasAuthTokenAndNotAcceptedTermsReturnInBetaFalse() = runTest {
        whenever(netpSubscriptionManager.hasValidEntitlement()).thenReturn(Result.success(true))
        whenever(netPWaitlistRepository.didAcceptWaitlistTerms()).thenReturn(false)
        whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
        testee.getState().also {
            assertEquals(InBeta(false), it)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndHasEntitlementAndAcceptedTermsReturnInBetaTrue() = runTest {
        whenever(netpSubscriptionManager.hasValidEntitlement()).thenReturn(Result.success(true))
        whenever(netPWaitlistRepository.didAcceptWaitlistTerms()).thenReturn(true)
        whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
        testee.getState().also {
            assertEquals(InBeta(true), it)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndEntitlementCheckFailedReturnNotUnlocked() = runTest {
        whenever(netpSubscriptionManager.hasValidEntitlement()).thenReturn(Result.failure(RuntimeException()))
        whenever(netPWaitlistRepository.didAcceptWaitlistTerms()).thenReturn(true)
        whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
        testee.getState().also {
            assertEquals(NotUnlocked, it)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndEntitlementCheckFailedAndNetpHasNeverBeenEnabledReturnVerifySubscription() = runTest {
        whenever(netpSubscriptionManager.hasValidEntitlement()).thenReturn(Result.failure(RuntimeException()))
        whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn(null)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
        }
    }
}
