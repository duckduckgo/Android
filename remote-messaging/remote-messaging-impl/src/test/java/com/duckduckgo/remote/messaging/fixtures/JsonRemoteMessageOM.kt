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

import com.duckduckgo.remote.messaging.impl.models.JsonContent
import com.duckduckgo.remote.messaging.impl.models.JsonContentTranslations
import com.duckduckgo.remote.messaging.impl.models.JsonMatchingRule
import com.duckduckgo.remote.messaging.impl.models.JsonMessageAction
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessage
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessagingConfig

object JsonRemoteMessageOM {
    private fun jsonMessageAction(
        type: String = "url",
        value: String = "http://example.com"
    ) = JsonMessageAction(
        type = type,
        value = value
    )

    fun smallJsonContent(
        titleText: String = "title",
        descriptionText: String = "description"
    ) = JsonContent(
        messageType = "small",
        titleText = titleText,
        descriptionText = descriptionText
    )

    fun mediumJsonContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: String = "Announce"
    ) = JsonContent(
        messageType = "medium",
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder
    )

    fun bigSingleActionJsonContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: String = "Announce",
        primaryActionText: String = "Action1",
        primaryAction: JsonMessageAction = jsonMessageAction()
    ) = JsonContent(
        messageType = "big_single_action",
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        primaryActionText = primaryActionText,
        primaryAction = primaryAction,
    )

    fun bigTwoActionJsonContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: String = "Announce",
        primaryActionText: String = "Action1",
        primaryAction: JsonMessageAction = jsonMessageAction(),
        secondaryActionText: String = "Action2",
        secondaryAction: JsonMessageAction = jsonMessageAction()
    ) = JsonContent(
        messageType = "big_two_action",
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        primaryActionText = primaryActionText,
        primaryAction = primaryAction,
        secondaryActionText = secondaryActionText,
        secondaryAction = secondaryAction
    )

    fun emptyJsonContent(messageType: String = "") = JsonContent(messageType = messageType)

    fun aJsonMessage(
        id: String = "id",
        content: JsonContent? = emptyJsonContent(),
        exclusionRules: List<Int>? = emptyList(),
        matchingRules: List<Int>? = emptyList(),
        translations: Map<String, JsonContentTranslations> = emptyMap()
    ) = JsonRemoteMessage(
        id = id,
        content = content,
        exclusionRules = exclusionRules,
        matchingRules = matchingRules,
        translations = translations
    )

    fun aJsonRemoteMessagingConfig(
        version: Long = 0L,
        messages: List<JsonRemoteMessage> = emptyList(),
        rules: List<JsonMatchingRule> = emptyList()
    ) = JsonRemoteMessagingConfig(
        version = version,
        messages = messages,
        rules = rules
    )
}
