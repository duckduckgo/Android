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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.Content.Placeholder.ANNOUNCE
import com.duckduckgo.remote.messaging.api.Content.Placeholder.APP_UPDATE
import com.duckduckgo.remote.messaging.api.Content.Placeholder.CRITICAL_UPDATE
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.impl.mappers.RemoteMessagingConfigJsonMapper
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessagingConfig
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.Api
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.DefaultBrowser
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.Locale
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.Unknown
import com.duckduckgo.remote.messaging.impl.models.MatchingAttribute.WebView
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.BufferedReader
import java.util.Locale.US

@ExperimentalCoroutinesApi
class RemoteMessagingConfigJsonMapperTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val appBuildConfig = mock<AppBuildConfig>().apply {
        whenever(this.deviceLocale).thenReturn(US)
    }

    @Test
    fun whenValidJsonParsedThenMessagesMappedIntoRemoteConfig() = runTest {
        val result = getConfigFromJson("json/remote_messaging_config.json")

        val testee = RemoteMessagingConfigJsonMapper(appBuildConfig)

        val config = testee.map(result)

        assertEquals(5, config.messages.size)
        val bigSingleActionMessage = RemoteMessage(
            id = "8274589c-8aeb-4322-a737-3852911569e3",
            content = Content.BigSingleAction(
                titleText = "title",
                descriptionText = "description",
                placeholder = ANNOUNCE,
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
            content = Content.Medium(
                titleText = "Here goes a title",
                descriptionText = "description",
                placeholder = CRITICAL_UPDATE
            ),
            matchingRules = emptyList(),
            exclusionRules = emptyList()
        )
        assertEquals(mediumMessage, config.messages[3])

        val bigTwoActions = RemoteMessage(
            id = "c2d0a1f1-6157-434f-8145-38416037d339",
            content = Content.BigTwoActions(
                titleText = "Here goes a title",
                descriptionText = "description",
                placeholder = APP_UPDATE,
                primaryActionText = "Ok",
                primaryAction = Action.PlayStore(
                    value = "com.duckduckgo.mobile.android"
                ),
                secondaryActionText = "Cancel",
                secondaryAction = Action.Dismiss(),
            ),
            matchingRules = emptyList(),
            exclusionRules = emptyList()
        )
        assertEquals(bigTwoActions, config.messages[4])
    }

    @Test
    fun whenValidJsonParsedThenRulesMappedIntoRemoteConfig() = runTest {
        val result = getConfigFromJson("json/remote_messaging_config.json")

        val testee = RemoteMessagingConfigJsonMapper(appBuildConfig)

        val config = testee.map(result)

        assertEquals(3, config.rules.size)

        assertEquals(21, config.rules[5]?.size)
        val localeMA = Locale(listOf("en-US", "en-GB"), fallback = true)
        assertEquals(localeMA, config.rules[5]?.first())
        assertTrue(config.rules[5]?.get(1) is Api)

        val locale2MA = Locale(listOf("en-GB"), fallback = null)
        assertEquals(locale2MA, config.rules[6]?.first())
        assertEquals(1, config.rules[6]?.size)

        val defaultBrowserMA = DefaultBrowser(value = true, fallback = null)
        assertEquals(defaultBrowserMA, config.rules[7]?.first())
        assertEquals(1, config.rules[7]?.size)
    }

    @Test
    fun whenJsonMessagesHaveUnknownTypesThenMessagesNotMappedIntoConfig() = runTest {
        val result = getConfigFromJson("json/remote_messaging_config_unsupported_items.json")

        val testee = RemoteMessagingConfigJsonMapper(appBuildConfig)

        val config = testee.map(result)

        assertEquals(0, config.messages.size)
    }

    @Test
    fun whenJsonMessagesHaveUnknownTypesThenRulesMappedIntoConfig() = runTest {
        val result = getConfigFromJson("json/remote_messaging_config_unsupported_items.json")

        val testee = RemoteMessagingConfigJsonMapper(appBuildConfig)

        val config = testee.map(result)

        assertEquals(2, config.rules.size)

        val unknown = Unknown(fallback = true)
        assertEquals(unknown, config.rules[6]!![0])

        val defaultBrowser = DefaultBrowser(value = true, fallback = null)
        assertEquals(defaultBrowser, config.rules[7]!![0])
    }

    @Test
    fun whenJsonMessagesMalformedOrMissingInformationThenMessagesNotParsedIntoConfig() = runTest {
        val result = getConfigFromJson("json/remote_messaging_config_malformed.json")

        val testee = RemoteMessagingConfigJsonMapper(appBuildConfig)

        val config = testee.map(result)

        assertEquals(1, config.messages.size)
        val smallMessage = RemoteMessage(
            id = "26780792-49fe-4e25-ae27-aa6a2e6f013b",
            content = Content.Small(
                titleText = "Here goes a title",
                descriptionText = "description"
            ),
            matchingRules = listOf(5, 6),
            exclusionRules = listOf(7, 8, 9)
        )
        assertEquals(smallMessage, config.messages[0])
    }

    @Test
    fun whenJsonMatchingAttributesMalformedThenParsedAsUnknwonIntoConfig() = runTest {
        val result = getConfigFromJson("json/remote_messaging_config_malformed.json")

        val testee = RemoteMessagingConfigJsonMapper(appBuildConfig)

        val config = testee.map(result)

        assertEquals(2, config.rules.size)
        assertEquals(3, config.rules[6]?.size)

        val matchingAttr = listOf(Locale(), Unknown(fallback = true), WebView(fallback = false))
        assertEquals(matchingAttr, config.rules[6])
    }

    @Test
    fun whenUnknownMatchingAttributeDoesNotProvideFallbackThenFallbackIsNull() = runTest {
        val result = getConfigFromJson("json/remote_messaging_config_malformed.json")

        val testee = RemoteMessagingConfigJsonMapper(appBuildConfig)

        val config = testee.map(result)

        assertEquals(Unknown(null), config.rules[7]?.first())
    }

    private fun getConfigFromJson(resourceName: String): JsonRemoteMessagingConfig {
        val jsonString = FileUtilities.loadText(resourceName)
        val moshi = Moshi.Builder().build()
        val jsonAdapter = moshi.adapter(JsonRemoteMessagingConfig::class.java)

        return jsonAdapter.fromJson(jsonString)!!
    }
}

object FileUtilities {

    fun loadText(resourceName: String): String = readResource(resourceName).use { it.readText() }

    private fun readResource(resourceName: String): BufferedReader {
        return javaClass.classLoader!!.getResource(resourceName).openStream().bufferedReader()
    }
}
