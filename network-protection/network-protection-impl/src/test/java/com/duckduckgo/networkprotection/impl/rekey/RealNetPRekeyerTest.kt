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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature.NETP_VPN
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
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
    private lateinit var workManager: WorkManager

    private lateinit var testee: RealNetPRekeyer

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealNetPRekeyer(
            workManager,
            networkProtectionRepository,
            vpnFeaturesRegistry,
            networkProtectionPixels,
            "name",
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
    }

    @Test
    fun whenNetPIsNotRegisteredThenDoRekeyShouldNotRefreshFeature() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(false)

        testee.doRekey()

        verify(networkProtectionRepository).privateKey = null
        verify(vpnFeaturesRegistry, never()).refreshFeature(any())
        verify(workManager).cancelUniqueWork("DAILY_NETP_REKEY_TAG")
        verify(networkProtectionPixels).reportRekeyCompleted()
    }

    @Test
    fun whenNetPIsRegisteredThenDoRekeyShouldRefreshFeature() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)

        testee.doRekey()

        verify(networkProtectionRepository).privateKey = null
        verify(vpnFeaturesRegistry).refreshFeature(NETP_VPN)
        verify(networkProtectionPixels).reportRekeyCompleted()
        verifyNoInteractions(workManager)
    }
}
