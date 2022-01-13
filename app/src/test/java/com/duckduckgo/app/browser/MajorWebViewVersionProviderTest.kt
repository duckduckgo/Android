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

package com.duckduckgo.app.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class MajorWebViewVersionProviderTest {
    private val webViewVersionSource: WebViewVersionSource = mock()
    private val testee = DefaultWebViewVersionProvider(
        webViewVersionSource,
    )

    @Test
    fun whenWebViewVersionIsEmptyThenReturnUnknownFullVersion() {
        whenever(webViewVersionSource.get()).thenReturn("")

        assertEquals("unknown", testee.getFullVersion())
    }

    @Test
    fun whenWebViewVersionIsAvailableThenReturnFullVersion() {
        whenever(webViewVersionSource.get()).thenReturn("91.1.12.1234.423")

        assertEquals("91.1.12.1234.423", testee.getFullVersion())
    }

    @Test
    fun whenWebViewVersionIsBlankThenReturnFullVersion() {
        whenever(webViewVersionSource.get()).thenReturn("    ")

        assertEquals("unknown", testee.getFullVersion())
    }

    @Test
    fun whenWebViewVersionIsEmptyThenReturnUnknownMajorVersion() {
        whenever(webViewVersionSource.get()).thenReturn("")

        assertEquals("unknown", testee.getMajorVersion())
    }

    @Test
    fun whenWebViewVersionAvailableThenReturnMajorVersionOnly() {
        whenever(webViewVersionSource.get()).thenReturn("91.1.12.1234.423")

        assertEquals("91", testee.getMajorVersion())
    }

    @Test
    fun whenWebViewVersionHasNonNumericValuesThenReturnMajorVersionOnly() {
        whenever(webViewVersionSource.get()).thenReturn("59.amazon-webview-v59-3071.3071.125.462")

        assertEquals("59", testee.getMajorVersion())
    }

    @Test
    fun whenWebViewVersionHasNoValidDelimiterThenReturnUnknownMajorVersion() {
        whenever(webViewVersionSource.get()).thenReturn("37%20%281448693564-arm%29")

        assertEquals("unknown", testee.getMajorVersion())
    }

    @Test
    fun whenWebViewVersionHasNonNumericMajorThenReturnUnknownMajorVersion() {
        whenever(webViewVersionSource.get()).thenReturn("37%20%28eng.jenkinswh-arm64%29")

        assertEquals("unknown", testee.getMajorVersion())
    }

    @Test
    fun whenWebViewVersionStartsWithDelimiterThenReturnUnknownMajorVersion() {
        whenever(webViewVersionSource.get()).thenReturn(".91.1.12.1234.423")

        assertEquals("unknown", testee.getMajorVersion())
    }

    @Test
    fun whenWebViewVersionIsBlankThenReturnUnknownMajorVersion() {
        whenever(webViewVersionSource.get()).thenReturn("    ")

        assertEquals("unknown", testee.getMajorVersion())
    }
}
