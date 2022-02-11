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

@file:Suppress("unused")

package com.duckduckgo.remote.messaging.api

import com.duckduckgo.remote.messaging.api.Action.ActionType.DISMISS
import com.duckduckgo.remote.messaging.api.Action.ActionType.PLAYSTORE
import com.duckduckgo.remote.messaging.api.Action.ActionType.DEFAULT_BROWSER
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
    data class Medium(val titleText: String, val descriptionText: String, val placeholder: Placeholder) : Content(MEDIUM)
    data class BigSingleAction(
        val titleText: String,
        val descriptionText: String,
        val placeholder: Placeholder,
        val primaryActionText: String,
        val primaryAction: Action
    ) : Content(BIG_SINGLE_ACTION)

    data class BigTwoActions(
        val titleText: String,
        val descriptionText: String,
        val placeholder: Placeholder,
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

    enum class Placeholder(val jsonValue: String) {
        ANNOUNCE("Announce"),
        DDG_ANNOUNCE("DDGAnnounce"),
        CRITICAL_UPDATE("CriticalUpdate"),
        APP_UPDATE("AppUpdate");

        companion object {
            fun from(jsonValue: String): Placeholder {
                return values().first { it.jsonValue == jsonValue }
            }
        }
    }
}

sealed class Action(val actionType: ActionType) {
    data class Url(val value: String) : Action(URL)
    data class PlayStore(val value: String) : Action(PLAYSTORE)
    // Using data class instead of Object. Object can't be serialized
    data class DefaultBrowser(val value: String = "") : Action(DEFAULT_BROWSER)
    data class Dismiss(val value: String = "") : Action(DISMISS)

    enum class ActionType {
        URL,
        PLAYSTORE,
        DEFAULT_BROWSER,
        DISMISS
    }
}
