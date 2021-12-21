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

package com.duckduckgo.app.remotemessage.impl

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.app.remotemessage.impl.matchingattributes.*
import com.duckduckgo.app.remotemessage.impl.matchingattributes.MatchingAttribute.*
import com.duckduckgo.app.remotemessage.impl.messages.*
import com.duckduckgo.app.runBlocking
import com.duckduckgo.privacy.config.impl.network.JSONObjectAdapter
import com.squareup.moshi.Moshi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RemoteMessagingConfigJsonParserTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Test
    fun whenJsonParseThenRemoteConfigReturned() = coroutineRule.runBlocking {
        val jsonString = FileUtilities.loadText("json/remote_messaging_config.json")
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter = moshi.adapter(JsonRemoteMessagingConfig::class.java)
        val result = jsonAdapter.fromJson(jsonString)!!

        val testee = RemoteMessagingConfigJsonParser(
            jsonRemoteMessageMapper = JsonRemoteMessageMapper(),
            jsonRulesMapper = JsonRulesMapper()
        )

        val config = testee.map(result)

        assertEquals(5, config.messages.size)
        val bigSingleActionMessage = RemoteMessage(
            id = "8274589c-8aeb-4322-a737-3852911569e3",
            messageType = "big_single_action",
            content = Content.BigSingleAction(
                titleText = "title",
                descriptionText = "description",
                placeholder = "WARNING",
                primaryActionText = "Ok",
                primaryAction = Action.Url(
                    value = "https://duckduckgo.com"
                )
            ),
            matchingRules = emptyList(),
            exclusionRules = emptyList()
        )
        assertEquals(bigSingleActionMessage, config.messages[0])

        val smallMessage = RemoteMessage(
            id = "26780792-49fe-4e25-ae27-aa6a2e6f013b",
            messageType = "small",
            content = Content.Small(
                titleText = "Here goes a title",
                descriptionText = "description"
            ),
            matchingRules = listOf(5, 6),
            exclusionRules = listOf(7, 8, 9)
        )
        assertEquals(smallMessage, config.messages[2])

        val mediumMessage = RemoteMessage(
            id = "c3549d64-b388-41d8-9649-33e6e2674e8e",
            messageType = "medium",
            content = Content.Medium(
                titleText = "Here goes a title",
                descriptionText = "description",
                placeholder = "WARNING"
            ),
            matchingRules = emptyList(),
            exclusionRules = emptyList()
        )
        assertEquals(mediumMessage, config.messages[3])

        val bigTwoActions = RemoteMessage(
            id = "c2d0a1f1-6157-434f-8145-38416037d339",
            messageType = "big_two_action",
            content = Content.BigTwoActions(
                titleText = "Here goes a title",
                descriptionText = "description",
                placeholder = "WARNING",
                primaryActionText = "Ok",
                primaryAction = Action.PlayStore(
                    value = "com.duckduckgo.mobile.android"
                ),
                secondaryActionText = "Cancel",
                secondaryAction = Action.Dismiss,
            ),
            matchingRules = emptyList(),
            exclusionRules = emptyList()
        )
        assertEquals(bigTwoActions, config.messages[4])


        assertEquals(3, config.rules.size)

        assertEquals(21, config.rules[5]?.size)
        val localeMA = Locale(listOf("en_US", "en_GB"), fallback = true)
        assertEquals(localeMA, config.rules[5]?.first())

        val locale2MA = Locale(listOf("en_GB"), fallback = null)
        assertEquals(locale2MA, config.rules[6]?.first())
        assertEquals(1, config.rules[6]?.size)

        val defaultBrowserMA = MatchingAttribute.DefaultBrowser(value = true, fallback = null)
        assertEquals(defaultBrowserMA, config.rules[7]?.first())
        assertEquals(1, config.rules[7]?.size)
    }

    @Test
    fun whenJsonHasUnknownItemsThenMessageNotParsed() = coroutineRule.runBlocking {
        val jsonString = FileUtilities.loadText("json/remote_messaging_config_unsupported_items.json")
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter = moshi.adapter(JsonRemoteMessagingConfig::class.java)
        val result = jsonAdapter.fromJson(jsonString)!!

        val testee = RemoteMessagingConfigJsonParser(
            jsonRemoteMessageMapper = JsonRemoteMessageMapper(),
            jsonRulesMapper = JsonRulesMapper()
        )

        val config = testee.map(result)

        assertEquals(1, config.messages.size)
        assertEquals(2, config.rules.size)

        val unknown = Unknown(fallback = true)
        assertEquals(unknown, config.rules[6]!![0])

        val defaultBrowser = MatchingAttribute.DefaultBrowser(value = true, fallback = null)
        assertEquals(defaultBrowser, config.rules[7]!![0])
    }

    @Test
    fun whenJsonMalformedThenMessageNotParsed() = coroutineRule.runBlocking {
        val jsonString = FileUtilities.loadText("json/remote_messaging_config_malformed.json")
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter = moshi.adapter(JsonRemoteMessagingConfig::class.java)
        val result = jsonAdapter.fromJson(jsonString)!!

        val testee = RemoteMessagingConfigJsonParser(
            jsonRemoteMessageMapper = JsonRemoteMessageMapper(),
            jsonRulesMapper = JsonRulesMapper()
        )

        val config = testee.map(result)


        assertEquals(1, config.messages.size)
        val smallMessage = RemoteMessage(
            id = "26780792-49fe-4e25-ae27-aa6a2e6f013b",
            messageType = "small",
            content = Content.Small(
                titleText = "Here goes a title",
                descriptionText = "description"
            ),
            matchingRules = listOf(5, 6),
            exclusionRules = listOf(7, 8, 9)
        )
        assertEquals(smallMessage, config.messages[0])

        assertEquals(2, config.rules.size)
        assertEquals(3, config.rules[6]?.size)

        val matchingAttr = listOf(Unknown(fallback = null), Unknown(fallback = true), Unknown(fallback = false))
        assertEquals(matchingAttr, config.rules[6])
    }

    @Test
    fun whenMatchingAttributeUnknownNoFallbackThenFallbackToFail() = coroutineRule.runBlocking {
        val jsonString = FileUtilities.loadText("json/remote_messaging_config_malformed.json")
        val moshi = Moshi.Builder().add(JSONObjectAdapter()).build()
        val jsonAdapter = moshi.adapter(JsonRemoteMessagingConfig::class.java)
        val result = jsonAdapter.fromJson(jsonString)!!

        val testee = RemoteMessagingConfigJsonParser(
            jsonRemoteMessageMapper = JsonRemoteMessageMapper(),
            jsonRulesMapper = JsonRulesMapper()
        )

        val config = testee.map(result)

        assertEquals(Unknown(fallback = false), config.rules[7]?.first())
    }
}