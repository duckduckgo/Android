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

package com.duckduckgo.app.remotemessage.impl

import com.duckduckgo.app.remotemessage.impl.messages.Action
import com.duckduckgo.app.remotemessage.impl.messages.Content
import com.duckduckgo.app.remotemessage.impl.messages.RemoteMessage
import timber.log.Timber


class JsonRemoteMessageMapper {
    private val smallMapper: (JsonContent) -> Content = { jsonContent ->
        Content.Small(
            titleText = jsonContent.titleText,
            descriptionText = jsonContent.descriptionText
        )
    }

    private val mediumMapper: (JsonContent) -> Content = { jsonContent ->
        Content.Medium(
            titleText = jsonContent.titleText,
            descriptionText = jsonContent.descriptionText,
            placeholder = jsonContent.placeholder
        )
    }

    private val bigMessageSingleAcionMapper: (JsonContent) -> Content = { jsonContent ->
        Content.BigSingleAction(
            titleText = jsonContent.titleText,
            descriptionText = jsonContent.descriptionText,
            placeholder = jsonContent.placeholder,
            primaryActionText = jsonContent.primaryActionText,
            primaryAction = jsonContent.primaryAction.toAction()
        )
    }

    private val bigMessageTwoAcionMapper: (JsonContent) -> Content = { jsonContent ->
        Content.BigTwoActions(
            titleText = jsonContent.titleText,
            descriptionText = jsonContent.descriptionText,
            placeholder = jsonContent.placeholder,
            primaryActionText = jsonContent.primaryActionText,
            primaryAction = jsonContent.primaryAction.toAction(),
            secondaryActionText = jsonContent.secondaryActionText,
            secondaryAction = jsonContent.secondaryAction.toAction()
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
        Action.Dismiss
    }

    private val playStoreActionMapper: (JsonMessageAction) -> Action = {
        Action.PlayStore(it.value)
    }

    private val actionMappers = mapOf(
        Pair("url", urlActionMapper),
        Pair("dismiss", dismissActionMapper),
        Pair("playstore", playStoreActionMapper)
    )

    fun map(jsonMessages: List<JsonRemoteMessage>) = jsonMessages.mapNotNull { it.map() }

    private fun JsonRemoteMessage.map(): RemoteMessage? {
        return runCatching {
            RemoteMessage(
                id = this.id,
                messageType = this.messageType,
                content = this.content.mapToContent(this.messageType),
                matchingRules = this.matchingRules.orEmpty(),
                exclusionRules = this.exclusionRules.orEmpty()
            )
        }.onFailure {
            Timber.i("RMF: error $it")
        }.getOrNull()
    }

    private fun JsonContent.mapToContent(messageType: String): Content {
        return messageMappers[messageType]?.invoke(this) ?: throw IllegalArgumentException("Message type not found")
    }

    private fun JsonMessageAction.toAction(): Action {
        return actionMappers[this.type]?.invoke(this) ?: throw IllegalArgumentException("Unknown Action Type")
    }
}
