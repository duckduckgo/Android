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

package com.duckduckgo.networkprotection.impl.reconnect

import android.content.Context
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.alerts.reconnect.NetPReconnectNotifications
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.NotReconnecting
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.Reconnecting
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ReconnectStatus.ReconnectingFailed
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ServerDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class NetpVpnConnectivityLossListenerPluginTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Mock
    private lateinit var reconnectNotifications: NetPReconnectNotifications

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var netpPixels: NetworkProtectionPixels
    private lateinit var repository: NetworkProtectionRepository
    private lateinit var testee: NetpVpnConnectivityLossListenerPlugin

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        repository = FakeNetworkProtectionRepository()
        testee = NetpVpnConnectivityLossListenerPlugin(
            vpnFeaturesRegistry,
            repository,
            reconnectNotifications,
            context,
            coroutineRule.testDispatcherProvider,
        ) { netpPixels }
    }

    @Test
    fun whenOnVpnConnectivityLossCalledOnceThenInitiateRecoveryByReconnecting() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        testee.onVpnConnectivityLoss(coroutineRule.testScope)

        assertEquals(Reconnecting, repository.reconnectStatus)
        verify(netpPixels).reportVpnConnectivityLoss()
        verify(reconnectNotifications).clearNotifications()
        verify(reconnectNotifications).launchReconnectingNotification(context)
        verify(vpnFeaturesRegistry).refreshFeature(NetPVpnFeature.NETP_VPN)
    }

    @Test
    fun whenOnVpnConnectivityLossCalledTwiceThenGiveUpRecoveringAndUnregisterNetp() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        repository.reconnectAttemptCount = 1
        testee.onVpnConnectivityLoss(coroutineRule.testScope)

        assertEquals(ReconnectingFailed, repository.reconnectStatus)
        assertEquals(0, repository.reconnectAttemptCount)
        verify(netpPixels).reportVpnReconnectFailed()
        verify(reconnectNotifications).clearNotifications()
        verify(reconnectNotifications).launchReconnectionFailedNotification(context)
        verify(vpnFeaturesRegistry).unregisterFeature(NetPVpnFeature.NETP_VPN)
    }

    @Test
    fun whenOnVpnConnectedAndNotReconnectingThenDoNothing() {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        repository.reconnectStatus = NotReconnecting
        testee.onVpnConnected(coroutineRule.testScope)

        assertEquals(NotReconnecting, repository.reconnectStatus)
        verifyNoInteractions(reconnectNotifications)
        verifyNoInteractions(netpPixels)
    }

    @Test
    fun whenOnVpnConnectedAndReconnectingFailedThenResetStatusToNotReconnecting() {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        repository.reconnectStatus = ReconnectingFailed
        testee.onVpnConnected(coroutineRule.testScope)

        assertEquals(NotReconnecting, repository.reconnectStatus)
        verifyNoInteractions(reconnectNotifications)
        verifyNoInteractions(netpPixels)
    }

    @Test
    fun whenOnVpnConnectedAndReconnectingThenRecoverSuccessfully() {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        repository.reconnectStatus = Reconnecting
        testee.onVpnConnected(coroutineRule.testScope)

        assertEquals(NotReconnecting, repository.reconnectStatus)
        assertEquals(0, repository.reconnectAttemptCount)
        verify(reconnectNotifications).clearNotifications()
        verify(reconnectNotifications).launchReconnectedNotification(context)
        verifyNoInteractions(netpPixels)
    }

    @Test
    fun whenNetpVpnFeatureIsNotRegisteredThenOnVpnConnectedDoesNothing() {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(false)

        testee.onVpnConnected(coroutineRule.testScope)

        assertEquals(NotReconnecting, repository.reconnectStatus)
        assertEquals(0, repository.reconnectAttemptCount)
        verifyNoInteractions(reconnectNotifications)
        verifyNoInteractions(netpPixels)
    }

    @Test
    fun whenNetpVpnFeatureIsNotRegisteredThenOnVpnConnectivityLossDoesNothing() {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(false)

        testee.onVpnConnectivityLoss(coroutineRule.testScope)

        assertEquals(NotReconnecting, repository.reconnectStatus)
        assertEquals(0, repository.reconnectAttemptCount)
        verifyNoInteractions(reconnectNotifications)
        verifyNoInteractions(netpPixels)
    }

    private class FakeNetworkProtectionRepository : NetworkProtectionRepository {
        private var _reconnectStatus: ReconnectStatus? = null
        private var _reconnectAttemptCount: Int = 0

        override var reconnectStatus: ReconnectStatus
            get() = _reconnectStatus ?: NotReconnecting
            set(value) {
                _reconnectStatus = value
            }
        override var reconnectAttemptCount: Int
            get() = _reconnectAttemptCount
            set(value) {
                _reconnectAttemptCount = value
            }
        override var privateKey: String?
            get() = null
            set(_) {}
        override var enabledTimeInMillis: Long
            get() = -1L
            set(_) {}
        override var serverDetails: ServerDetails?
            get() = null
            set(_) {}
    }
}
