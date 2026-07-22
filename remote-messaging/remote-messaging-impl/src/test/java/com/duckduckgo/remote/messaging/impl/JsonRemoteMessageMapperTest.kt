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

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.Surface.NEW_TAB_PAGE
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.bigSingleActionJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.bigTwoActionJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.cardsListJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.emptyJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.mediumJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.promoSingleActionJsonContent
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.smallJsonContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aBigSingleActionMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aBigTwoActionsMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aCardsListMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aMediumMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aPromoSingleActionMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.aSmallMessage
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.bigSingleActionContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.bigTwoActionsContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.cardsListContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.mediumContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.promoSingleActionContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.smallContent
import com.duckduckgo.remote.messaging.fixtures.RemoteMessageOM.translatedListItems
import com.duckduckgo.remote.messaging.fixtures.messageActionPlugins
import com.duckduckgo.remote.messaging.impl.mappers.mapToRemoteMessage
import com.duckduckgo.remote.messaging.impl.models.JsonContentTranslations
import com.duckduckgo.remote.messaging.impl.models.JsonListItem
import com.duckduckgo.remote.messaging.impl.models.JsonListItemTranslation
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.*

@RunWith(Parameterized::class)
class JsonRemoteMessageMapperTest(private val testCase: TestCase) {

