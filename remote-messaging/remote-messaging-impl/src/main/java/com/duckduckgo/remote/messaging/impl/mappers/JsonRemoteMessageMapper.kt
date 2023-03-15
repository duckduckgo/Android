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
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.Content.BigSingleAction
import com.duckduckgo.remote.messaging.api.Content.BigTwoActions
import com.duckduckgo.remote.messaging.api.Content.Medium
import com.duckduckgo.remote.messaging.api.Content.Placeholder
import com.duckduckgo.remote.messaging.api.Content.Small
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.impl.models.*
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.APP_TP_ONBOARDING
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.DEFAULT_BROWSER
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.DISMISS
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.PLAYSTORE
import com.duckduckgo.remote.messaging.impl.models.JsonActionType.URL
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.BIG_SINGLE_ACTION
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.BIG_TWO_ACTION
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.MEDIUM
import com.duckduckgo.remote.messaging.impl.models.JsonMessageType.SMALL
import com.duckduckgo.remote.messaging.impl.models.asJsonFormat
import java.util.Locale
import timber.log.Timber

private val smallMapper: (JsonContent, List<ActionMapper>) -> Content = { jsonContent, actionMappers ->
    Small(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
    )
}

private val mediumMapper: (JsonContent, List<ActionMapper>) -> Content = { jsonContent, actionMappers ->
    Medium(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
        placeholder = jsonContent.placeholder.asPlaceholder(),
    )
}

private val bigMessageSingleActionMapper: (JsonContent, List<ActionMapper>) -> Content = { jsonContent, actionMappers ->
    BigSingleAction(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
        placeholder = jsonContent.placeholder.asPlaceholder(),
        primaryActionText = jsonContent.primaryActionText.failIfEmpty(),
        primaryAction = jsonContent.primaryAction!!.toAction(actionMappers),
    )
}

private val bigMessageTwoActionMapper: (JsonContent, List<ActionMapper>) -> Content = { jsonContent, actionMappers ->
    BigTwoActions(
        titleText = jsonContent.titleText.failIfEmpty(),
        descriptionText = jsonContent.descriptionText.failIfEmpty(),
        placeholder = jsonContent.placeholder.asPlaceholder(),
        primaryActionText = jsonContent.primaryActionText.failIfEmpty(),
        primaryAction = jsonContent.primaryAction!!.toAction(actionMappers),
        secondaryActionText = jsonContent.secondaryActionText.failIfEmpty(),
        secondaryAction = jsonContent.secondaryAction!!.toAction(actionMappers),
    )
}

// plugin point?
private val messageMappers = mapOf(
    Pair(SMALL.jsonValue, smallMapper),
    Pair(MEDIUM.jsonValue, mediumMapper),
    Pair(BIG_SINGLE_ACTION.jsonValue, bigMessageSingleActionMapper),
    Pair(BIG_TWO_ACTION.jsonValue, bigMessageTwoActionMapper),
)

fun List<JsonRemoteMessage>.mapToRemoteMessage(
    locale: Locale,
    actionMappers: List<ActionMapper>
): List<RemoteMessage> = this.mapNotNull { it.map(locale, actionMappers) }

private fun JsonRemoteMessage.map(
    locale: Locale,
    actionMappers: List<ActionMapper>
): RemoteMessage? {
    return runCatching {
        val remoteMessage = RemoteMessage(
            id = this.id.failIfEmpty(),
            content = this.content!!.mapToContent(this.content.messageType, actionMappers),
            matchingRules = this.matchingRules.orEmpty(),
            exclusionRules = this.exclusionRules.orEmpty(),
        )
        remoteMessage.localizeMessage(this.translations, locale)
    }.onFailure {
        Timber.e("RMF: error $it")
    }.getOrNull()
}

private fun RemoteMessage.localizeMessage(translations: Map<String, JsonContentTranslations>?, locale: Locale): RemoteMessage {
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
    actionMappers: List<ActionMapper>
): Content {
    return messageMappers[messageType]?.invoke(this, actionMappers) ?: throw IllegalArgumentException("Message type not found")
}

private fun JsonMessageAction.toAction(actionMappers: List<ActionMapper>): Action {
    actionMappers.forEach {
        val result = it.mapper.invoke(this)
        if (result != null) return result
    }

    throw IllegalArgumentException("Unknown Action Type")
}

private fun String.failIfEmpty() = this.ifEmpty { throw IllegalStateException("Empty argument") }

private fun String.asPlaceholder(): Placeholder = Placeholder.from(this)

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
    }
}
