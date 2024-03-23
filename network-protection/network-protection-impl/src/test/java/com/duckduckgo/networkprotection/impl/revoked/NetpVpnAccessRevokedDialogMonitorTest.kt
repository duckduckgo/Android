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

package com.duckduckgo.networkprotection.impl.revoked

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.ACTIVE
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.EXPIRED
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.INACTIVE
import com.duckduckgo.networkprotection.impl.subscription.NetpSubscriptionManager.VpnStatus.SIGNED_OUT
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class NetpVpnAccessRevokedDialogMonitorTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var netpSubscriptionManager: NetpSubscriptionManager

    @Mock
    private lateinit var betaEndedDialog: BetaEndedDialog

    @Mock
    private lateinit var accessRevokedDialog: AccessRevokedDialog

    @Mock
    private lateinit var subscriptions: Subscriptions

    @Mock
    private lateinit var netPWaitlistRepository: NetPWaitlistRepository

    @Mock lateinit var networkProtectionState: NetworkProtectionState

    private lateinit var netpVpnAccessRevokedDialogMonitor: NetpVpnAccessRevokedDialogMonitor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        runBlocking { whenever(networkProtectionState.isOnboarded()) }.thenReturn(true)

        netpVpnAccessRevokedDialogMonitor = NetpVpnAccessRevokedDialogMonitor(
            netpSubscriptionManager,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
            betaEndedDialog,
            accessRevokedDialog,
            subscriptions,
            netPWaitlistRepository,
            networkProtectionState,
        )
    }

    @Test
    fun whenUserParticipatedInBetaAndPrivacyProActiveAndDialogNotShownThenShowBetaEndDialog() {
        coroutineTestRule.testScope.launch {
            whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(INACTIVE)
            whenever(betaEndedDialog.shouldShowDialog()).thenReturn(true)
            whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
            whenever(subscriptions.isEnabled()).thenReturn(true)

            netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

            verify(betaEndedDialog).show(any())
            verifyNoInteractions(accessRevokedDialog)
        }
    }

    @Test
    fun whenDialogAlreadyNotShownThenDontShowAnyDialog() {
        coroutineTestRule.testScope.launch {
            whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(ACTIVE)
            whenever(betaEndedDialog.shouldShowDialog()).thenReturn(false)
            whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
            whenever(subscriptions.isEnabled()).thenReturn(true)

            netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

            verify(betaEndedDialog, never()).show(any())
            verify(accessRevokedDialog).clearIsShown()
        }
    }

    @Test
    fun whenPrivacyProNotActiveThenDontShowAnyDialog() {
        coroutineTestRule.testScope.launch {
            whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(ACTIVE)
            whenever(betaEndedDialog.shouldShowDialog()).thenReturn(true)
            whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn("123")
            whenever(subscriptions.isEnabled()).thenReturn(false)

            netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

            verify(betaEndedDialog, never()).show(any())
            verify(accessRevokedDialog).clearIsShown()
        }
    }

    @Test
    fun whenUserNotInBetaThenDontShowAnyDialog() {
        coroutineTestRule.testScope.launch {
            whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(ACTIVE)
            whenever(betaEndedDialog.shouldShowDialog()).thenReturn(true)
            whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn(null)
            whenever(subscriptions.isEnabled()).thenReturn(true)

            netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

            verify(betaEndedDialog, never()).show(any())
            verify(accessRevokedDialog).clearIsShown()
        }
    }

    @Test
    fun whenUserNotInBetaAndVPNInactiveThenClearShownAccessRevokedDialog() {
        coroutineTestRule.testScope.launch {
            whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(INACTIVE)
            whenever(betaEndedDialog.shouldShowDialog()).thenReturn(true)
            whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn(null)
            whenever(subscriptions.isEnabled()).thenReturn(true)

            netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

            verify(betaEndedDialog, never()).show(any())
            verify(accessRevokedDialog).clearIsShown()
        }
    }

    @Test
    fun whenUserNotInBetaAndVPNSignedOutThenClearShownAccessRevokedDialog() {
        coroutineTestRule.testScope.launch {
            whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(SIGNED_OUT)
            whenever(betaEndedDialog.shouldShowDialog()).thenReturn(true)
            whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn(null)
            whenever(subscriptions.isEnabled()).thenReturn(true)

            netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

            verify(betaEndedDialog, never()).show(any())
            verify(accessRevokedDialog).clearIsShown()
        }
    }

    @Test
    fun whenUserNotInBetaAndVPNActiveThenClearShownAccessRevokedDialog() {
        coroutineTestRule.testScope.launch {
            whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(ACTIVE)
            whenever(betaEndedDialog.shouldShowDialog()).thenReturn(true)
            whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn(null)
            whenever(subscriptions.isEnabled()).thenReturn(true)

            netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

            verify(betaEndedDialog, never()).show(any())
            verify(accessRevokedDialog).clearIsShown()
        }
    }

    @Test
    fun whenUserNotInBetaAndVPNExpiredThenShowAccessRevokedDialog() {
        coroutineTestRule.testScope.launch {
            whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(EXPIRED)
            whenever(betaEndedDialog.shouldShowDialog()).thenReturn(true)
            whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn(null)
            whenever(subscriptions.isEnabled()).thenReturn(true)

            netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

            verify(betaEndedDialog, never()).show(any())
            verify(accessRevokedDialog).showOnce(any())
        }
    }

    @Test
    fun whenUserNotInBetaAndVPNExpiredAndVpnNotOnboardedThenNoDialog() {
        coroutineTestRule.testScope.launch {
            whenever(networkProtectionState.isOnboarded()).thenReturn(false)
            whenever(netpSubscriptionManager.getVpnStatus()).thenReturn(EXPIRED)
            whenever(betaEndedDialog.shouldShowDialog()).thenReturn(true)
            whenever(netPWaitlistRepository.getAuthenticationToken()).thenReturn(null)
            whenever(subscriptions.isEnabled()).thenReturn(true)

            netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

            verify(betaEndedDialog, never()).show(any())
            verify(accessRevokedDialog, never()).showOnce(any())
            verify(accessRevokedDialog.clearIsShown())
        }
    }
}
