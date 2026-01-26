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

package com.duckduckgo.remote.messaging.impl.mappers

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

class CardItemAdapter {

    @ToJson
    fun cardItemToJson(cardItem: CardItem): CardItemJson {
        return when (cardItem) {
            is CardItem.ListItem -> CardItemJson(
                id = cardItem.id,
                type = cardItem.type.jsonValue,
                titleText = cardItem.titleText,
                descriptionText = cardItem.descriptionText,
                placeholder = cardItem.placeholder.jsonValue,
                primaryAction = cardItem.primaryAction,
                primaryActionText = cardItem.primaryActionText,
                matchingRules = cardItem.matchingRules,
                exclusionRules = cardItem.exclusionRules,
                itemIDs = null,
            )

            is CardItem.SectionTitle -> CardItemJson(
                id = cardItem.id,
                type = cardItem.type.jsonValue,
                titleText = cardItem.titleText,
                descriptionText = null,
                placeholder = null,
                primaryAction = null,
                matchingRules = null,
                exclusionRules = null,
                itemIDs = cardItem.itemIDs,
            )
        }
    }

    @FromJson
    fun cardItemFromJson(json: CardItemJson): CardItem {
        val type = CardItemType.entries.firstOrNull { it.jsonValue == json.type }
            ?: throw IllegalArgumentException("Unknown CardItemType: ${json.type}")

        return when (type) {
            CardItemType.TWO_LINE_LIST_ITEM,
            CardItemType.FEATURED_TWO_LINE_SINGLE_ACTION_LIST_ITEM,
            -> {
                CardItem.ListItem(
                    id = json.id,
                    type = type,
                    titleText = json.titleText,
                    descriptionText = json.descriptionText ?: "",
                    placeholder = json.placeholder?.let { Content.Placeholder.from(it) } ?: Content.Placeholder.ANNOUNCE,
                    primaryAction = json.primaryAction ?: throw IllegalArgumentException("ListItem requires primaryAction"),
                    primaryActionText = json.primaryActionText ?: "",
                    matchingRules = json.matchingRules ?: emptyList(),
                    exclusionRules = json.exclusionRules ?: emptyList(),
                )
            }

            CardItemType.LIST_SECTION_TITLE -> {
                CardItem.SectionTitle(
                    id = json.id,
                    type = type,
                    titleText = json.titleText,
                    itemIDs = json.itemIDs ?: emptyList(),
                )
            }
        }
    }

    data class CardItemJson(
        val id: String,
        val type: String,
        val titleText: String,
        val descriptionText: String? = null,
        val placeholder: String? = null,
        val primaryAction: Action? = null,
        val primaryActionText: String? = null,
        val matchingRules: List<Int>? = null,
        val exclusionRules: List<Int>? = null,
        val itemIDs: List<String>? = null,
    )
}
