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

package com.duckduckgo.networkprotection.impl.rekey

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature.NETP_VPN
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import java.lang.RuntimeException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class RealNetPRekeyerTest {
    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Mock
    private lateinit var networkProtectionPixels: NetworkProtectionPixels

    @Mock
    private lateinit var wgServerApi: WgServerApi

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    private var isDeviceLocked = false

    private lateinit var testee: RealNetPRekeyer

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        runBlocking {
            whenever(wgServerApi.registerPublicKey(any())).thenReturn(
                WgServerApi.WgServerData(
                    serverName = "",
                    publicKey = "key",
                    publicEndpoint = "endpoint",
                    address = "1.1.1.2",
                    location = null,
                    gateway = "1.1.1.1",
                ),
            )
        }

        testee = RealNetPRekeyer(
            networkProtectionRepository,
            vpnFeaturesRegistry,
            networkProtectionPixels,
            "name",
            wgServerApi,
            appBuildConfig,
            { isDeviceLocked },
        )
    }

    @Test
    fun `do not rekey if time since last rekey is less than 24h`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)

        testee.doRekey()

        assertNoRekey()
    }

    @Test
    fun `do not rekey if registering new key fails`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(networkProtectionRepository.lastPrivateKeyUpdateTimeInMillis)
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        whenever(wgServerApi.registerPublicKey(any())).thenThrow(RuntimeException(""))

        testee.doRekey()

        assertNoRekey()
    }

    @Test
    fun `do not rekey device is not locked`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(networkProtectionRepository.lastPrivateKeyUpdateTimeInMillis)
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = false

        testee.doRekey()

        assertNoRekey()
    }

    @Test
    fun `do not rekey if not internal build and forced rekey`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(networkProtectionRepository.lastPrivateKeyUpdateTimeInMillis)
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        testee.forceRekey()

        assertNoRekey()
    }

    @Test
    fun `do rekey if internal build and forced rekey`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(networkProtectionRepository.lastPrivateKeyUpdateTimeInMillis)
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.forceRekey()

        assertRekey()
    }

    @Test
    fun `do not rekey if internal build and forced rekey but vpn disabled`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(false)
        whenever(networkProtectionRepository.lastPrivateKeyUpdateTimeInMillis)
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.forceRekey()

        assertNoRekey()
    }

    @Test
    fun `do rekey if production build`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(networkProtectionRepository.lastPrivateKeyUpdateTimeInMillis)
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        testee.doRekey()

        assertRekey()
    }

    @Test
    fun `do not rekey if production build but vpn disabled`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(false)
        whenever(networkProtectionRepository.lastPrivateKeyUpdateTimeInMillis)
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        testee.doRekey()

        assertNoRekey()
    }

    private suspend fun assertNoRekey() {
        verify(networkProtectionRepository, never()).privateKey = any()
        verify(vpnFeaturesRegistry, never()).refreshFeature(NETP_VPN)
        verify(networkProtectionPixels, never()).reportRekeyCompleted()
    }

    private suspend fun assertRekey() {
        verify(networkProtectionRepository).privateKey = any()
        verify(vpnFeaturesRegistry).refreshFeature(NETP_VPN)
        verify(networkProtectionPixels).reportRekeyCompleted()
    }
}
