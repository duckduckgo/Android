/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.subscription

import app.cash.turbine.test
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState.NetPAccessState.InBeta
import com.duckduckgo.networkprotection.api.NetworkProtectionAccessState.NetPAccessState.NotUnlocked
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.ACTIVE
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.EXPIRED
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.INACTIVE
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.SIGNED_OUT
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
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
class NetworkProtectionAccessStateImplTest {
    private lateinit var testee: NetworkProtectionAccessStateImpl

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

        testee = NetworkProtectionAccessStateImpl(
            netPWaitlistRepository,
            networkProtectionState,
            coroutineTestRule.testDispatcherProvider,
            netpSubscriptionManager,
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
    fun whenSubscriptionsEnabledAndVpnInactiveAndNetpDisabledThenReturnNotUnlocked() = runTest {
        whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(INACTIVE)
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
            verifyNoInteractions(networkProtectionRepository)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnExpiredAndNetpDisabledThenReturnNotUnlocked() = runTest {
        whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(EXPIRED)
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
            verifyNoInteractions(networkProtectionRepository)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnSignedOutAndNetpDisabledThenReturnNotUnlocked() = runTest {
        whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(SIGNED_OUT)
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
            verifyNoInteractions(networkProtectionRepository)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnInactiveAndNetpEnabledThenReturnNotUnlockedAndResetVpnState() = runTest {
        whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(INACTIVE)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
            verify(networkProtectionState).stop()
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnExpiredAndNetpEnabledThenReturnNotUnlockedAndResetVpnState() = runTest {
        whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(EXPIRED)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
            verify(networkProtectionState).stop()
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnSignedOutAndNetpEnabledThenReturnNotUnlockedAndResetVpnState() = runTest {
        whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(SIGNED_OUT)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        testee.getState().also {
            assertEquals(NotUnlocked, it)
            verify(networkProtectionState).stop()
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnActiveAndHasAuthTokenAndNotAcceptedTermsReturnInBetaFalse() = runTest {
        whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(ACTIVE)
        whenever(netPWaitlistRepository.didAcceptWaitlistTerms()).thenReturn(false)
        whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
        testee.getState().also {
            assertEquals(InBeta(false), it)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnActiveAndAcceptedTermsReturnInBetaTrue() = runTest {
        whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(ACTIVE)
        whenever(netPWaitlistRepository.didAcceptWaitlistTerms()).thenReturn(true)
        whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
        testee.getState().also {
            assertEquals(InBeta(true), it)
        }
    }

    @Test
    fun whenSubscriptionsDisabledThenReturnFlowEmitsNotUnlocked() = runTest {
        whenever(subscriptions.isEnabled()).thenReturn(false)
        testee.getStateFlow().test {
            assertEquals(NotUnlocked, expectMostRecentItem())
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnInactiveAndNetpDisabledThenReturnFlowEmitsNotUnlocked() = runTest {
        whenever(netpSubscriptionManager.vpnStatus()).thenReturn(flowOf(INACTIVE))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        testee.getStateFlow().test {
            assertEquals(NotUnlocked, expectMostRecentItem())
            verifyNoInteractions(networkProtectionRepository)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnExpiredAndNetpDisabledThenReturnFlowEmitsNotUnlocked() = runTest {
        whenever(netpSubscriptionManager.vpnStatus()).thenReturn(flowOf(EXPIRED))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        testee.getStateFlow().test {
            assertEquals(NotUnlocked, expectMostRecentItem())
            verifyNoInteractions(networkProtectionRepository)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnSingedOutAndNetpDisabledThenReturnFlowEmitsNotUnlocked() = runTest {
        whenever(netpSubscriptionManager.vpnStatus()).thenReturn(flowOf(SIGNED_OUT))
        whenever(networkProtectionState.isEnabled()).thenReturn(false)
        testee.getStateFlow().test {
            assertEquals(NotUnlocked, expectMostRecentItem())
            verifyNoInteractions(networkProtectionRepository)
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnInactiveAndNetpEnabledThenReturnFlowEmitNotUnlockedAndResetVpnState() = runTest {
        whenever(netpSubscriptionManager.vpnStatus()).thenReturn(flowOf(INACTIVE))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        testee.getStateFlow().test {
            assertEquals(NotUnlocked, expectMostRecentItem())
            verify(networkProtectionState).stop()
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnExpiredAndNetpEnabledThenReturnFlowEmitNotUnlockedAndResetVpnState() = runTest {
        whenever(netpSubscriptionManager.vpnStatus()).thenReturn(flowOf(EXPIRED))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        testee.getStateFlow().test {
            assertEquals(NotUnlocked, expectMostRecentItem())
            verify(networkProtectionState).stop()
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnSignedOutAndNetpEnabledThenReturnFlowEmitNotUnlockedAndResetVpnState() = runTest {
        whenever(netpSubscriptionManager.vpnStatus()).thenReturn(flowOf(SIGNED_OUT))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        testee.getStateFlow().test {
            assertEquals(NotUnlocked, expectMostRecentItem())
            verify(networkProtectionState).stop()
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnActiveAndHasAuthTokenAndNotAcceptedTermsReturnFlowEmitInBetaFalse() = runTest {
        whenever(netpSubscriptionManager.vpnStatus()).thenReturn(flowOf(ACTIVE))
        whenever(netPWaitlistRepository.didAcceptWaitlistTerms()).thenReturn(false)
        whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
        testee.getStateFlow().test {
            assertEquals(InBeta(false), expectMostRecentItem())
        }
    }

    @Test
    fun whenSubscriptionsEnabledAndVpnActiveAndAcceptedTermsReturnFlowEmitInBetaTrue() = runTest {
        whenever(netpSubscriptionManager.vpnStatus()).thenReturn(flowOf(ACTIVE))
        whenever(netPWaitlistRepository.didAcceptWaitlistTerms()).thenReturn(true)
        whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
        testee.getStateFlow().test {
            assertEquals(InBeta(true), expectMostRecentItem())
        }
    }
}