    @Test
    fun whenJsonMessageThenReturnMessage() {
        val remoteMessages = testCase.jsonRemoteMessages.mapToRemoteMessage(Locale.FRANCE, messageActionPlugins)

        assertEquals(testCase.expectedMessages, remoteMessages)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun parameters() = arrayOf(
            TestCase(
                listOf(
                    aJsonMessage(id = "id1", content = smallJsonContent()),
                    aJsonMessage(id = "id2", content = mediumJsonContent()),
                    aJsonMessage(id = "id3", content = bigSingleActionJsonContent()),
                    aJsonMessage(id = "id4", content = bigTwoActionJsonContent()),
                    aJsonMessage(id = "id5", content = promoSingleActionJsonContent()),
                    aJsonMessage(id = "id6", content = cardsListJsonContent()),
                ),
                listOf(
                    aSmallMessage(id = "id1", surfaces = listOf(NEW_TAB_PAGE)),
                    aMediumMessage(id = "id2", surfaces = listOf(NEW_TAB_PAGE)),
                    aBigSingleActionMessage(id = "id3", surfaces = listOf(NEW_TAB_PAGE)),
                    aBigTwoActionsMessage(id = "id4", surfaces = listOf(NEW_TAB_PAGE)),
                    aPromoSingleActionMessage(id = "id5", surfaces = listOf(NEW_TAB_PAGE)),
                    aCardsListMessage(id = "id6", surfaces = listOf(NEW_TAB_PAGE)),
                ),
            ),
            TestCase(
                listOf(
                    aJsonMessage(id = "id1", content = emptyJsonContent()),
                    aJsonMessage(id = "id2", content = smallJsonContent()),
                    aJsonMessage(id = "id3", content = mediumJsonContent()),
                    aJsonMessage(id = "id4", content = bigSingleActionJsonContent()),
                    aJsonMessage(id = "id5", content = bigTwoActionJsonContent()),
                    aJsonMessage(id = "id6", content = promoSingleActionJsonContent()),
                    aJsonMessage(id = "id7", content = cardsListJsonContent()),
                ),
                listOf(
                    aSmallMessage(id = "id2", surfaces = listOf(NEW_TAB_PAGE)),
                    aMediumMessage(id = "id3", surfaces = listOf(NEW_TAB_PAGE)),
                    aBigSingleActionMessage(id = "id4", surfaces = listOf(NEW_TAB_PAGE)),
                    aBigTwoActionsMessage(id = "id5", surfaces = listOf(NEW_TAB_PAGE)),
                    aPromoSingleActionMessage(id = "id6", surfaces = listOf(NEW_TAB_PAGE)),
                    aCardsListMessage(id = "id7", surfaces = listOf(NEW_TAB_PAGE)),
                ),
            ),
            TestCase(
                listOf(
                    aJsonMessage(id = "id1", content = emptyJsonContent()),
                    aJsonMessage(id = "id1", content = emptyJsonContent(messageType = "small")),
                    aJsonMessage(id = "id1", content = emptyJsonContent(messageType = "medium")),
                ),
                emptyList(),
            ),
            TestCase(
                listOf(
                    aJsonMessage(id = ""),
                    aJsonMessage(content = emptyJsonContent()),
                    aJsonMessage(content = null),
                ),
                emptyList(),
            ),
            TestCase(
                listOf(
                    aJsonMessage(
                        id = "id1",
                        content = smallJsonContent(),
                        translations = mapOf("fr" to frenchTranslations()),
                    ),
                    aJsonMessage(
                        id = "id2",
                        content = mediumJsonContent(),
                        translations = mapOf("fr" to frenchTranslations()),
                    ),
                    aJsonMessage(
                        id = "id3",
                        content = bigSingleActionJsonContent(),
                        translations = mapOf("fr" to frenchTranslations()),
                    ),
                    aJsonMessage(
                        id = "id4",
                        content = bigTwoActionJsonContent(),
                        translations = mapOf("fr" to frenchTranslations()),
                    ),
                    aJsonMessage(
                        id = "id5",
                        content = promoSingleActionJsonContent(),
                        translations = mapOf("fr" to frenchTranslations()),
                    ),
                    aJsonMessage(
                        id = "id6",
                        content = cardsListJsonContent(),
                        translations = mapOf("fr" to frenchTranslations()),
                    ),
                ),
                listOf(
                    aSmallMessage(
                        id = "id1",
                        smallContent(titleText = frenchTranslations().titleText, descriptionText = frenchTranslations().descriptionText),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aMediumMessage(
                        id = "id2",
                        mediumContent(
                            titleText = frenchTranslations().titleText,
                            descriptionText = frenchTranslations().descriptionText,
                        ),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aBigSingleActionMessage(
                        id = "id3",
                        bigSingleActionContent(
                            titleText = frenchTranslations().titleText,
                            descriptionText = frenchTranslations().descriptionText,
                            primaryActionText = frenchTranslations().primaryActionText,
                        ),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aBigTwoActionsMessage(
                        id = "id4",
                        bigTwoActionsContent(
                            titleText = frenchTranslations().titleText,
                            descriptionText = frenchTranslations().descriptionText,
                            primaryActionText = frenchTranslations().primaryActionText,
                            secondaryActionText = frenchTranslations().secondaryActionText,
                        ),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aPromoSingleActionMessage(
                        id = "id5",
                        promoSingleActionContent(
                            titleText = frenchTranslations().titleText,
                            descriptionText = frenchTranslations().descriptionText,
                            actionText = frenchTranslations().actionText,
                        ),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aCardsListMessage(
                        id = "id6",
                        cardsListContent(
                            titleText = frenchTranslations().titleText,
                            descriptionText = frenchTranslations().descriptionText,
                            primaryActionText = frenchTranslations().primaryActionText,
                        ),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                ),
            ),
            TestCase(
                listOf(
                    aJsonMessage(id = "id1", content = mediumJsonContent(imageUrl = "https://example.com/image.png")),
                    aJsonMessage(id = "id2", content = bigSingleActionJsonContent(imageUrl = "https://example.com/banner.jpg")),
                    aJsonMessage(id = "id3", content = bigTwoActionJsonContent(imageUrl = "https://example.com/promo.png")),
                    aJsonMessage(id = "id4", content = promoSingleActionJsonContent(imageUrl = "https://example.com/promo2.jpg")),
                    aJsonMessage(id = "id5", content = cardsListJsonContent(imageUrl = "https://example.com/cards.png")),
                    aJsonMessage(id = "id6", content = mediumJsonContent(imageUrl = null)),
                ),
                listOf(
                    aMediumMessage(
                        id = "id1",
                        mediumContent(imageUrl = "https://example.com/image.png"),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aBigSingleActionMessage(
                        id = "id2",
                        bigSingleActionContent(imageUrl = "https://example.com/banner.jpg"),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aBigTwoActionsMessage(
                        id = "id3",
                        bigTwoActionsContent(imageUrl = "https://example.com/promo.png"),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aPromoSingleActionMessage(
                        id = "id4",
                        promoSingleActionContent(imageUrl = "https://example.com/promo2.jpg"),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aCardsListMessage(
                        id = "id5",
                        cardsListContent(imageUrl = "https://example.com/cards.png"),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                    aMediumMessage(
                        id = "id6",
                        mediumContent(imageUrl = null),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                ),
            ),
            TestCase(
                listOf(
                    aJsonMessage(
                        id = "id1",
                        content = cardsListJsonContent(),
                        translations = mapOf("fr" to frenchTranslationsWithListItems()),
                    ),
                ),
                listOf(
                    aCardsListMessage(
                        id = "id1",
                        cardsListContent(
                            titleText = "Bonjour",
                            descriptionText = "la description",
                            primaryActionText = "action principale",
                            listItems = translatedListItems(
                                item1TitleText = "Titre de l'élément 1",
                                item1DescriptionText = "Description de l'élément 1",
                                item1PrimaryActionText = "Action de l'élément 1",
                                item2TitleText = "Titre de l'élément 2",
                                item2DescriptionText = "Description de l'élément 2",
                                item2PrimaryActionText = "Action de l'élément 2",
                            ),
                        ),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                ),
            ),
            TestCase(
                listOf(
                    aJsonMessage(
                        id = "id1",
                        content = cardsListJsonContent(),
                        translations = mapOf(
                            "fr" to JsonContentTranslations(
                                titleText = "Bonjour",
                                descriptionText = "la description",
                                primaryActionText = "action principale",
                                listItems = mapOf(
                                    "unknown_item" to JsonListItemTranslation(
                                        titleText = "Should not appear",
                                        descriptionText = "Should not appear",
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
                listOf(
                    aCardsListMessage(
                        id = "id1",
                        cardsListContent(
                            titleText = "Bonjour",
                            descriptionText = "la description",
                            primaryActionText = "action principale",
                        ),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                ),
            ),
            TestCase(
                listOf(
                    aJsonMessage(
                        id = "id1",
                        content = cardsListJsonContent(
                            listItems = listOf(
                                JsonListItem(
                                    id = "item1",
                                    type = "featured_two_line_single_action_list_item",
                                    titleText = "Item Title 1",
                                    descriptionText = "Item Description 1",
                                    placeholder = "ImageAI",
                                    primaryAction = JsonMessageAction(type = "url", value = "http://example.com", additionalParameters = null),
                                    primaryActionText = "Action 1",
                                ),
                                JsonListItem(
                                    id = "section1",
                                    type = "section_title",
                                    titleText = "Section Title",
                                    descriptionText = null,
                                    placeholder = null,
                                    primaryAction = null,
                                    itemIDs = listOf("item1", "item2"),
                                ),
                                JsonListItem(
                                    id = "item2",
                                    type = "two_line_list_item",
                                    titleText = "Item Title 2",
                                    descriptionText = "Item Description 2",
                                    placeholder = "Radar",
                                    primaryAction = JsonMessageAction(type = "url", value = "http://example.com", additionalParameters = null),
                                    primaryActionText = "Action 2",
                                ),
                            ),
                        ),
                    ),
                ),
                listOf(
                    aCardsListMessage(
                        id = "id1",
                        cardsListContent(
                            listItems = listOf(
                                CardItem.ListItem(
                                    id = "item1",
                                    type = CardItemType.FEATURED_TWO_LINE_SINGLE_ACTION_LIST_ITEM,
                                    titleText = "Item Title 1",
                                    descriptionText = "Item Description 1",
                                    placeholder = Content.Placeholder.IMAGE_AI,
                                    primaryAction = Action.Url(value = "http://example.com"),
                                    primaryActionText = "Action 1",
                                    matchingRules = emptyList(),
                                    exclusionRules = emptyList(),
                                ),
                                CardItem.SectionTitle(
                                    id = "section1",
                                    type = CardItemType.LIST_SECTION_TITLE,
                                    titleText = "Section Title",
                                    itemIDs = listOf("item1", "item2"),
                                ),
                                CardItem.ListItem(
                                    id = "item2",
                                    type = CardItemType.TWO_LINE_LIST_ITEM,
                                    titleText = "Item Title 2",
                                    descriptionText = "Item Description 2",
                                    placeholder = Content.Placeholder.RADAR,
                                    primaryAction = Action.Url(value = "http://example.com"),
                                    primaryActionText = "Action 2",
                                    matchingRules = emptyList(),
                                    exclusionRules = emptyList(),
                                ),
                            ),
                        ),
                        surfaces = listOf(NEW_TAB_PAGE),
                    ),
                ),
            ),
        )

        private fun frenchTranslations() = JsonContentTranslations(
            titleText = "Bonjour",
            descriptionText = "la description",
            primaryActionText = "action principale",
            secondaryActionText = "action secondaire",
            actionText = "action principale",
        )

        private fun frenchTranslationsWithListItems() = JsonContentTranslations(
            titleText = "Bonjour",
            descriptionText = "la description",
            primaryActionText = "action principale",
            secondaryActionText = "action secondaire",
            actionText = "action principale",
            listItems = mapOf(
                "item1" to JsonListItemTranslation(
                    titleText = "Titre de l'élément 1",
                    descriptionText = "Description de l'élément 1",
                    primaryActionText = "Action de l'élément 1",
                ),
                "item2" to JsonListItemTranslation(
                    titleText = "Titre de l'élément 2",
                    descriptionText = "Description de l'élément 2",
                    primaryActionText = "Action de l'élément 2",
                ),
            ),
        )
    }

    data class TestCase(
        val jsonRemoteMessages: List<JsonRemoteMessage>,
        val expectedMessages: List<RemoteMessage>,
    )
}
