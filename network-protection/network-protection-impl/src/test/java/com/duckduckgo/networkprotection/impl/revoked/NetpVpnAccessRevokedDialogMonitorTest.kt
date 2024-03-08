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
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.waitlist.FakeNetPRemoteFeatureFactory
import com.duckduckgo.networkprotection.impl.waitlist.NetPRemoteFeature
import com.duckduckgo.networkprotection.impl.waitlist.store.NetPWaitlistRepository
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

    private lateinit var netpVpnAccessRevokedDialogMonitor: NetpVpnAccessRevokedDialogMonitor
    private val netPRemoteFeature: NetPRemoteFeature = FakeNetPRemoteFeatureFactory.create()

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        netpVpnAccessRevokedDialogMonitor = NetpVpnAccessRevokedDialogMonitor(
            networkProtectionRepository,
            coroutineTestRule.testScope,
            coroutineTestRule.testDispatcherProvider,
            betaEndStore,
            netPWaitlistRepository,
            netPRemoteFeature,
            betaEndedDialog,
            accessRevokedDialog,
        )
    }

    @Test
    fun whenMonitorIsCreatedThenStoreParticipatedInBetaValue() {
        verify(betaEndStore).storeUserParticipatedInBeta(false)
    }

    @Test
    fun whenUserParticipatedInBetaAndWaitlistInactiveAndDialogNotShownThenShowBetaEndDialog() = runTest {
        whenever(networkProtectionRepository.vpnAccessRevoked).thenReturn(true)
        whenever(betaEndStore.betaEndDialogShown()).thenReturn(false)
        whenever(betaEndStore.didParticipateInBeta()).thenReturn(true)
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

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
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

        netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

        verifyNoInteractions(betaEndedDialog)
        verifyNoInteractions(accessRevokedDialog)
    }

    @Test
    fun whenWaitlistStillActiveThenDontShowAnyDialog() = runTest {
        whenever(networkProtectionRepository.vpnAccessRevoked).thenReturn(false)
        whenever(betaEndStore.betaEndDialogShown()).thenReturn(false)
        whenever(betaEndStore.didParticipateInBeta()).thenReturn(true)
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = true))

        netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

        verifyNoInteractions(betaEndedDialog)
        verifyNoInteractions(accessRevokedDialog)
    }

    @Test
    fun whenUserNotInBetaThenDontShowAnyDialog() = runTest {
        whenever(networkProtectionRepository.vpnAccessRevoked).thenReturn(false)
        whenever(betaEndStore.betaEndDialogShown()).thenReturn(false)
        whenever(betaEndStore.didParticipateInBeta()).thenReturn(false)
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

        netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

        verifyNoInteractions(betaEndedDialog)
        verifyNoInteractions(accessRevokedDialog)
    }

    @Test
    fun whenUserNotInBetaAndVPNAccessRevokedThenShowAccessRevokedDialog() {
        whenever(networkProtectionRepository.vpnAccessRevoked).thenReturn(true)
        whenever(betaEndStore.betaEndDialogShown()).thenReturn(false)
        whenever(betaEndStore.didParticipateInBeta()).thenReturn(false)
        netPRemoteFeature.waitlistBetaActive().setEnabled(Toggle.State(enable = false))

        netpVpnAccessRevokedDialogMonitor.onActivityResumed(mock())

        verifyNoInteractions(betaEndedDialog)
        verify(accessRevokedDialog).show(any())
    }
}
