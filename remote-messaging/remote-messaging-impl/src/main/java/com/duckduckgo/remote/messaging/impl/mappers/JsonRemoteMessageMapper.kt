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

package com.duckduckgo.remote.messaging.impl.mappers

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.CardItem
import com.duckduckgo.remote.messaging.api.CardItemType
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.Content.BigSingleAction
import com.duckduckgo.remote.messaging.api.Content.BigTwoActions
import com.duckduckgo.remote.messaging.api.Content.CardsList
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.Placeholder
import com.duckduckgo.remote.messaging.api.Content.PromoSingleAction
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.api.Surface
import com.duckduckgo.remote.messaging.impl.models.*
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.BIG_SINGLE_ACTION
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.BIG_TWO_ACTION
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.CARDS_LIST
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.MEDIUM
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.PROMO_SINGLE_ACTION
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.SMALL
import com.duckduckgo.remote.messaging.impl.models.asJsonFormat
import logcat.LogPriority.ERROR
import logcat.logcat
import java.util.Locale

private val smallMapper: (JsonContent, Set<MessageActionMapperPlugin>) -> Content = { jsonContent, _ ->
    Small(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
    )
}

private val mediumMapper: (JsonContent, Set<MessageActionMapperPlugin>) -> Content = { jsonContent, _ ->
    Medium(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
        placeholder = jsonContent.placeholder.asPlaceholder(),
        imageUrl = jsonContent.imageUrl,
    )
}

private val bigMessageSingleActionMapper: (JsonContent, Set<MessageActionMapperPlugin>) -> Content = { jsonContent, actionMappers ->
    BigSingleAction(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
        placeholder = jsonContent.placeholder.asPlaceholder(),
        primaryActionText = jsonContent.primaryActionText.failIfEmpty(),
        primaryAction = jsonContent.primaryAction!!.toAction(actionMappers),
        imageUrl = jsonContent.imageUrl,
    )
}

private val bigMessageTwoActionMapper: (JsonContent, Set<MessageActionMapperPlugin>) -> Content = { jsonContent, actionMappers ->
    BigTwoActions(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
        placeholder = jsonContent.placeholder.asPlaceholder(),
        primaryActionText = jsonContent.primaryActionText.failIfEmpty(),
        primaryAction = jsonContent.primaryAction!!.toAction(actionMappers),
        secondaryActionText = jsonContent.secondaryActionText.failIfEmpty(),
        secondaryAction = jsonContent.secondaryAction!!.toAction(actionMappers),
        imageUrl = jsonContent.imageUrl,
    )
}

private val promoSingleActionMapper: (JsonContent, Set<MessageActionMapperPlugin>) -> Content = { jsonContent, actionMappers ->
    PromoSingleAction(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
        placeholder = jsonContent.placeholder.asPlaceholder(),
        actionText = jsonContent.actionText.failIfEmpty(),
        action = jsonContent.action!!.toAction(actionMappers),
        imageUrl = jsonContent.imageUrl,
    )
}

private val cardsListMapper: (JsonContent, Set<MessageActionMapperPlugin>) -> Content = { jsonContent, actionMappers ->
    CardsList(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
        placeholder = jsonContent.placeholder.asPlaceholder(),
        primaryActionText = jsonContent.primaryActionText.failIfEmpty(),
        primaryAction = jsonContent.primaryAction!!.toAction(actionMappers),
        listItems = jsonContent.listItems.toListItems(actionMappers),
        imageUrl = jsonContent.imageUrl,
    )
}

private fun List<JsonListItem>?.toListItems(actionMappers: Set<MessageActionMapperPlugin>): List<CardItem> {
    return this?.mapNotNull { jsonItem ->
        itemMappers[jsonItem.type]?.invoke(jsonItem, actionMappers)
    } ?: emptyList()
}

