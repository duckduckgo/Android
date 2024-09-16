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

package com.duckduckgo.remote.messaging.impl.matchers

import android.os.Build
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.AppProperties
import com.duckduckgo.remote.messaging.impl.models.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DeviceAttributeMatcherTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val appProperties: AppProperties = mock()

    private val testee = DeviceAttributeMatcher(appBuildConfig, appProperties)

    @Test
    fun whenDeviceMatchesLocaleThenReturnMatch() = runTest {
        givenDeviceProperties(locale = java.util.Locale.US)

        val result = testee.evaluate(
            Locale(value = listOf("en-US")),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceMatchesAnyLocaleThenReturnMatch() = runTest {
        givenDeviceProperties(locale = java.util.Locale.US)

        val result = testee.evaluate(
            Locale(value = listOf("fr-FR", "fr-CA", "en-US")),
        )

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceDoesNotMatchLocaleThenReturnFail() = runTest {
        givenDeviceProperties(locale = java.util.Locale.FRANCE)

        val result = testee.evaluate(
            Locale(value = listOf("en_US")),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenLocaleMatchingAttributeIsEmptyThenReturnFail() = runTest {
        givenDeviceProperties(locale = java.util.Locale.US)

        val result = testee.evaluate(
            Locale(value = listOf("")),
        )

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceSameAsOsApiLevelThenReturnMatch() = runTest {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(Api(value = 21))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceDifferentAsOsApiLevelThenReturnFail() = runTest {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(Api(value = 19))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceMatchesOsApiLevelThenReturnMatch() = runTest {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(Api(min = 19))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceMatchesOsApiLevelRangeThenReturnMatch() = runTest {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(Api(min = 19, max = 23))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceDoesNotMatchesOsApiLevelRangeThenReturnMatch() = runTest {
        givenDeviceProperties(apiLevel = 23)

        val result = testee.evaluate(Api(min = 19, max = 21))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceDoesNotMatchOsApiLevelThenReturnFail() = runTest {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(Api(max = 19))

        assertEquals(false, result)
    }

    @Test
    fun whenOsApiMatchingAttributeEmptyThenReturnFail() = runTest {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(Api())

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceWVSameAsWVVersionThenReturnMatch() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(value = "96.0.4664.104"))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceWVDifferentAsWVVersionThenReturnMatch() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(value = "96.0.4664.105"))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceWVLowerThanMaxWVVersionThenReturnMatch() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(max = "96.0.4665.101"))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceWVGreaterThanMaxWVVersionThenReturnFail() = runTest {
        givenDeviceProperties(webView = "96.0.4665.0")

        val result = testee.evaluate(WebView(max = "96.0.4664.101"))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceWVGreaterThanMinWVVersionThenReturnMatch() = runTest {
        givenDeviceProperties(webView = "96.0.4665.104")

        val result = testee.evaluate(WebView(min = "96.0.4664.101"))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceWVLowerThanMinWVVersionThenReturnFail() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(min = "96.1.4664.104"))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceMatchesWebViewMaxVersionThenReturnMatch() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(max = "96.0.4664.104"))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceMatchesWebViewMinVersionThenReturnMatch() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(min = "96.0.4664.104"))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceWVMatchesMaxSimplifiedWVVersionThenReturnMatch() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(max = "96"))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceWVMatchesMinSimplifiedWVVersionThenReturnMatch() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(min = "96"))

        assertEquals(true, result)
    }

    @Test
    fun whenDeviceWVGreaterThanMaxSimplifiedWVVersionThenReturnFail() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(max = "95"))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceWVLowerThanMinSimplifiedWVVersionThenReturnFail() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView(min = "97"))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceDoesNotProvideWVVersionsThenReturnFail() = runTest {
        givenDeviceProperties(webView = "")

        val result = testee.evaluate(WebView(min = "97"))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceWVVersionHasUnknownFormatThenReturnFail() = runTest {
        givenDeviceProperties(webView = "test93.91.0")

        val result = testee.evaluate(WebView(min = "91"))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceWVVersionHasUnknownFormatInBetweenThenReturnFail() = runTest {
        givenDeviceProperties(webView = "93.91.test.0")

        val result = testee.evaluate(WebView(min = "91"))

        assertEquals(false, result)
    }

    @Test
    fun whenDeviceWVVersionHasUnknownFormatAtEndThenReturnFail() = runTest {
        givenDeviceProperties(webView = "93.91.0.test")

        val result = testee.evaluate(WebView(min = "91"))

        assertEquals(false, result)
    }

    @Test
    fun whenEmptyWebViewMatchingAttributeThenReturnFail() = runTest {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(WebView())

        assertEquals(false, result)
    }

    private fun givenDeviceProperties(
        locale: java.util.Locale = java.util.Locale.getDefault(),
        apiLevel: Int = Build.VERSION.SDK_INT,
        webView: String = "",
    ) {
        whenever(appBuildConfig.deviceLocale).thenReturn(locale)
        whenever(appBuildConfig.sdkInt).thenReturn(apiLevel)
        whenever(appProperties.webView()).thenReturn(webView)
    }
}
