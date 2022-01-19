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
import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.api.RemoteMessage

object RemoteMessageOM {
    fun urlAction(
        url: String = "http://example.com"
    ) = Action.Url(value = url)

    fun smallContent(
        titleText: String = "title",
        descriptionText: String = "description"
    ) = Content.Small(
        titleText = titleText,
        descriptionText = descriptionText
    )

    fun mediumContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: String = "placeholder"
    ) = Content.Medium(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder
    )

    fun bigSingleActionContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: String = "placeholder",
        primaryActionText: String = "Action1",
        primaryAction: Action = urlAction()
    ) = Content.BigSingleAction(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        primaryActionText = primaryActionText,
        primaryAction = primaryAction
    )

    fun bigTwoActionsContent(
        titleText: String = "title",
        descriptionText: String = "description",
        placeholder: String = "placeholder",
        primaryActionText: String = "Action1",
        primaryAction: Action = urlAction(),
        secondaryActionText: String = "Action2",
        secondaryAction: Action = urlAction()
    ) = Content.BigTwoActions(
        titleText = titleText,
        descriptionText = descriptionText,
        placeholder = placeholder,
        primaryActionText = primaryActionText,
        primaryAction = primaryAction,
        secondaryActionText = secondaryActionText,
        secondaryAction = secondaryAction
    )

    fun aSmallMessage(
        id: String = "id",
        messageType: String = "small",
        content: Content = smallContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList()
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            messageType = messageType,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules
        )
    }

    fun aMediumMessage(
        id: String = "id",
        messageType: String = "medium",
        content: Content = mediumContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList()
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            messageType = messageType,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules
        )
    }

    fun aBigSingleActionMessage(
        id: String = "id",
        messageType: String = "big_single_action",
        content: Content = bigSingleActionContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList()
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            messageType = messageType,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules
        )
    }

    fun aBigTwoActionsMessage(
        id: String = "id",
        messageType: String = "big_two_action",
        content: Content = bigTwoActionsContent(),
        exclusionRules: List<Int> = emptyList(),
        matchingRules: List<Int> = emptyList()
    ): RemoteMessage {
        return RemoteMessage(
            id = id,
            messageType = messageType,
            content = content,
            exclusionRules = exclusionRules,
            matchingRules = matchingRules
        )
    }
}
