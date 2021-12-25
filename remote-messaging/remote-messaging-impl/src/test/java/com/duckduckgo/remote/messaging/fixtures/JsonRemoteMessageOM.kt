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
import com.duckduckgo.remote.messaging.impl.models.JsonMessageAction
import com.duckduckgo.remote.messaging.impl.models.JsonRemoteMessage

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
        titleText = titleText,
        descriptionText = descriptionText
    )

    fun mediumJsonContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: String = "placeholder"
    ) = JsonContent(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder
    )

    fun bigSingleActionJsonContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: String = "placeholder",
        primaryActionText: String = "Action1",
        primaryAction: JsonMessageAction = jsonMessageAction()
    ) = JsonContent(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        primaryActionText = primaryActionText,
        primaryAction = primaryAction,
    )

    fun bigTwoActionJsonContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: String = "placeholder",
        primaryActionText: String = "Action1",
        primaryAction: JsonMessageAction = jsonMessageAction(),
        secondaryActionText: String = "Action2",
        secondaryAction: JsonMessageAction = jsonMessageAction()
    ) = JsonContent(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        primaryActionText = primaryActionText,
        primaryAction = primaryAction,
        secondaryActionText = secondaryActionText,
        secondaryAction = secondaryAction
    )

    fun emptyJsonContent() = JsonContent()

    fun aJsonMessage(
        id: String = "id",
        messageType: String = "small",
        content: JsonContent? = bigSingleActionJsonContent(),
        exclusionRules: List<Int>? = emptyList(),
        matchingRules: List<Int>? = emptyList()
    ) = JsonRemoteMessage(
        id = id,
        messageType = messageType,
        content = content,
        exclusionRules = exclusionRules,
        matchingRules = matchingRules
    )

    fun aSmallJsonMessage(
        id: String = "id",
        messageType: String = "small",
        content: JsonContent = smallJsonContent(),
        exclusionRules: List<Int>? = emptyList(),
        matchingRules: List<Int>? = emptyList()
    ): JsonRemoteMessage {
        return JsonRemoteMessage(
            id = id,
            messageType = messageType,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules
        )
    }

    fun aMediumJsonMessage(
        id: String = "id",
        messageType: String = "medium",
        content: JsonContent = mediumJsonContent(),
        exclusionRules: List<Int>? = emptyList(),
        matchingRules: List<Int>? = emptyList()
    ): JsonRemoteMessage {
        return JsonRemoteMessage(
            id = id,
            messageType = messageType,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules
        )
    }

    fun aBigSingleActionJsonMessage(
        id: String = "id",
        messageType: String = "big_single_action",
        content: JsonContent = bigSingleActionJsonContent(),
        exclusionRules: List<Int>? = emptyList(),
        matchingRules: List<Int>? = emptyList()
    ): JsonRemoteMessage {
        return JsonRemoteMessage(
            id = id,
            messageType = messageType,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules
        )
    }

    fun aBigTwoActionJsonMessage(
        id: String = "id",
        messageType: String = "big_two_action",
        content: JsonContent = bigTwoActionJsonContent(),
        exclusionRules: List<Int>? = emptyList(),
        matchingRules: List<Int>? = emptyList()
    ): JsonRemoteMessage {
        return JsonRemoteMessage(
            id = id,
            messageType = messageType,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules
        )
    }
}