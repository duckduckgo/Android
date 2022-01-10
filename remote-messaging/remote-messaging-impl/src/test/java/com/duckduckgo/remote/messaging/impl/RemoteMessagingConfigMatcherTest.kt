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

import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aMediumMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aSmallMessage
import com.duckduckgo.remote.messaging.impl.matchers.AndroidAppAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.DeviceAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.Result
import com.duckduckgo.remote.messaging.impl.matchers.UserAttributeMatcher
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class RemoteMessagingConfigMatcherTest {

    private val deviceAttributeMatcher: DeviceAttributeMatcher = mock()
    private val androidAppAttributeMatcher: AndroidAppAttributeMatcher = mock()
    private val userAttributeMatcher: UserAttributeMatcher = mock()

    private val testee = RemoteMessagingConfigMatcher(deviceAttributeMatcher, androidAppAttributeMatcher, userAttributeMatcher)

    private val printlnTree = object : Timber.Tree() {
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
    fun whenEmptyConfigThenReturnNull() = runBlocking {
        val message = testee.evaluate(RemoteConfig(emptyList(), emptyMap()))

        assertNull(message)
    }

    @Test
    fun whenNoMatchingRulesThenReturnFirstMessage() = runBlocking {
        val remoteMessage = aSmallMessage()

        val message = testee.evaluate(RemoteConfig(listOf(remoteMessage), emptyMap()))

        assertEquals(remoteMessage, message)
    }

    @Test
    fun whenNotExistingRuleThenReturnMessage() = runBlocking {
        val remoteMessage = aSmallMessage(matchingRules = listOf(1))

        val message = testee.evaluate(RemoteConfig(listOf(remoteMessage), emptyMap()))

        assertEquals(remoteMessage, message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesThenReturnFirstMatch() = runBlocking {
        val matchingAttributes = listOf(MatchingAttribute.Api(max = 19))
        given(matchingAttributes = matchingAttributes)
        val rules = mapOf(Pair(1, matchingAttributes))
        val expectedMessage = aMediumMessage(matchingRules = rules.keys.toList())

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage),
                rules = rules
            )
        )

        assertEquals(expectedMessage, message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesForMultipleMessagesThenReturnFirstMatch() = runBlocking {
        val matchingAttributes = listOf(MatchingAttribute.Api(max = 19))
        given(matchingAttributes = matchingAttributes)
        val rules = mapOf(Pair(1, matchingAttributes))
        val expectedMessage = aMediumMessage(matchingRules = rules.keys.toList())

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage, aSmallMessage(matchingRules = rules.keys.toList())),
                rules = rules
            )
        )

        assertEquals(expectedMessage, message)
    }

    @Test
    fun whenDeviceDoesNotMatchMessageRulesThenReturnNull() = runBlocking {
        val failingAttributes = listOf(MatchingAttribute.Api(max = 19))
        given(failingAttributes = failingAttributes)
        val rules = mapOf(Pair(1, failingAttributes))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aSmallMessage(matchingRules = rules.keys.toList()), aMediumMessage(matchingRules = rules.keys.toList())),
                rules = rules
            )
        )

        assertNull(message)
    }

    @Test
    fun whenDeviceMatchesAnyRuleThenReturnFirstMatch() = runBlocking {
        val matchingAttributes = listOf(MatchingAttribute.Locale(value = listOf("en_US")))
        val failingAttributes = listOf(MatchingAttribute.Api(max = 19))
        given(matchingAttributes = matchingAttributes, failingAttributes = failingAttributes)
        val rules = mapOf(
            Pair(1, failingAttributes),
            Pair(2, matchingAttributes)
        )
        val expectedMessage = aMediumMessage(matchingRules = rules.keys.toList())

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage),
                rules = rules
            )
        )

        assertEquals(expectedMessage, message)
    }

    @Test
    fun whenUnknownRuleFailsThenReturnNull() = runBlocking {
        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aSmallMessage(matchingRules = listOf(1)), aMediumMessage(matchingRules = listOf(1))),
                rules = mapOf(
                    Pair(1, listOf(MatchingAttribute.Unknown(fallback = false)))
                )
            )
        )

        assertNull(message)
    }

    @Test
    fun whenUnknownRuleMatchesThenReturnFirstMatch() = runBlocking {
        val expectedMessage = aSmallMessage(matchingRules = listOf(1))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(expectedMessage, aMediumMessage(matchingRules = listOf(1))),
                rules = mapOf(
                    Pair(1, listOf(MatchingAttribute.Unknown(fallback = true)))
                )
            )
        )

        assertEquals(expectedMessage, message)
    }

    @Test
    fun whenThen() = runBlocking {
        val message = testee.evaluate(RemoteConfig(emptyList(), emptyMap()))
    }

    private suspend fun given(
        matchingAttributes: List<MatchingAttribute> = emptyList(),
        failingAttributes: List<MatchingAttribute> = emptyList()
    ) {
        matchingAttributes.forEach {
            whenever(deviceAttributeMatcher.evaluate(it)).thenReturn(Result.Match)
            whenever(androidAppAttributeMatcher.evaluate(it)).thenReturn(Result.Match)
            whenever(userAttributeMatcher.evaluate(it)).thenReturn(Result.Match)
        }

        failingAttributes.forEach {
            whenever(deviceAttributeMatcher.evaluate(it)).thenReturn(Result.Fail)
            whenever(androidAppAttributeMatcher.evaluate(it)).thenReturn(Result.Fail)
            whenever(userAttributeMatcher.evaluate(it)).thenReturn(Result.Fail)
        }
    }
}
