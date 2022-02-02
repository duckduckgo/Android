/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.remote.messaging.api

import com.duckduckgo.remote.messaging.api.Action.ActionType.DISMISS
import com.duckduckgo.remote.messaging.api.Action.ActionType.PLAYSTORE
import com.duckduckgo.remote.messaging.api.Action.ActionType.URL
import com.duckduckgo.remote.messaging.api.Content.MessageType
import com.duckduckgo.remote.messaging.api.Content.MessageType.BIG_SINGLE_ACTION
import com.duckduckgo.remote.messaging.api.Content.MessageType.BIG_TWO_ACTION
import com.duckduckgo.remote.messaging.api.Content.MessageType.MEDIUM
import com.duckduckgo.remote.messaging.api.Content.MessageType.SMALL

data class RemoteMessage(
    val id: String,
    val content: Content,
    val matchingRules: List<Int>,
    val exclusionRules: List<Int>
)

sealed class Content(val messageType: MessageType) {
    data class Small(val titleText: String, val descriptionText: String) : Content(SMALL)
    data class Medium(val titleText: String, val descriptionText: String, val placeholder: String) : Content(MEDIUM)
    data class BigSingleAction(
        val titleText: String,
        val descriptionText: String,
        val placeholder: String,
        val primaryActionText: String,
        val primaryAction: Action
    ) : Content(BIG_SINGLE_ACTION)

    data class BigTwoActions(
        val titleText: String,
        val descriptionText: String,
        val placeholder: String,
        val primaryActionText: String,
        val primaryAction: Action,
        val secondaryActionText: String,
        val secondaryAction: Action
    ) : Content(BIG_TWO_ACTION)

    enum class MessageType {
        SMALL,
        MEDIUM,
        BIG_SINGLE_ACTION,
        BIG_TWO_ACTION
    }
}

sealed class Action(val actionType: ActionType) {
    data class Url(val value: String) : Action(URL)
    data class PlayStore(val value: String) : Action(PLAYSTORE)
    // Using data class instead of Object. Object can't be serialized
    data class Dismiss(private val value: String = "") : Action(DISMISS)

    enum class ActionType {
        URL,
        PLAYSTORE,
        DISMISS
    }
}
