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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.nextStepsItemsJsonContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aNextStepsItemsMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.nextStepListItems
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.nextStepsItemsContent
import com.duckduckgo.remote.messaging.fixtures.getMessageMapper
import com.duckduckgo.remote.messaging.fixtures.messageActionPlugins
import com.duckduckgo.remote.messaging.impl.mappers.mapToRemoteMessage
import com.duckduckgo.remote.messaging.impl.models.JsonContentTranslations
import com.duckduckgo.remote.messaging.impl.models.JsonListItem
import com.duckduckgo.remote.messaging.impl.models.JsonListItemTranslation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class NextStepsItemsMessageMapperTest {

    @Test
    fun whenNextStepsItemsMessageWithValidDataThenReturnMessage() {
        val jsonMessages = listOf(aJsonMessage(id = "next_steps", content = nextStepsItemsJsonContent()))

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val content = remoteMessages.first().content
        assertTrue(content is Content.NextStepsItems)
        assertEquals(nextStepsItemsContent(), content)
    }

    @Test
    fun whenSetupItemThenMappedAsNextStepItemListItem() {
        val jsonMessages = listOf(aJsonMessage(id = "next_steps", content = nextStepsItemsJsonContent()))

        val content = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins).first().content as Content.NextStepsItems

        val item = content.listItems.first() as CardItem.ListItem
        assertEquals("setup_default_browser", item.id)
        assertEquals(CardItemType.NEXT_STEP_ITEM, item.type)
        assertEquals("Set as default", item.primaryActionText)
        assertEquals(Action.DefaultBrowser, item.primaryAction)
    }

    @Test
    fun whenListItemHasUnknownTypeThenItemIsDropped() {
        val listItems = listOf(
            JsonListItem(
                id = "unknown",
                type = "unknown_type",
                titleText = "Unknown",
                descriptionText = "Unknown",
                placeholder = "Announce",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com", additionalParameters = null),
            ),
        ) + nextStepsItemsJsonContent().listItems.orEmpty()
        val jsonMessages = listOf(aJsonMessage(id = "next_steps", content = nextStepsItemsJsonContent(listItems = listItems)))

        val content = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins).first().content as Content.NextStepsItems

        assertEquals(nextStepListItems(), content.listItems)
    }

    @Test
    fun whenNextStepsItemsMessageWithEmptyListItemsThenReturnEmptyList() {
        val jsonMessages = listOf(aJsonMessage(id = "next_steps", content = nextStepsItemsJsonContent(listItems = emptyList())))

        val content = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins).first().content as Content.NextStepsItems

        assertEquals(emptyList<CardItem>(), content.listItems)
    }

    @Test
    fun whenNextStepsItemsMessageWithEmptyTitleThenMessageIsFiltered() {
        val jsonMessages = listOf(aJsonMessage(id = "next_steps", content = nextStepsItemsJsonContent(titleText = "")))

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(0, remoteMessages.size)
    }

    @Test
    fun whenListItemMissingRequiredFieldsThenMessageIsFiltered() {
        val listItems = listOf(
            JsonListItem(
                id = "",
                type = "setup_item",
                titleText = "Set as default browser",
                descriptionText = "Description",
                placeholder = "Announce",
                primaryAction = JsonMessageAction(type = "defaultBrowser", value = "", additionalParameters = null),
            ),
        )
        val jsonMessages = listOf(aJsonMessage(id = "next_steps", content = nextStepsItemsJsonContent(listItems = listItems)))

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(0, remoteMessages.size)
    }

    @Test
    fun whenTranslationsMatchDeviceLocaleThenTitleAndListItemsAreTranslated() {
        val translations = mapOf(
            "de" to JsonContentTranslations(
                titleText = "Einrichtung abschließen",
                listItems = mapOf(
                    "setup_default_browser" to JsonListItemTranslation(
                        titleText = "Als Standardbrowser festlegen",
                        descriptionText = "Links in DuckDuckGo öffnen",
                        primaryActionText = "Als Standard festlegen",
                    ),
                ),
            ),
        )
        val jsonMessages = listOf(aJsonMessage(id = "next_steps", content = nextStepsItemsJsonContent(), translations = translations))

        val content = jsonMessages.mapToRemoteMessage(Locale.GERMANY, messageActionPlugins).first().content as Content.NextStepsItems

        val expected = nextStepsItemsContent(
            titleText = "Einrichtung abschließen",
            listItems = nextStepListItems(
                defaultBrowserTitleText = "Als Standardbrowser festlegen",
                defaultBrowserDescriptionText = "Links in DuckDuckGo öffnen",
                defaultBrowserPrimaryActionText = "Als Standard festlegen",
            ),
        )
        assertEquals(expected, content)
    }

    @Test
    fun whenNextStepsItemsMessageSerializedThenDeserializedMessageIsEqual() {
        val mapper = getMessageMapper()
        val message = aNextStepsItemsMessage(id = "next_steps")

        val restored = mapper.fromMessage(mapper.toString(message))

        assertEquals(message, restored)
    }
}