private val twoLineListItemMapper: (JsonListItem, Set<MessageActionMapperPlugin>) -> CardItem = { jsonItem, actionMappers ->
    CardItem.ListItem(
        id = jsonItem.id.failIfEmpty(),
        type = jsonItem.type.toCardItemType(),
        titleText = jsonItem.titleText.failIfEmpty(),
        descriptionText = jsonItem.descriptionText.orEmpty().failIfEmpty(),
        placeholder = jsonItem.placeholder.orEmpty().asPlaceholder(),
        primaryAction = jsonItem.primaryAction?.toAction(actionMappers)
            ?: throw IllegalStateException("CardItem primaryAction cannot be null"),
        primaryActionText = jsonItem.primaryActionText.orEmpty(),
        matchingRules = jsonItem.matchingRules.orEmpty(),
        exclusionRules = jsonItem.exclusionRules.orEmpty(),
    )
}

private val sectionTitleMapper: (JsonListItem, Set<MessageActionMapperPlugin>) -> CardItem = { jsonItem, _ ->
    CardItem.SectionTitle(
        id = jsonItem.id.failIfEmpty(),
        type = jsonItem.type.toCardItemType(),
        titleText = jsonItem.titleText.failIfEmpty(),
        itemIDs = jsonItem.itemIDs.orEmpty(),
    )
}

private val itemMappers = mapOf(
    CardItemType.TWO_LINE_LIST_ITEM.jsonValue to twoLineListItemMapper,
    CardItemType.FEATURED_TWO_LINE_SINGLE_ACTION_LIST_ITEM.jsonValue to twoLineListItemMapper,
    CardItemType.LIST_SECTION_TITLE.jsonValue to sectionTitleMapper,
)

// plugin point?
private val messageMappers = mapOf(
    Pair(SMALL.jsonValue, smallMapper),
    Pair(MEDIUM.jsonValue, mediumMapper),
    Pair(BIG_SINGLE_ACTION.jsonValue, bigMessageSingleActionMapper),
    Pair(BIG_TWO_ACTION.jsonValue, bigMessageTwoActionMapper),
    Pair(PROMO_SINGLE_ACTION.jsonValue, promoSingleActionMapper),
    Pair(CARDS_LIST.jsonValue, cardsListMapper),
)

fun List<JsonRemoteMessage>.mapToRemoteMessage(
    locale: Locale,
    actionMappers: Set<MessageActionMapperPlugin>,
): List<RemoteMessage> = this.mapNotNull { it.map(locale, actionMappers) }

private fun JsonRemoteMessage.map(
    locale: Locale,
    actionMappers: Set<MessageActionMapperPlugin>,
): RemoteMessage? {
    return runCatching {
        val remoteMessage = RemoteMessage(
            id = this.id.failIfEmpty(),
            content = this.content!!.mapToContent(this.content.messageType, actionMappers),
            matchingRules = this.matchingRules.orEmpty(),
            exclusionRules = this.exclusionRules.orEmpty(),
            surfaces = this.surfaces.toSurfaceList(),
        )
        remoteMessage.localizeMessage(this.translations, locale)
    }.onFailure {
        logcat(ERROR) { "RMF: error parsing message id=${this.id}: ${it.message}\n${it.stackTraceToString()}" }
    }.getOrNull()
}

private fun List<String>?.toSurfaceList(): List<Surface> {
    return this?.mapNotNull { value ->
        Surface.entries.firstOrNull { it.jsonValue == value }
    } ?: listOf(Surface.NEW_TAB_PAGE)
}

private fun RemoteMessage.localizeMessage(
    translations: Map<String, JsonContentTranslations>?,
    locale: Locale,
): RemoteMessage {
    if (translations == null) return this

    val deviceTranslations = translations[locale.asJsonFormat()] ?: translations[locale.language]

    return if (deviceTranslations != null) {
        this.copy(content = this.content.localize(deviceTranslations))
    } else {
        this
    }
}

private fun JsonContent.mapToContent(
    messageType: String,
    actionMappers: Set<MessageActionMapperPlugin>,
): Content {
    return messageMappers[messageType]?.invoke(this, actionMappers) ?: throw IllegalArgumentException("Message type not found")
}

