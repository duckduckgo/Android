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
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
import com.duckduckgo.subscriptions.api.Subscriptions
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class NetpVpnAccessRevokedDialogMonitorTest {
    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var betaEndStore: BetaEndStore

    @Mock
    private lateinit var netPWaitlistRepository: NetPWaitlistRepository

    @Mock
    private lateinit var betaEndedDialog: BetaEndedDialog

    @Mock
    private lateinit var accessRevokedDialog: AccessRevokedDialog

    @Mock
    private lateinit var subscriptions: Subscriptions

    private lateinit var netpVpnAccessRevokedDialogMonitor: NetpVpnAccessRevokedDialogMonitor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        netpVpnAccessRevokedDialogMonitor = NetpVpnAccessRevokedDialogMonitor(
            networkProtectionRepository,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
            betaEndStore,
            netPWaitlistRepository,
            betaEndedDialog,
            accessRevokedDialog,
            subscriptions,
        )
    }

    @Test
    fun whenMonitorIsCreatedThenStoreParticipatedInBetaValue() {
        verify(betaEndStore).storeUserParticipatedInBeta(false)
    }

    @Test
    fun whenUserParticipatedInBetaAndPrivacyProInactiveAndDialogNotShownThenShowBetaEndDialog() = runTest {
        whenever(networkProtectionRepository.vpnAccessRevoked).thenReturn(true)
        whenever(betaEndStore.betaEndDialogShown()).thenReturn(false)
        whenever(betaEndStore.didParticipateInBeta()).thenReturn(true)
        whenever(subscriptions.isEnabled()).thenReturn(true)

        netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

        verify(betaEndedDialog).show(any())
        verify(betaEndStore).showBetaEndDialog()
        verify(networkProtectionRepository).vpnAccessRevoked = false
        verifyNoInteractions(accessRevokedDialog)
    }

    @Test
    fun whenDialogAlreadyNotShownThenDontShowAnyDialog() = runTest {
        whenever(networkProtectionRepository.vpnAccessRevoked).thenReturn(false)
        whenever(betaEndStore.betaEndDialogShown()).thenReturn(true)
        whenever(betaEndStore.didParticipateInBeta()).thenReturn(true)
        whenever(subscriptions.isEnabled()).thenReturn(true)

        netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

        verifyNoInteractions(betaEndedDialog)
        verifyNoInteractions(accessRevokedDialog)
    }

    @Test
    fun whenPrivacyProNotActiveThenDontShowAnyDialog() = runTest {
        whenever(networkProtectionRepository.vpnAccessRevoked).thenReturn(false)
        whenever(betaEndStore.betaEndDialogShown()).thenReturn(false)
        whenever(betaEndStore.didParticipateInBeta()).thenReturn(true)
        whenever(subscriptions.isEnabled()).thenReturn(false)

        netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

        verifyNoInteractions(betaEndedDialog)
        verifyNoInteractions(accessRevokedDialog)
    }

    @Test
    fun whenUserNotInBetaThenDontShowAnyDialog() = runTest {
        whenever(networkProtectionRepository.vpnAccessRevoked).thenReturn(false)
        whenever(betaEndStore.betaEndDialogShown()).thenReturn(false)
        whenever(betaEndStore.didParticipateInBeta()).thenReturn(false)
        whenever(subscriptions.isEnabled()).thenReturn(true)

        netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

        verifyNoInteractions(betaEndedDialog)
        verifyNoInteractions(accessRevokedDialog)
    }

    @Test
    fun whenUserNotInBetaAndVPNAccessRevokedThenShowAccessRevokedDialog() = runTest {
        whenever(networkProtectionRepository.vpnAccessRevoked).thenReturn(true)
        whenever(betaEndStore.betaEndDialogShown()).thenReturn(false)
        whenever(betaEndStore.didParticipateInBeta()).thenReturn(false)
        whenever(subscriptions.isEnabled()).thenReturn(true)

        netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

        verifyNoInteractions(betaEndedDialog)
        verify(accessRevokedDialog).show(any())
    }
}
