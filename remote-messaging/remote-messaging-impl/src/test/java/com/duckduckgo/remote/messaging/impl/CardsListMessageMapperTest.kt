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

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.cardsListJsonContent
import com.duckduckgo.remote.messaging.fixtures.messageActionPlugins
import com.duckduckgo.remote.messaging.impl.mappers.mapToRemoteMessage
import com.duckduckgo.remote.messaging.impl.models.JsonContent
import com.duckduckgo.remote.messaging.impl.models.JsonListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

class CardsListMessageMapperTest {

    @Test
    fun whenCardsListMessageWithValidDataThenReturnMessage() {
        val jsonMessages = listOf(
            aJsonMessage(id = "cards1", content = cardsListJsonContent()),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val message = remoteMessages.first()
        assertEquals("cards1", message.id)
        assertTrue(message.content is Content.CardsList)

        val content = message.content as Content.CardsList
        assertEquals("title", content.titleText)
        assertEquals("description", content.descriptionText)
        assertEquals("Action", content.primaryActionText)
        assertEquals(2, content.listItems.size)
    }

    @Test
    fun whenCardsListMessageWithCustomListItemsThenMapCorrectly() {
        val customListItems = listOf(
            JsonListItem(
                id = "feature1",
                type = "two_line_list_item",
                titleText = "Feature One",
                descriptionText = "Description for feature one",
                placeholder = "ImageAI",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com/feature1", additionalParameters = null),
            ),
            JsonListItem(
                id = "feature2",
                type = "two_line_list_item",
                titleText = "Feature Two",
                descriptionText = "Description for feature two",
                placeholder = "Radar",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com/feature2", additionalParameters = null),
            ),
            JsonListItem(
                id = "feature3",
                type = "two_line_list_item",
                titleText = "Feature Three",
                descriptionText = "Description for feature three",
                placeholder = "KeyImport",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com/feature3", additionalParameters = null),
            ),
        )

        val jsonMessages = listOf(
            aJsonMessage(
                id = "cards2",
                content = cardsListJsonContent(listItems = customListItems),
            ),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val content = remoteMessages.first().content as Content.CardsList
        assertEquals(3, content.listItems.size)

        val item1 = content.listItems[0] as CardItem.ListItem
        assertEquals("feature1", item1.id)
        assertEquals(CardItemType.TWO_LINE_LIST_ITEM, item1.type)
        assertEquals("Feature One", item1.titleText)
        assertEquals("Description for feature one", item1.descriptionText)
        assertEquals(Content.Placeholder.IMAGE_AI, item1.placeholder)
        assertTrue(item1.primaryAction is Action.Url)
        assertEquals("https://example.com/feature1", (item1.primaryAction as Action.Url).value)

        val item2 = content.listItems[1] as CardItem.ListItem
        assertEquals("feature2", item2.id)
        assertEquals("Feature Two", item2.titleText)
        assertEquals(Content.Placeholder.RADAR, item2.placeholder)

        val item3 = content.listItems[2] as CardItem.ListItem
        assertEquals("feature3", item3.id)
        assertEquals("Feature Three", item3.titleText)
        assertEquals(Content.Placeholder.KEY_IMPORT, item3.placeholder)
    }

    @Test
    fun whenCardsListMessageWithEmptyListItemsThenReturnEmptyList() {
        val jsonMessages = listOf(
            aJsonMessage(
                id = "cards3",
                content = cardsListJsonContent(listItems = emptyList()),
            ),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val content = remoteMessages.first().content as Content.CardsList
        assertEquals(0, content.listItems.size)
    }

    @Test
    fun whenCardsListMessageWithNullListItemsThenReturnEmptyList() {
        val jsonContent = JsonContent(
            messageType = "cards_list",
            titleText = "title",
            descriptionText = "description",
            placeholder = "Announce",
            primaryActionText = "Action",
            primaryAction = JsonMessageAction(type = "url", value = "https://example.com", additionalParameters = null),
            listItems = null,
        )

        val jsonMessages = listOf(
            aJsonMessage(id = "cards4", content = jsonContent),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val content = remoteMessages.first().content as Content.CardsList
        assertNotNull(content.listItems)
        assertEquals(0, content.listItems.size)
    }

    @Test
    fun whenCardsListMessageWithMissingRequiredFieldsThenMessageIsFiltered() {
        // Missing title
        val jsonContentMissingTitle = JsonContent(
            messageType = "cards_list",
            titleText = "",
            descriptionText = "description",
            placeholder = "Announce",
            primaryActionText = "Action",
            primaryAction = JsonMessageAction(type = "url", value = "https://example.com", additionalParameters = null),
            listItems = emptyList(),
        )

        val jsonMessages = listOf(
            aJsonMessage(id = "cards5", content = jsonContentMissingTitle),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        // Message should be filtered out due to empty required field
        assertEquals(0, remoteMessages.size)
    }

    @Test
    fun whenCardsListMessageWithListItemMissingRequiredFieldsThenMessageIsFiltered() {
        val invalidListItems = listOf(
            JsonListItem(
                id = "", // Empty ID should cause failure
                type = "two_line_list_item",
                titleText = "Feature",
                descriptionText = "Description",
                placeholder = "ImageAI",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com", additionalParameters = null),
            ),
        )

        val jsonMessages = listOf(
            aJsonMessage(
                id = "cards6",
                content = cardsListJsonContent(listItems = invalidListItems),
            ),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        // Message should be filtered out due to invalid list item
        assertEquals(0, remoteMessages.size)
    }

    @Test
    fun whenCardsListMessageWithListItemMissingActionThenMessageIsFiltered() {
        val listItemsWithNullAction = listOf(
            JsonListItem(
                id = "item1",
                type = "two_line_list_item",
                titleText = "Feature",
                descriptionText = "Description",
                placeholder = "ImageAI",
                primaryAction = null, // Null action should cause failure
            ),
        )

        val jsonMessages = listOf(
            aJsonMessage(
                id = "cards7",
                content = cardsListJsonContent(listItems = listItemsWithNullAction),
            ),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        // Message should be filtered out due to null action in list item
        assertEquals(0, remoteMessages.size)
    }

    @Test
    fun whenCardsListMessageWithDifferentPlaceholdersThenMapCorrectly() {
        val listItemsWithDifferentPlaceholders = listOf(
            JsonListItem(
                id = "item1",
                type = "two_line_list_item",
                titleText = "AI Feature",
                descriptionText = "AI Description",
                placeholder = "DuckAi",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com", additionalParameters = null),
            ),
            JsonListItem(
                id = "item2",
                type = "two_line_list_item",
                titleText = "Privacy Feature",
                descriptionText = "Privacy Description",
                placeholder = "PrivacyShield",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com", additionalParameters = null),
            ),
        )

        val jsonMessages = listOf(
            aJsonMessage(
                id = "cards8",
                content = cardsListJsonContent(
                    placeholder = "DDGAnnounce",
                    listItems = listItemsWithDifferentPlaceholders,
                ),
            ),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val content = remoteMessages.first().content as Content.CardsList
        assertEquals(Content.Placeholder.DDG_ANNOUNCE, content.placeholder)
        assertEquals(Content.Placeholder.DUCK_AI, (content.listItems[0] as CardItem.ListItem).placeholder)
        assertEquals(Content.Placeholder.PRIVACY_SHIELD, (content.listItems[1] as CardItem.ListItem).placeholder)
    }

    @Test
    fun whenCardsListMessageWithDifferentActionTypesThenMapCorrectly() {
        val listItemsWithDifferentActions = listOf(
            JsonListItem(
                id = "item1",
                type = "two_line_list_item",
                titleText = "Web Feature",
                descriptionText = "Opens URL",
                placeholder = "ImageAI",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com", additionalParameters = null),
            ),
            JsonListItem(
                id = "item2",
                type = "two_line_list_item",
                titleText = "Dismiss Feature",
                descriptionText = "Just dismisses",
                placeholder = "Radar",
                primaryAction = JsonMessageAction(type = "dismiss", value = "", additionalParameters = null),
            ),
            JsonListItem(
                id = "item3",
                type = "two_line_list_item",
                titleText = "Share Feature",
                descriptionText = "Share content",
                placeholder = "KeyImport",
                primaryAction = JsonMessageAction(
                    type = "share",
                    value = "Share this!",
                    additionalParameters = mapOf("title" to "Share Title"),
                ),
            ),
        )

        val jsonMessages = listOf(
            aJsonMessage(
                id = "cards9",
                content = cardsListJsonContent(listItems = listItemsWithDifferentActions),
            ),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val content = remoteMessages.first().content as Content.CardsList
        assertEquals(3, content.listItems.size)

        assertTrue((content.listItems[0] as CardItem.ListItem).primaryAction is Action.Url)
        assertTrue((content.listItems[1] as CardItem.ListItem).primaryAction is Action.Dismiss)
        assertTrue((content.listItems[2] as CardItem.ListItem).primaryAction is Action.Share)

        val shareAction = (content.listItems[2] as CardItem.ListItem).primaryAction as Action.Share
        assertEquals("Share this!", shareAction.value)
        assertEquals("Share Title", shareAction.title)
    }

    @Test
    fun whenCardsListMessageWithMatchingAndExclusionRulesThenMapCorrectly() {
        val listItemsWithRules = listOf(
            JsonListItem(
                id = "item1",
                type = "two_line_list_item",
                titleText = "Feature One",
                descriptionText = "First feature",
                placeholder = "ImageAI",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com/1", additionalParameters = null),
                matchingRules = listOf(1, 2, 3),
                exclusionRules = listOf(4, 5),
            ),
            JsonListItem(
                id = "item2",
                type = "two_line_list_item",
                titleText = "Feature Two",
                descriptionText = "Second feature",
                placeholder = "Radar",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com/2", additionalParameters = null),
                matchingRules = listOf(6),
                exclusionRules = emptyList(),
            ),
        )

        val jsonMessages = listOf(
            aJsonMessage(
                id = "cards10",
                content = cardsListJsonContent(listItems = listItemsWithRules),
            ),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val content = remoteMessages.first().content as Content.CardsList
        assertEquals(2, content.listItems.size)

        val item1 = content.listItems[0] as CardItem.ListItem
        assertEquals("item1", item1.id)
        assertEquals(listOf(1, 2, 3), item1.matchingRules)
        assertEquals(listOf(4, 5), item1.exclusionRules)

        val item2 = content.listItems[1] as CardItem.ListItem
        assertEquals("item2", item2.id)
        assertEquals(listOf(6), item2.matchingRules)
        assertEquals(emptyList<Int>(), item2.exclusionRules)
    }

    @Test
    fun whenCardsListMessageWithoutRulesThenDefaultToEmptyLists() {
        val listItemsWithoutRules = listOf(
            JsonListItem(
                id = "item1",
                type = "two_line_list_item",
                titleText = "Feature",
                descriptionText = "Description",
                placeholder = "ImageAI",
                primaryAction = JsonMessageAction(type = "url", value = "https://example.com", additionalParameters = null),
            ),
        )

        val jsonMessages = listOf(
            aJsonMessage(
                id = "cards11",
                content = cardsListJsonContent(listItems = listItemsWithoutRules),
            ),
        )

        val remoteMessages = jsonMessages.mapToRemoteMessage(Locale.US, messageActionPlugins)

        assertEquals(1, remoteMessages.size)
        val content = remoteMessages.first().content as Content.CardsList
        assertEquals(1, content.listItems.size)

        val item = content.listItems[0] as CardItem.ListItem
        assertEquals("item1", item.id)
        assertEquals(emptyList<Int>(), item.matchingRules)
        assertEquals(emptyList<Int>(), item.exclusionRules)
    }
}
