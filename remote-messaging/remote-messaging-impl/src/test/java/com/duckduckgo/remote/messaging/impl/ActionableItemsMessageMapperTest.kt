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
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.actionableItemsJsonContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.actionableItemsContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.actionableListItems
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.anActionableItemsMessage
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

class ActionableItemsMessageMapperTest {

    @Test
    fun whenActionableItemsMessageWithValidDataThenReturnMessage() {
        val jsonMessages = listOf(aJsonMessage(id = "actionable_items", content = actionableItemsJsonContent()))

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val content = remoteMessages.first().content
        assertTrue(content is Content.ActionableItems)
        assertEquals(actionableItemsContent(), content)
    }

    @Test
    fun whenOneActionItemThenMappedAsOneActionItemListItem() {
        val jsonMessages = listOf(aJsonMessage(id = "actionable_items", content = actionableItemsJsonContent()))

        val content = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins).first().content as Content.ActionableItems

        val item = content.listItems.first() as CardItem.ListItem
        assertEquals("setup_default_browser", item.id)
        assertEquals(CardItemType.ONE_ACTION_ITEM, item.type)
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
        ) + actionableItemsJsonContent().listItems.orEmpty()
        val jsonMessages = listOf(aJsonMessage(id = "actionable_items", content = actionableItemsJsonContent(listItems = listItems)))

        val content = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins).first().content as Content.ActionableItems

        assertEquals(actionableListItems(), content.listItems)
    }

    @Test
    fun whenListItemTypeIsNotOneActionItemThenItemIsDropped() {
        val unsupportedItems = listOf("two_line_list_item", "featured_two_line_single_action_list_item", "section_title").map { type ->
            JsonListItem(
                id = type,
                type = type,
                titleText = "Title",
                descriptionText = "Description",
                placeholder = "Announce",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com", additionalParameters = null),
            )
        }
        val listItems = unsupportedItems + actionableItemsJsonContent().listItems.orEmpty()
        val jsonMessages = listOf(aJsonMessage(id = "actionable_items", content = actionableItemsJsonContent(listItems = listItems)))

        val content = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins).first().content as Content.ActionableItems

        assertEquals(actionableListItems(), content.listItems)
    }

    @Test
    fun whenActionableItemsMessageWithEmptyListItemsThenReturnEmptyList() {
        val jsonMessages = listOf(aJsonMessage(id = "actionable_items", content = actionableItemsJsonContent(listItems = emptyList())))

        val content = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins).first().content as Content.ActionableItems

        assertEquals(emptyList<CardItem>(), content.listItems)
    }

    @Test
    fun whenActionableItemsMessageWithEmptyTitleThenMessageIsFiltered() {
        val jsonMessages = listOf(aJsonMessage(id = "actionable_items", content = actionableItemsJsonContent(titleText = "")))

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(0, remoteMessages.size)
    }

    @Test
    fun whenListItemMissingRequiredFieldsThenMessageIsFiltered() {
        val listItems = listOf(
            JsonListItem(
                id = "",
                type = "one_action_item",
                titleText = "Set as default browser",
                descriptionText = "Description",
                placeholder = "Announce",
                primaryAction = JsonMessageAction(type = "defaultBrowser", value = "", additionalParameters = null),
            ),
        )
        val jsonMessages = listOf(aJsonMessage(id = "actionable_items", content = actionableItemsJsonContent(listItems = listItems)))

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
        val jsonMessages = listOf(aJsonMessage(id = "actionable_items", content = actionableItemsJsonContent(), translations = translations))

        val content = jsonMessages.mapToRemoteMessage(Locale.GERMANY, messageActionPlugins).first().content as Content.ActionableItems

        val expected = actionableItemsContent(
            titleText = "Einrichtung abschließen",
            listItems = actionableListItems(
                defaultBrowserTitleText = "Als Standardbrowser festlegen",
                defaultBrowserDescriptionText = "Links in DuckDuckGo öffnen",
                defaultBrowserPrimaryActionText = "Als Standard festlegen",
            ),
        )
        assertEquals(expected, content)
    }

    @Test
    fun whenActionableItemsMessageSerializedThenDeserializedMessageIsEqual() {
        val mapper = getMessageMapper()
        val message = anActionableItemsMessage(id = "actionable_items")

        val restored = mapper.fromMessage(mapper.toString(message))

        assertEquals(message, restored)
    }
}
