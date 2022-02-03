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
import com.duckduckgo.remote.messaging.api.Content.Placeholder
import com.duckduckgo.remote.messaging.api.RemoteMessage
import com.duckduckgo.remote.messaging.impl.models.JsonContent
import com.duckduckgo.remote.messaging.impl.models.JsonMessageAction
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessage
import timber.log.Timber

class JsonRemoteMessageMapper {
    private val smallMapper: (JsonContent) -> Content = { jsonContent ->
        Content.Small(
            titleText = jsonContent.titleText.failIfEmpty(),
            descriptionText = jsonContent.descriptionText.failIfEmpty()
        )
    }

    private val mediumMapper: (JsonContent) -> Content = { jsonContent ->
        Content.Medium(
            titleText = jsonContent.titleText.failIfEmpty(),
            descriptionText = jsonContent.descriptionText.failIfEmpty(),
            placeholder = jsonContent.placeholder.asPlaceholder()
        )
    }

    private val bigMessageSingleAcionMapper: (JsonContent) -> Content = { jsonContent ->
        Content.BigSingleAction(
            titleText = jsonContent.titleText.failIfEmpty(),
            descriptionText = jsonContent.descriptionText.failIfEmpty(),
            placeholder = jsonContent.placeholder.asPlaceholder(),
            primaryActionText = jsonContent.primaryActionText.failIfEmpty(),
            primaryAction = jsonContent.primaryAction!!.toAction()
        )
    }

    private val bigMessageTwoAcionMapper: (JsonContent) -> Content = { jsonContent ->
        Content.BigTwoActions(
            titleText = jsonContent.titleText.failIfEmpty(),
            descriptionText = jsonContent.descriptionText.failIfEmpty(),
            placeholder = jsonContent.placeholder.asPlaceholder(),
            primaryActionText = jsonContent.primaryActionText.failIfEmpty(),
            primaryAction = jsonContent.primaryAction!!.toAction(),
            secondaryActionText = jsonContent.secondaryActionText.failIfEmpty(),
            secondaryAction = jsonContent.secondaryAction!!.toAction()
        )
    }

    private val messageMappers = mapOf(
        Pair("small", smallMapper),
        Pair("medium", mediumMapper),
        Pair("big_single_action", bigMessageSingleAcionMapper),
        Pair("big_two_action", bigMessageTwoAcionMapper)
    )

    private val urlActionMapper: (JsonMessageAction) -> Action = {
        Action.Url(it.value)
    }

    private val dismissActionMapper: (JsonMessageAction) -> Action = {
        Action.Dismiss()
    }

    private val playStoreActionMapper: (JsonMessageAction) -> Action = {
        Action.PlayStore(it.value)
    }

    private val defaultBrowserActionMapper: (JsonMessageAction) -> Action = {
        Action.DefaultBrowser()
    }

    private val actionMappers = mapOf(
        Pair("url", urlActionMapper),
        Pair("dismiss", dismissActionMapper),
        Pair("playstore", playStoreActionMapper),
        Pair("defaultBrowser", defaultBrowserActionMapper)
    )

    fun map(jsonMessages: List<JsonRemoteMessage>): List<RemoteMessage> = jsonMessages.mapNotNull { it.map() }

    private fun JsonRemoteMessage.map(): RemoteMessage? {
        return runCatching {
            RemoteMessage(
                id = this.id.failIfEmpty(),
                content = this.content!!.mapToContent(this.content.messageType),
                matchingRules = this.matchingRules.orEmpty(),
                exclusionRules = this.exclusionRules.orEmpty()
            )
        }.onFailure {
            Timber.e("RMF: error $it")
        }.getOrNull()
    }

    private fun JsonContent.mapToContent(messageType: String): Content {
        return messageMappers[messageType]?.invoke(this) ?: throw IllegalArgumentException("Message type not found")
    }

    private fun JsonMessageAction.toAction(): Action {
        return actionMappers[this.type]?.invoke(this) ?: throw IllegalArgumentException("Unknown Action Type")
    }

    private fun String.failIfEmpty() = this.ifEmpty { throw IllegalStateException("Empty argument") }

    private fun String.asPlaceholder(): Placeholder = Placeholder.from(this)
}
