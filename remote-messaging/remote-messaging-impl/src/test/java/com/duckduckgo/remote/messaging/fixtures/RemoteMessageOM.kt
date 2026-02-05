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

package com.duckduckgo.remote.messaging.fixtures

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.Content.Placeholder
import com.duckduckgo.remote.messaging.api.Content.Placeholder.ANNOUNCE
import com.duckduckgo.remote.messaging.api.Content.Placeholder.IMAGE_AI
import com.duckduckgo.remote.messaging.api.Content.Placeholder.MAC_AND_WINDOWS
import com.duckduckgo.remote.messaging.api.Content.Placeholder.RADAR
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.Surface

@Suppress("MemberVisibilityCanBePrivate")
object RemoteMessageOM {
    fun urlAction(
        url: String = "http://example.com",
    ) = Action.Url(value = url)

    fun smallContent(
        titleText: String = "title",
        descriptionText: String = "description",
    ) = Content.Small(
        titleText = titleText,
        descriptionText = descriptionText,
    )

    fun mediumContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: Placeholder = ANNOUNCE,
        imageUrl: String? = null,
    ) = Content.Medium(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        imageUrl = imageUrl,
    )

    fun bigSingleActionContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: Placeholder = ANNOUNCE,
        primaryActionText: String = "Action1",
        primaryAction: Action = urlAction(),
        imageUrl: String? = null,
    ) = Content.BigSingleAction(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        primaryActionText = primaryActionText,
        primaryAction = primaryAction,
        imageUrl = imageUrl,
    )

    fun bigTwoActionsContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: Placeholder = ANNOUNCE,
        primaryActionText: String = "Action1",
        primaryAction: Action = urlAction(),
        secondaryActionText: String = "Action2",
        secondaryAction: Action = urlAction(),
        imageUrl: String? = null,
    ) = Content.BigTwoActions(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        primaryActionText = primaryActionText,
        primaryAction = primaryAction,
        secondaryActionText = secondaryActionText,
        secondaryAction = secondaryAction,
        imageUrl = imageUrl,
    )

    fun promoSingleActionContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: Placeholder = MAC_AND_WINDOWS,
        actionText: String = "Action",
        action: Action = urlAction(),
        imageUrl: String? = null,
    ) = Content.PromoSingleAction(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        actionText = actionText,
        action = action,
        imageUrl = imageUrl,
    )

    fun cardsListContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: Placeholder = ANNOUNCE,
        primaryActionText: String = "Action",
        primaryAction: Action = urlAction(),
        listItems: List<CardItem> = translatedListItems(),
        imageUrl: String? = null,
    ) = Content.CardsList(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        primaryActionText = primaryActionText,
        primaryAction = primaryAction,
        listItems = listItems,
        imageUrl = imageUrl,
    )

    fun aSmallMessage(
        id: String = "id",
        content: Content = smallContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList(),
        surfaces: List<Surface> = emptyList(),
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules,
            surfaces = surfaces,
        )
    }

    fun aMediumMessage(
        id: String = "id",
        content: Content = mediumContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList(),
        surfaces: List<Surface> = emptyList(),
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules,
            surfaces = surfaces,
        )
    }

    fun aBigSingleActionMessage(
        id: String = "id",
        content: Content = bigSingleActionContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList(),
        surfaces: List<Surface> = emptyList(),
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules,
            surfaces = surfaces,
        )
    }

    fun aBigTwoActionsMessage(
        id: String = "id",
        content: Content = bigTwoActionsContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList(),
        surfaces: List<Surface> = emptyList(),
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules,
            surfaces = surfaces,
        )
    }

    fun aPromoSingleActionMessage(
        id: String = "id",
        content: Content = promoSingleActionContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList(),
        surfaces: List<Surface> = emptyList(),
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules,
            surfaces = surfaces,
        )
    }

    fun aCardsListMessage(
        id: String = "id",
        content: Content = cardsListContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList(),
        surfaces: List<Surface> = emptyList(),
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules,
            surfaces = surfaces,
        )
    }

    fun translatedListItems(
        item1TitleText: String = "Item Title 1",
        item1DescriptionText: String = "Item Description 1",
        item1PrimaryActionText: String = "Item Action 1",
        item2TitleText: String = "Item Title 2",
        item2DescriptionText: String = "Item Description 2",
        item2PrimaryActionText: String = "Item Action 2",
    ) = listOf(
        CardItem.ListItem(
            id = "item1",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = item1TitleText,
            descriptionText = item1DescriptionText,
            placeholder = IMAGE_AI,
            primaryAction = urlAction(),
            primaryActionText = item1PrimaryActionText,
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
        ),
        CardItem.ListItem(
            id = "item2",
            type = CardItemType.TWO_LINE_LIST_ITEM,
            titleText = item2TitleText,
            descriptionText = item2DescriptionText,
            placeholder = RADAR,
            primaryAction = urlAction(),
            primaryActionText = item2PrimaryActionText,
            matchingRules = emptyList(),
            exclusionRules = emptyList(),
        ),
    )
}
