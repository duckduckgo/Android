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

package com.duckduckgo.user.agent.impl

import android.webkit.WebSettings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandHintFeature
import com.duckduckgo.user.agent.impl.remoteconfig.ClientBrandHintFeatureSettingsRepository
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.never
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RealClientBrandHintProviderTest {

    private lateinit var webViewFeatureMock: MockedStatic<WebViewFeature>
    private lateinit var webSettingsCompatMock: MockedStatic<WebSettingsCompat>

    private val toggle: Toggle = mock()
    private val clientBrandHintFeature: ClientBrandHintFeature = mock()
    private val repository: ClientBrandHintFeatureSettingsRepository = mock()
    private val settings: WebSettings = mock()

    private val testee = RealClientBrandHintProvider(clientBrandHintFeature, repository)

    @Before
    fun setUp() {
        webViewFeatureMock = mockStatic(WebViewFeature::class.java)
        webSettingsCompatMock = mockStatic(WebSettingsCompat::class.java)
        webViewFeatureMock.`when`<Boolean> { WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA) }.thenReturn(true)
        whenever(clientBrandHintFeature.self()).thenReturn(toggle)
        whenever(toggle.isEnabled()).thenReturn(true)
    }

    @After
    fun tearDown() {
        webSettingsCompatMock.close()
        webViewFeatureMock.close()
    }

    @Test
    fun whenReadingMetadataThrowsUnsupportedOperationThenSetDefaultDoesNotCrashAndSkipsBranding() {
        webSettingsCompatMock.`when`<UserAgentMetadata> { WebSettingsCompat.getUserAgentMetadata(settings) }
            .thenThrow(UnsupportedOperationException("form factors not supported"))

        testee.setDefault(settings)

        webSettingsCompatMock.verify({ WebSettingsCompat.setUserAgentMetadata(eq(settings), any()) }, never())
    }

    @Test
    fun whenReadingMetadataSucceedsThenSetDefaultAppliesBranding() {
        webSettingsCompatMock.`when`<UserAgentMetadata> { WebSettingsCompat.getUserAgentMetadata(settings) }
            .thenReturn(UserAgentMetadata.Builder().build())

        testee.setDefault(settings)

        webSettingsCompatMock.verify { WebSettingsCompat.setUserAgentMetadata(eq(settings), any()) }
    }
}
