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

import com.duckduckgo.remote.messaging.api.RemoteMessagingRepository
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aMediumMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aSmallMessage
import com.duckduckgo.remote.messaging.impl.matchers.AndroidAppAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.DeviceAttributeMatcher
import com.duckduckgo.remote.messaging.impl.matchers.Result
import com.duckduckgo.remote.messaging.impl.matchers.UserAttributeMatcher
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.Api
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.Bookmarks
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.EmailEnabled
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.Locale
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.Unknown
import com.duckduckgo.remote.messaging.impl.models.RemoteConfig
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteMessagingConfigMatcherTest {

    private val deviceAttributeMatcher: DeviceAttributeMatcher = mock()
    private val androidAppAttributeMatcher: AndroidAppAttributeMatcher = mock()
    private val userAttributeMatcher: UserAttributeMatcher = mock()
    private val remoteMessagingRepository: RemoteMessagingRepository = mock()

    private val testee = RemoteMessagingConfigMatcher(
        deviceAttributeMatcher, androidAppAttributeMatcher, remoteMessagingRepository, userAttributeMatcher
    )

    @Test
    fun whenEmptyConfigThenReturnNull() = runBlocking {
        val emptyRemoteConfig = RemoteConfig(messages = emptyList(), rules = emptyMap())

        val message = testee.evaluate(emptyRemoteConfig)

        assertNull(message)
    }

    @Test
    fun whenNoMatchingRulesThenReturnFirstMessage() = runBlocking {
        val noRulesRemoteConfig = RemoteConfig(messages = listOf(aSmallMessage()), rules = emptyMap())

        val message = testee.evaluate(noRulesRemoteConfig)

        assertEquals(aSmallMessage(), message)
    }

    @Test
    fun whenNotExistingRuleThenReturnSkipMessage() = runBlocking {
        val noRulesRemoteConfig = RemoteConfig(
            messages = listOf(
                aSmallMessage(matchingRules = rules(1)),
                aMediumMessage()
            ),
            rules = emptyMap()
        )

        val message = testee.evaluate(noRulesRemoteConfig)

        assertEquals(aMediumMessage(), message)
    }

    @Test
    fun whenNoMessagesThenReturnNull() = runBlocking {
        val noMessagesRemoteConfig = RemoteConfig(
            messages = emptyList(),
            rules = mapOf(rule(1, Api(max = 19)))
        )

        val message = testee.evaluate(noMessagesRemoteConfig)

        assertNull(message)
    }

    @Test
    fun whenDeviceDoesNotMatchMessageRulesThenReturnNull() = runBlocking {
        givenDeviceMatches()

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(matchingRules = rules(1)),
                    aMediumMessage(matchingRules = rules(1))
                ),
                rules = mapOf(rule(1, Api(max = 19)))
            )
        )

        assertNull(message)
    }

    @Test
    fun whenNoMatchingRulesThenReturnFirstNonExcludedMessage() = runBlocking {
        givenDeviceMatches(Api(max = 19), Locale(value = listOf("en-US")), EmailEnabled(value = true))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = emptyList(), exclusionRules = rules(2)),
                    aMediumMessage(matchingRules = emptyList(), exclusionRules = rules(3))
                ),
                rules = mapOf(
                    rule(1, Api(max = 19)),
                    rule(2, Locale(value = listOf("en-US"))),
                    rule(3, EmailEnabled(value = false))
                )
            )
        )

        assertEquals(aMediumMessage(matchingRules = emptyList(), exclusionRules = rules(3)), message)
    }

    @Test
    fun whenMatchingMessageShouldBeExcludedThenReturnNull() = runBlocking {
        givenDeviceMatches(Api(max = 19), Locale(value = listOf("en-US")))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2))),
                rules = mapOf(
                    rule(1, Api(max = 19)),
                    rule(2, Locale(value = listOf("en-US")))
                )
            )
        )

        assertNull(message)
    }

    @Test
    fun whenMatchingMessageShouldBeExcludedByOneOfMultipleRulesThenReturnNull() = runBlocking {
        givenDeviceMatches(Api(max = 19), EmailEnabled(value = true), Bookmarks(max = 10))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(4)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2, 3)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2, 3, 4)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2, 4)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(5))
                ),
                rules = mapOf(
                    rule(1, Api(max = 19)),
                    rule(2, EmailEnabled(value = true), Bookmarks(max = 10)),
                    rule(3, EmailEnabled(value = true), Bookmarks(max = 10)),
                    rule(4, Api(max = 19)),
                    rule(5, EmailEnabled(value = true))
                )
            )
        )

        assertNull(message)
    }

    @Test
    fun whenMultipleMatchingMessagesAndSomeExcludedThenReturnFirstNonExcludedMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19), Locale(value = listOf("en-US")))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2)),
                    aMediumMessage(matchingRules = rules(1), exclusionRules = emptyList()),
                ),
                rules = mapOf(
                    rule(1, Api(max = 19)),
                    rule(2, Locale(value = listOf("en-US")))
                )
            )
        )

        assertEquals(aMediumMessage(matchingRules = rules(1), exclusionRules = emptyList()), message)
    }

    @Test
    fun whenMessageMatchesAndExclusionRuleFailsThenReturnMessage() = runBlocking {
        givenDeviceMatches(Api(max = 19), EmailEnabled(value = true))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2)),
                ),
                rules = mapOf(
                    rule(1, Api(max = 19)),
                    rule(2, EmailEnabled(value = false))
                )
            )
        )

        assertEquals(aMediumMessage(matchingRules = rules(1), exclusionRules = rules(2)), message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesThenReturnFirstMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(matchingRules = rules(1))),
                rules = mapOf(rule(1, Api(max = 19)))
            )
        )

        assertEquals(aMediumMessage(matchingRules = rules(1)), message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesForMultipleMessagesThenReturnFirstMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1)),
                    aSmallMessage(matchingRules = rules(1))
                ),
                rules = mapOf(rule(1, Api(max = 19)))
            )
        )

        assertEquals(aMediumMessage(matchingRules = rules(1)), message)
    }

    @Test
    fun whenDeviceMatchesMessageRulesForOneOfMultipleMessagesThenReturnMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19), EmailEnabled(value = true))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(matchingRules = rules(2)),
                    aMediumMessage(matchingRules = rules(1, 2))
                ),
                rules = mapOf(
                    rule(1, Api(max = 19)),
                    rule(2, EmailEnabled(value = false))
                )
            )
        )

        assertEquals(aMediumMessage(matchingRules = rules(1, 2)), message)
    }

    @Test
    fun whenUserDismissedMessagesAndDeviceMatchesMultipleMessagesThenReturnFistMatchNotDismissed() = runBlocking {
        givenDeviceMatches(Api(max = 19), EmailEnabled(value = true))
        givenUserDismissed("1")
        val rules = mapOf(
            rule(1, Api(max = 19))
        )

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(id = "1", matchingRules = rules(1)),
                    aMediumMessage(id = "2", matchingRules = rules(1))
                ),
                rules = rules
            )
        )

        assertEquals(aMediumMessage(id = "2", matchingRules = rules(1)), message)
    }

    @Test
    fun whenDeviceMatchesAnyRuleThenReturnFirstMatch() = runBlocking {
        givenDeviceMatches(Api(max = 19), Locale(value = listOf("en-US")))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(aMediumMessage(matchingRules = rules(1, 2))),
                rules = mapOf(
                    rule(1, Locale(value = listOf("en-US"))),
                    rule(2, Api(max = 15))
                )
            )
        )

        assertEquals(aMediumMessage(matchingRules = rules(1, 2)), message)
    }

    @Test
    fun whenDeviceDoesMatchAnyRuleThenReturnNull() = runBlocking {
        givenDeviceMatches(Locale(value = listOf("en-US")), Api(max = 19))

        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aMediumMessage(matchingRules = rules(1, 2)),
                    aSmallMessage(matchingRules = rules(1, 2))
                ),
                rules = mapOf(
                    rule(1, Api(max = 15)),
                    rule(2, Api(max = 15))
                )
            )
        )

        assertNull(message)
    }

    @Test
    fun whenUnknownRuleFailsThenReturnNull() = runBlocking {
        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(matchingRules = rules(1)),
                    aMediumMessage(matchingRules = rules(1))
                ),
                rules = mapOf(
                    rule(1, Unknown(fallback = false))
                )
            )
        )

        assertNull(message)
    }

    @Test
    fun whenUnknownRuleMatchesThenReturnFirstMatch() = runBlocking {
        val message = testee.evaluate(
            RemoteConfig(
                messages = listOf(
                    aSmallMessage(matchingRules = rules(1)),
                    aMediumMessage(matchingRules = rules(1))
                ),
                rules = mapOf(
                    rule(1, Unknown(fallback = true))
                )
            )
        )

        assertEquals(aSmallMessage(matchingRules = rules(1)), message)
    }

    private suspend fun givenDeviceMatches(
        vararg matchingAttributes: MatchingAttribute
    ) {
        whenever(deviceAttributeMatcher.evaluate(any())).thenReturn(Result.Fail)
        whenever(androidAppAttributeMatcher.evaluate(any())).thenReturn(Result.Fail)
        whenever(userAttributeMatcher.evaluate(any())).thenReturn(Result.Fail)

        matchingAttributes.forEach {
            whenever(deviceAttributeMatcher.evaluate(it)).thenReturn(Result.Match)
            whenever(androidAppAttributeMatcher.evaluate(it)).thenReturn(Result.Match)
            whenever(userAttributeMatcher.evaluate(it)).thenReturn(Result.Match)
        }
    }

    private fun givenUserDismissed(vararg ids: String) {
        whenever(remoteMessagingRepository.dismissedMessages()).thenReturn(ids.asList())
    }

    private fun rule(
        id: Int,
        vararg matchingAttributes: MatchingAttribute
    ) = Pair(id, matchingAttributes.asList())

    private fun rules(vararg ids: Int) = ids.asList()
}
