/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.integration

import com.duckduckgo.app.global.api.FakeChain
import com.duckduckgo.mobile.android.vpn.integration.VpnNetworkStackVariantPixelInterceptor.Companion.PIXELS
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(Parameterized::class)
class VpnNetworkStackVariantPixelInterceptorTest(private val pixel: String) {

    private val vpnNetworkStackVariantStore: VpnNetworkStackVariantStore = mock()

    lateinit var interceptor: VpnNetworkStackVariantPixelInterceptor

    @Before
    fun setup() {

        whenever(vpnNetworkStackVariantStore.variant).thenReturn("variant")
        interceptor = VpnNetworkStackVariantPixelInterceptor(vpnNetworkStackVariantStore)
    }

    @Test
    fun whenPixelsForNetworkStackExperimentFiredThenAddNetwotkStackVariantName() {
        val response = interceptor.intercept(FakeChain("https://improving.duckduckgo.com/t/$pixel"))

        assertEquals("variant", response.request.url.queryParameter("networkLayer"))
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun testData(): List<String> {
            return PIXELS
        }
    }
}
