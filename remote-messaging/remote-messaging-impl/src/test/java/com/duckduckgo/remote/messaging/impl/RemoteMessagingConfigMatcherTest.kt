/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl

import android.os.Build
import com.duckduckgo.browser.api.DeviceProperties
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aMediumMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aSmallMessage
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import timber.log.Timber
import java.util.*
import kotlin.math.min

class RemoteMessagingConfigMatcherTest {

    private val deviceProperties: DeviceProperties = mock()
    private val testee = RemoteMessagingConfigMatcher(deviceProperties)

    private val printlnTree = object: Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            println("$tag: $message")
        }
    }

    //TODO: move this to a rule
    @Before
    fun before() {
        Timber.plant(printlnTree)
    }

    @After
    fun after() {
        Timber.uproot(printlnTree)
    }

    @Test
    fun whenEmptyConfigThenReturnNull() {
        val message = testee.evaluate(RemoteConfig(emptyList(), emptyMap()))

        assertNull(message)
    }

    @Test
    fun whenNoMatchingRulesThenReturnFirstMessage() {
        val remoteMessage = aSmallMessage()
        val message = testee.evaluate(RemoteConfig(listOf(remoteMessage), emptyMap()))

        assertEquals(remoteMessage, message)
    }

    @Test
    fun whenDeviceMatchesLocaleThenReturnFirstMatch() {
        givenDeviceProperties(locale = Locale.US)
        val expectedMessage = aMediumMessage(matchingRules = listOf(1))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage),
                rules = mapOf(
                    Pair(1, listOf(MatchingAttribute.Locale(value = listOf("en_US"))))
                )
            )
        )

        assertEquals(expectedMessage, message)
    }

    @Test
    fun whenDeviceDoesNotMatchLocaleThenReturnNull() {
        givenDeviceProperties(locale = Locale.FRANCE)
        val expectedMessage = aMediumMessage(matchingRules = listOf(1))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage),
                rules = mapOf(
                    Pair(1, listOf(MatchingAttribute.Locale(value = listOf("en_US"))))
                )
            )
        )

        assertNull(message)
    }

    @Test
    fun whenDeviceMatchesOsApiLevelThenReturnFirstMatch() {
        givenDeviceProperties(apiLevel = 21)
        val expectedMessage = aMediumMessage(matchingRules = listOf(1))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage),
                rules = mapOf(
                    Pair(1, listOf(MatchingAttribute.Api(min = 19)))
                )
            )
        )

        assertEquals(expectedMessage, message)
    }

    @Test
    fun whenDeviceDoesNotMatchOsApiLevelThenReturnNull() {
        givenDeviceProperties(apiLevel = 21)
        val expectedMessage = aMediumMessage(matchingRules = listOf(1))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage),
                rules = mapOf(
                    Pair(1, listOf(MatchingAttribute.Api(max = 19)))
                )
            )
        )

        assertNull(message)
    }

    @Test
    fun whenDeviceMatchesWebViewVersionThenReturnFirstMatch() {
        givenDeviceProperties(webView = "80")
        val expectedMessage = aMediumMessage(matchingRules = listOf(1))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage),
                rules = mapOf(
                    Pair(1, listOf(MatchingAttribute.WebView(max = "80")))
                )
            )
        )

        assertEquals(expectedMessage, message)
    }

    @Test
    fun whenDeviceDoesNotMatchWebViewVersionThenReturnNull() {
        givenDeviceProperties(webView = "80")
        val expectedMessage = aMediumMessage(matchingRules = listOf(1))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage),
                rules = mapOf(
                    Pair(1, listOf(MatchingAttribute.WebView(max = "70")))
                )
            )
        )

        assertNull(message)
    }

    @Test
    fun whenDeviceDoesNotMatchMessageRulesThenReturnNull() {
        givenDeviceProperties(
            locale = Locale.ENGLISH,
            apiLevel = 21,
            webView = "80"
        )
        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aSmallMessage(matchingRules = listOf(1)), aMediumMessage(matchingRules = listOf(1))),
                rules = mapOf(
                    Pair(1, listOf(MatchingAttribute.Api(max = 19)))
                )
            )
        )

        assertNull(message)
    }

    @Test
    fun whenThen() {
        val message = testee.evaluate(RemoteConfig(emptyList(), emptyMap()))
    }

    private fun givenDeviceProperties(
        locale: Locale = Locale.getDefault(),
        apiLevel: Int =  Build.VERSION.SDK_INT,
        webView: String = ""
    ) {
        whenever(deviceProperties.deviceLocale()).thenReturn(locale)
        whenever(deviceProperties.osApiLevel()).thenReturn(apiLevel)
        whenever(deviceProperties.webView()).thenReturn(webView)
    }
}
