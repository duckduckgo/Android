/*
 * Copyright (c) 2025 DuckDuckGo
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

import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.Content.Placeholder.SPLIT_BAR_SETTINGS
import com.duckduckgo.remote.messaging.api.Surface.NEW_TAB_PAGE
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.bigSingleActionJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.bigTwoActionJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.cardsListJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.mediumJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.promoSingleActionJsonContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aBigSingleActionMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aBigTwoActionsMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aCardsListMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aMediumMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aPromoSingleActionMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.bigSingleActionContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.bigTwoActionsContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.cardsListContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.mediumContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.promoSingleActionContent
import com.duckduckgo.remote.messaging.fixtures.messageActionPlugins
import com.duckduckgo.remote.messaging.impl.mappers.mapToRemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class SplitBarSettingsPlaceholderTest {

    @Test
    fun whenMediumMessageWithSplitBarSettingsPlaceholderThenCorrectlyParsed() {
        val jsonMessage = aJsonMessage(
            id = "split-bar-medium",
            content = mediumJsonContent(placeholder = "SplitBarSettings"),
        )

        val remoteMessages = listOf(jsonMessage).mapToRemoteMessage(Locale.US, messageActionPlugins)

        val expectedMessage = aMediumMessage(
            id = "split-bar-medium",
            content = mediumContent(placeholder = SPLIT_BAR_SETTINGS),
            surfaces = listOf(NEW_TAB_PAGE),
        )
        assertEquals(1, remoteMessages.size)
        assertEquals(expectedMessage, remoteMessages.first())

        val content = remoteMessages.first().content as Content.Medium
        assertEquals(SPLIT_BAR_SETTINGS, content.placeholder)
    }

    @Test
    fun whenBigSingleActionMessageWithSplitBarSettingsPlaceholderThenCorrectlyParsed() {
        val jsonMessage = aJsonMessage(
            id = "split-bar-big-single",
            content = bigSingleActionJsonContent(placeholder = "SplitBarSettings"),
        )

        val remoteMessages = listOf(jsonMessage).mapToRemoteMessage(Locale.US, messageActionPlugins)

        val expectedMessage = aBigSingleActionMessage(
            id = "split-bar-big-single",
            content = bigSingleActionContent(placeholder = SPLIT_BAR_SETTINGS),
            surfaces = listOf(NEW_TAB_PAGE),
        )
        assertEquals(1, remoteMessages.size)
        assertEquals(expectedMessage, remoteMessages.first())

        val content = remoteMessages.first().content as Content.BigSingleAction
        assertEquals(SPLIT_BAR_SETTINGS, content.placeholder)
    }

    @Test
    fun whenBigTwoActionsMessageWithSplitBarSettingsPlaceholderThenCorrectlyParsed() {
        val jsonMessage = aJsonMessage(
            id = "split-bar-big-two",
            content = bigTwoActionJsonContent(placeholder = "SplitBarSettings"),
        )

        val remoteMessages = listOf(jsonMessage).mapToRemoteMessage(Locale.US, messageActionPlugins)

        val expectedMessage = aBigTwoActionsMessage(
            id = "split-bar-big-two",
            content = bigTwoActionsContent(placeholder = SPLIT_BAR_SETTINGS),
            surfaces = listOf(NEW_TAB_PAGE),
        )
        assertEquals(1, remoteMessages.size)
        assertEquals(expectedMessage, remoteMessages.first())

        val content = remoteMessages.first().content as Content.BigTwoActions
        assertEquals(SPLIT_BAR_SETTINGS, content.placeholder)
    }

    @Test
    fun whenPromoSingleActionMessageWithSplitBarSettingsPlaceholderThenCorrectlyParsed() {
        val jsonMessage = aJsonMessage(
            id = "split-bar-promo",
            content = promoSingleActionJsonContent(placeholder = "SplitBarSettings"),
        )

        val remoteMessages = listOf(jsonMessage).mapToRemoteMessage(Locale.US, messageActionPlugins)

        val expectedMessage = aPromoSingleActionMessage(
            id = "split-bar-promo",
            content = promoSingleActionContent(placeholder = SPLIT_BAR_SETTINGS),
            surfaces = listOf(NEW_TAB_PAGE),
        )
        assertEquals(1, remoteMessages.size)
        assertEquals(expectedMessage, remoteMessages.first())

        val content = remoteMessages.first().content as Content.PromoSingleAction
        assertEquals(SPLIT_BAR_SETTINGS, content.placeholder)
    }

    @Test
    fun whenCardsListMessageWithSplitBarSettingsPlaceholderThenCorrectlyParsed() {
        val jsonMessage = aJsonMessage(
            id = "split-bar-cards",
            content = cardsListJsonContent(placeholder = "SplitBarSettings"),
        )

        val remoteMessages = listOf(jsonMessage).mapToRemoteMessage(Locale.US, messageActionPlugins)

        val expectedMessage = aCardsListMessage(
            id = "split-bar-cards",
            content = cardsListContent(placeholder = SPLIT_BAR_SETTINGS),
            surfaces = listOf(NEW_TAB_PAGE),
        )
        assertEquals(1, remoteMessages.size)
        assertEquals(expectedMessage, remoteMessages.first())

        val content = remoteMessages.first().content as Content.CardsList
        assertEquals(SPLIT_BAR_SETTINGS, content.placeholder)
    }

    @Test
    fun whenPlaceholderFromStringThenCorrectlyConverted() {
        val placeholder = Content.Placeholder.from("SplitBarSettings")

        assertEquals(SPLIT_BAR_SETTINGS, placeholder)
        assertEquals("SplitBarSettings", placeholder.jsonValue)
    }

    @Test
    fun whenMultipleMessagesWithDifferentPlaceholdersIncludingSplitBarSettingsThenAllCorrectlyParsed() {
        val messages = listOf(
            aJsonMessage(id = "msg1", content = mediumJsonContent(placeholder = "Announce")),
            aJsonMessage(id = "msg2", content = mediumJsonContent(placeholder = "SplitBarSettings")),
            aJsonMessage(id = "msg3", content = promoSingleActionJsonContent(placeholder = "SplitBarSettings")),
            aJsonMessage(id = "msg4", content = bigSingleActionJsonContent(placeholder = "PrivacyShield")),
        )

        val remoteMessages = messages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(4, remoteMessages.size)
        assertEquals(Content.Placeholder.ANNOUNCE, (remoteMessages[0].content as Content.Medium).placeholder)
        assertEquals(SPLIT_BAR_SETTINGS, (remoteMessages[1].content as Content.Medium).placeholder)
        assertEquals(SPLIT_BAR_SETTINGS, (remoteMessages[2].content as Content.PromoSingleAction).placeholder)
        assertEquals(Content.Placeholder.PRIVACY_SHIELD, (remoteMessages[3].content as Content.BigSingleAction).placeholder)
    }
}
