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

import android.content.Intent
import com.duckduckgo.remote.messaging.api.Content.MessageType.BIG_SINGLE_ACTION
import com.duckduckgo.remote.messaging.api.Content.MessageType.BIG_TWO_ACTION
import com.duckduckgo.remote.messaging.api.Content.MessageType.MEDIUM
import com.duckduckgo.remote.messaging.api.Content.MessageType.SMALL
import com.duckduckgo.remote.messaging.api.JsonActionType.APP_NAVIGATION
import com.duckduckgo.remote.messaging.api.JsonActionType.APP_TP_ONBOARDING
import com.duckduckgo.remote.messaging.api.JsonActionType.DEFAULT_BROWSER
import com.duckduckgo.remote.messaging.api.JsonActionType.DISMISS
import com.duckduckgo.remote.messaging.api.JsonActionType.PLAYSTORE
import com.duckduckgo.remote.messaging.api.JsonActionType.URL

data class RemoteMessage(
    val id: String,
    val content: Content,
    val matchingRules: List<Int>,
    val exclusionRules: List<Int>,
)

sealed class Content(val messageType: MessageType) {
    data class Small(val titleText: String, val descriptionText: String) : Content(SMALL)
    data class Medium(val titleText: String, val descriptionText: String, val placeholder: Placeholder) : Content(MEDIUM)
    data class BigSingleAction(
        val titleText: String,
        val descriptionText: String,
        val placeholder: Placeholder,
        val primaryActionText: String,
        val primaryAction: Action,
    ) : Content(BIG_SINGLE_ACTION)

    data class BigTwoActions(
        val titleText: String,
        val descriptionText: String,
        val placeholder: Placeholder,
        val primaryActionText: String,
        val primaryAction: Action,
        val secondaryActionText: String,
        val secondaryAction: Action,
    ) : Content(BIG_TWO_ACTION)

    enum class MessageType {
        SMALL,
        MEDIUM,
        BIG_SINGLE_ACTION,
        BIG_TWO_ACTION,
    }

    enum class Placeholder(val jsonValue: String) {
        ANNOUNCE("Announce"),
        DDG_ANNOUNCE("DDGAnnounce"),
        CRITICAL_UPDATE("CriticalUpdate"),
        APP_UPDATE("AppUpdate"),
        ;

        companion object {
            fun from(jsonValue: String): Placeholder {
                return values().first { it.jsonValue == jsonValue }
            }
        }
    }
}

sealed class Action(val actionType: String, open val value: String) {
    data class Url(override val value: String) : Action(URL.jsonValue, value)
    data class PlayStore(override val value: String) : Action(PLAYSTORE.jsonValue, value)
    object DefaultBrowser : Action(DEFAULT_BROWSER.jsonValue, "")
    object Dismiss : Action(DISMISS.jsonValue, "")
    data class AppNavigation(val intent: Intent, override val value: String) : Action(APP_NAVIGATION.jsonValue, value)
    object AppTpOnboarding : Action(APP_TP_ONBOARDING.jsonValue, "")
}
