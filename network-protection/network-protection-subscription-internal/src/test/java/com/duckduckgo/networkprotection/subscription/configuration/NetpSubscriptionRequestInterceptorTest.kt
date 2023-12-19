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

package com.duckduckgo.networkprotection.subscription.configuration

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL
import com.duckduckgo.appbuildconfig.api.BuildFlavor.PLAY
import com.duckduckgo.common.test.api.FakeChain
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.subscription.NetpSubscriptionManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class NetpSubscriptionRequestInterceptorTest {
    private lateinit var testee: NetpSubscriptionRequestInterceptor

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    @Mock
    private lateinit var subscriptionManager: NetpSubscriptionManager

    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = NetpSubscriptionRequestInterceptor(
            appBuildConfig,
            subscriptionManager,
            networkProtectionRepository,
        )
    }

    @Test
    fun whenUrlIsServersAndFlavorIsPlayThenOnlyAddTokenToHeader() = runTest {
        val fakeChain = FakeChain(url = "https://controller.netp.duckduckgo.com/servers")
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        whenever(subscriptionManager.getToken()).thenReturn("token123")

        testee.intercept(fakeChain).run {
            assertEquals("bearer ddg:token123", headers["Authorization"])
            assertFalse(headers.names().contains("NetP-Debug-Code"))
        }
    }

    @Test
    fun whenUrlIsLocationsAndFlavorIsPlayThenOnlyAddTokenToHeader() = runTest {
        val fakeChain = FakeChain(url = "https://controller.netp.duckduckgo.com/locations")
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        whenever(subscriptionManager.getToken()).thenReturn("token123")

        testee.intercept(fakeChain).run {
            assertEquals("bearer ddg:token123", headers["Authorization"])
            assertFalse(headers.names().contains("NetP-Debug-Code"))
        }
    }

    @Test
    fun whenUrlIsRegisterAndFlavorIsPlayThenOnlyAddTokenToHeader() = runTest {
        val fakeChain = FakeChain(url = "https://staging1.netp.duckduckgo.com/register")
        whenever(appBuildConfig.flavor).thenReturn(PLAY)
        whenever(subscriptionManager.getToken()).thenReturn("token123")

        testee.intercept(fakeChain).run {
            assertEquals("bearer ddg:token123", headers["Authorization"])
            assertFalse(headers.names().contains("NetP-Debug-Code"))
        }
    }

    @Test
    fun whenUrlIsNotNetPAndFlavorIsInternalThenDoNothingWithHeaders() = runTest {
        val fakeChain = FakeChain(url = "https://improving.duckduckgo.com/t/m_netp_ev_enabled_android_phone?atb=v336-7&appVersion=5.131.0&test=1")

        testee.intercept(fakeChain).run {
            assertNull(headers["Authorization"])
            assertFalse(headers.names().contains("NetP-Debug-Code"))
        }
    }

    @Test
    fun whenUrlIsNetPAndFlavorIsInternalThenAddTokenAndDebugCodeToHeader() = runTest {
        val fakeChain = FakeChain(url = "https://controller.netp.duckduckgo.com/servers")
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        whenever(subscriptionManager.getToken()).thenReturn("token123")

        testee.intercept(fakeChain).run {
            assertEquals("bearer ddg:token123", headers["Authorization"])
            assertTrue(headers.names().contains("NetP-Debug-Code"))
        }
    }

    @Test
    fun whenUrlIsNotNetpThenDoNothingWithVPNAccessRevoked() = runTest {
        val fakeChain = FakeChain(url = "https://improving.duckduckgo.com/t/m_netp_ev_enabled_android_phone?atb=v336-7&appVersion=5.131.0&test=1")

        testee.intercept(fakeChain)

        verifyNoInteractions(networkProtectionRepository)
    }

    @Test
    fun whenUrlIsNetPAndResponseCodeIs200ThenSetVPNAccessRevokedFalse() = runTest {
        val fakeChain = FakeChain(url = "https://controller.netp.duckduckgo.com/servers")
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        whenever(subscriptionManager.getToken()).thenReturn("token123")

        testee.intercept(fakeChain)

        verify(networkProtectionRepository).vpnAccessRevoked = false
    }

    @Test
    fun whenUrlIsNetPAndResponseCodeIs403ThenSetVPNAccessRevokedFalse() = runTest {
        val url = "https://controller.netp.duckduckgo.com/servers"
        val fakeChain = FakeChain(
            url = url,
            expectedResponseCode = 403,
        )
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        whenever(subscriptionManager.getToken()).thenReturn("token123")

        testee.intercept(fakeChain)

        verify(networkProtectionRepository).vpnAccessRevoked = true
    }
}
