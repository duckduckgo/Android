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
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class DeviceAttributeMatcherTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val appProperties: AppProperties = mock()

    private val testee = DeviceAttributeMatcher(appBuildConfig, appProperties)

    @Test
    fun whenDeviceMatchesLocaleThenReturnMatch() {
        givenDeviceProperties(locale = Locale.US)

        val result = testee.evaluate(
            MatchingAttribute.Locale(value = listOf("en-US"))
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceMatchesAnyLocaleThenReturnMatch() {
        givenDeviceProperties(locale = Locale.US)

        val result = testee.evaluate(
            MatchingAttribute.Locale(value = listOf("fr-FR", "fr-CA", "en-US"))
        )

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceDoesNotMatchLocaleThenReturnFail() {
        givenDeviceProperties(locale = Locale.FRANCE)

        val result = testee.evaluate(
            MatchingAttribute.Locale(value = listOf("en_US"))
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenLocaleMatchingAttributeIsEmptyThenReturnFail() {
        givenDeviceProperties(locale = Locale.US)

        val result = testee.evaluate(
            MatchingAttribute.Locale(value = listOf(""))
        )

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceSameAsOsApiLevelThenReturnMatch() {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(MatchingAttribute.Api(value = 21))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceDifferentAsOsApiLevelThenReturnFail() {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(MatchingAttribute.Api(value = 19))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceMatchesOsApiLevelThenReturnMatch() {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(MatchingAttribute.Api(min = 19))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceMatchesOsApiLevelRangeThenReturnMatch() {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(MatchingAttribute.Api(min = 19, max = 23))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceDoesNotMatchesOsApiLevelRangeThenReturnMatch() {
        givenDeviceProperties(apiLevel = 23)

        val result = testee.evaluate(MatchingAttribute.Api(min = 19, max = 21))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceDoesNotMatchOsApiLevelThenReturnFail() {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(MatchingAttribute.Api(max = 19))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenOsApiMatchingAttributeEmptyThenReturnFail() {
        givenDeviceProperties(apiLevel = 21)

        val result = testee.evaluate(MatchingAttribute.Api())

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceWVSameAsWVVersionThenReturnMatch() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(value = "96.0.4664.104"))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceWVDifferentAsWVVersionThenReturnMatch() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(value = "96.0.4664.105"))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceWVLowerThanMaxWVVersionThenReturnMatch() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(max = "96.0.4665.101"))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceWVGreaterThanMaxWVVersionThenReturnFail() {
        givenDeviceProperties(webView = "96.0.4665.0")

        val result = testee.evaluate(MatchingAttribute.WebView(max = "96.0.4664.101"))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceWVGreaterThanMinWVVersionThenReturnMatch() {
        givenDeviceProperties(webView = "96.0.4665.104")

        val result = testee.evaluate(MatchingAttribute.WebView(min = "96.0.4664.101"))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceWVLowerThanMinWVVersionThenReturnFail() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(min = "96.1.4664.104"))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceMatchesWebViewMaxVersionThenReturnMatch() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(max = "96.0.4664.104"))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceMatchesWebViewMinVersionThenReturnMatch() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(min = "96.0.4664.104"))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceWVMatchesMaxSimplifiedWVVersionThenReturnMatch() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(max = "96"))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceWVMatchesMinSimplifiedWVVersionThenReturnMatch() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(min = "96"))

        assertEquals(EvaluationResult.Match, result)
    }

    @Test
    fun whenDeviceWVGreaterThanMaxSimplifiedWVVersionThenReturnFail() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(max = "95"))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceWVLowerThanMinSimplifiedWVVersionThenReturnFail() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView(min = "97"))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceDoesNotProvideWVVersionsThenReturnFail() {
        givenDeviceProperties(webView = "")

        val result = testee.evaluate(MatchingAttribute.WebView(min = "97"))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceWVVersionHasUnknownFormatThenReturnFail() {
        givenDeviceProperties(webView = "test93.91.0")

        val result = testee.evaluate(MatchingAttribute.WebView(min = "91"))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceWVVersionHasUnknownFormatInBetweenThenReturnFail() {
        givenDeviceProperties(webView = "93.91.test.0")

        val result = testee.evaluate(MatchingAttribute.WebView(min = "91"))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenDeviceWVVersionHasUnknownFormatAtEndThenReturnFail() {
        givenDeviceProperties(webView = "93.91.0.test")

        val result = testee.evaluate(MatchingAttribute.WebView(min = "91"))

        assertEquals(EvaluationResult.Fail, result)
    }

    @Test
    fun whenEmptyWebViewMatchingAttributeThenReturnFail() {
        givenDeviceProperties(webView = "96.0.4664.104")

        val result = testee.evaluate(MatchingAttribute.WebView())

        assertEquals(EvaluationResult.Fail, result)
    }

    private fun givenDeviceProperties(
        locale: Locale = Locale.getDefault(),
        apiLevel: Int = Build.VERSION.SDK_INT,
        webView: String = ""
    ) {
        whenever(appBuildConfig.deviceLocale).thenReturn(locale)
        whenever(appBuildConfig.sdkInt).thenReturn(apiLevel)
        whenever(appProperties.webView()).thenReturn(webView)
    }
}