private fun JsonMessageAction.toAction(actionMappers: Set<MessageActionMapperPlugin>): Action {
    actionMappers.forEach {
        val result = it.evaluate(this)
        if (result != null) return result
    }
    logcat(ERROR) { "Unknown Action Type: $this. Available mappers: ${actionMappers.map { it::class.simpleName }}" }
    throw IllegalArgumentException("Unknown Action Type: $this")
}

private fun String.failIfEmpty() = this.ifEmpty { throw IllegalStateException("Empty argument") }

private fun String.asPlaceholder(): Placeholder = Placeholder.from(this)

private fun String.toCardItemType(): CardItemType {
    return CardItemType.entries.first { it.jsonValue == this }
}

private fun Content.localize(translations: JsonContentTranslations): Content {
    return when (this) {
        is BigSingleAction -> this.copy(
            titleText = translations.titleText.takeUnless { it.isEmpty() } ?: this.titleText,
            descriptionText = translations.descriptionText.takeUnless { it.isEmpty() } ?: this.descriptionText,
            primaryActionText = translations.primaryActionText.takeUnless { it.isEmpty() } ?: this.primaryActionText,
        )

        is BigTwoActions -> this.copy(
            titleText = translations.titleText.takeUnless { it.isEmpty() } ?: this.titleText,
            descriptionText = translations.descriptionText.takeUnless { it.isEmpty() } ?: this.descriptionText,
            primaryActionText = translations.primaryActionText.takeUnless { it.isEmpty() } ?: this.primaryActionText,
            secondaryActionText = translations.secondaryActionText.takeUnless { it.isEmpty() } ?: this.secondaryActionText,
        )

        is Medium -> this.copy(
            titleText = translations.titleText.takeUnless { it.isEmpty() } ?: this.titleText,
            descriptionText = translations.descriptionText.takeUnless { it.isEmpty() } ?: this.descriptionText,
        )

        is Small -> this.copy(
            titleText = translations.titleText.takeUnless { it.isEmpty() } ?: this.titleText,
            descriptionText = translations.descriptionText.takeUnless { it.isEmpty() } ?: this.descriptionText,
        )

        is PromoSingleAction -> this.copy(
            titleText = translations.titleText.takeUnless { it.isEmpty() } ?: this.titleText,
            descriptionText = translations.descriptionText.takeUnless { it.isEmpty() } ?: this.descriptionText,
            actionText = translations.actionText.takeUnless { it.isEmpty() } ?: this.actionText,
        )

        is CardsList -> this.copy(
            titleText = translations.titleText.takeUnless { it.isEmpty() } ?: this.titleText,
            descriptionText = translations.descriptionText.takeUnless { it.isEmpty() } ?: this.descriptionText,
            primaryActionText = translations.primaryActionText.takeUnless { it.isEmpty() } ?: this.primaryActionText,
            listItems = listItems.map { item ->
                when (item) {
                    is CardItem.ListItem -> {
                        val translatedItem = item.localize(translations)
                        item.copy(
                            titleText = translatedItem?.title.takeUnless { it.isNullOrEmpty() } ?: item.titleText,
                            descriptionText = translatedItem?.description.takeUnless { it.isNullOrEmpty() } ?: item.descriptionText,
                            primaryActionText = translatedItem?.primaryAction.takeUnless { it.isNullOrEmpty() } ?: item.primaryActionText,
                        )
                    }
                    is CardItem.SectionTitle -> {
                        val translatedItem = item.localize(translations)
                        item.copy(
                            titleText = translatedItem?.title.takeUnless { it.isNullOrEmpty() } ?: item.titleText,
                        )
                    }
                }
            },
        )
    }
}

private fun CardItem.localize(
    translations: JsonContentTranslations,
): CardItemTranslations? = translations.listItems?.get(id)?.let {
    CardItemTranslations(
        title = it.titleText,
        description = it.descriptionText.orEmpty(),
        primaryAction = it.primaryActionText.orEmpty(),
    )
}

private data class CardItemTranslations(
    val title: String,
    val description: String,
    val primaryAction: String,
)
