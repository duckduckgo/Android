/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.pir.impl.common

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.pir.impl.PirRemoteFeatures
import com.duckduckgo.pir.impl.models.Broker
import com.duckduckgo.user.agent.api.UserAgentProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealPirBrokerUserAgentResolverTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val mockUserAgentProvider: UserAgentProvider = mock()
    private val mockPirRemoteFeatures: PirRemoteFeatures = mock()
    private val mockCustomUserAgentToggle: Toggle = mock()

    private lateinit var testee: RealPirBrokerUserAgentResolver

    @Before
    fun setUp() {
        whenever(mockUserAgentProvider.userAgent(anyOrNull(), any())).thenReturn(DEFAULT_UA)
        whenever(mockPirRemoteFeatures.customWebViewUserAgent()).thenReturn(mockCustomUserAgentToggle)
        whenever(mockCustomUserAgentToggle.isEnabled()).thenReturn(true)

        testee = RealPirBrokerUserAgentResolver(
            userAgentProvider = mockUserAgentProvider,
            pirRemoteFeatures = mockPirRemoteFeatures,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenBrokerHasUserAgentAndFeatureEnabledThenReturnsBrokerUserAgent() = runTest {
        assertEquals(BROKER_UA, testee.resolve(broker(userAgent = BROKER_UA)))
    }

    @Test
    fun whenBrokerHasUserAgentAndFeatureDisabledThenReturnsDefaultUserAgent() = runTest {
        whenever(mockCustomUserAgentToggle.isEnabled()).thenReturn(false)

        assertEquals(DEFAULT_UA, testee.resolve(broker(userAgent = BROKER_UA)))
    }

    @Test
    fun whenBrokerHasNoUserAgentThenReturnsDefaultUserAgent() = runTest {
        assertEquals(DEFAULT_UA, testee.resolve(broker(userAgent = null)))
    }

    @Test
    fun whenBrokerHasBlankUserAgentThenReturnsDefaultUserAgent() = runTest {
        assertEquals(DEFAULT_UA, testee.resolve(broker(userAgent = " ")))
    }

    private fun broker(userAgent: String?) = Broker(
        name = "test-broker",
        fileName = "test-broker.json",
        url = "https://test-broker.com",
        version = "1.0",
        parent = null,
        addedDatetime = 1000L,
        removedAt = 0L,
        userAgent = userAgent,
    )

    private companion object {
        const val DEFAULT_UA = "default-pir-user-agent"
        const val BROKER_UA = "Mozilla/5.0 (X11; Linux x86_64; rv:142.0) Gecko/20100101 Firefox/142.0"
    }
}
