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

package com.duckduckgo.browsermode.impl

import android.annotation.SuppressLint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.webkit.WebViewFeature
import com.duckduckgo.common.test.CoroutineTestRule
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic

@RunWith(AndroidJUnit4::class)
@SuppressLint("DenyListedApi")
class RealFireModeAvailabilityTest {

    @get:Rule val coroutineRule = CoroutineTestRule()

    private lateinit var webViewFeatureMock: MockedStatic<WebViewFeature>

    private val testee = RealFireModeAvailability(
        coroutineRule.testDispatcherProvider,
        coroutineRule.testScope,
    )

    @Before
    fun setUp() {
        webViewFeatureMock = mockStatic(WebViewFeature::class.java)
        setMultiProfileSupported(true)
        setDeleteBrowsingDataSupported(true)
    }

    @After
    fun tearDown() {
        webViewFeatureMock.close()
    }

    @Test
    fun `is available when both WebView features are supported`() {
        assertTrue(testee.isAvailable())
    }

    @Test
    fun `is unavailable when MULTI_PROFILE is unsupported`() {
        setMultiProfileSupported(false)

        assertFalse(testee.isAvailable())
    }

    @Test
    fun `is unavailable when DELETE_BROWSING_DATA is unsupported`() {
        setDeleteBrowsingDataSupported(false)

        assertFalse(testee.isAvailable())
    }

    @Test
    fun `availability is frozen on first computation even if WebView support changes later`() {
        setMultiProfileSupported(false)
        assertFalse(testee.isAvailable())

        setMultiProfileSupported(true)
        assertFalse(testee.isAvailable())
    }

    private fun setMultiProfileSupported(supported: Boolean) {
        webViewFeatureMock.`when`<Boolean> {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        }.thenReturn(supported)
    }

    private fun setDeleteBrowsingDataSupported(supported: Boolean) {
        webViewFeatureMock.`when`<Boolean> {
            WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA)
        }.thenReturn(supported)
    }
}
