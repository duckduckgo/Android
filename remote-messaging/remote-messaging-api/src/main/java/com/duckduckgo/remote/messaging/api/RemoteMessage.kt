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

import com.duckduckgo.remote.messaging.api.Content.MessageType.BIG_SINGLE_ACTION
import com.duckduckgo.remote.messaging.api.Content.MessageType.BIG_TWO_ACTION
import com.duckduckgo.remote.messaging.api.Content.MessageType.MEDIUM
import com.duckduckgo.remote.messaging.api.Content.MessageType.PROMO_SINGLE_ACTION
import com.duckduckgo.remote.messaging.api.Content.MessageType.SMALL
import com.duckduckgo.remote.messaging.api.Content.Placeholder
import com.duckduckgo.remote.messaging.api.JsonActionType.APP_TP_ONBOARDING
import com.duckduckgo.remote.messaging.api.JsonActionType.DEFAULT_BROWSER
import com.duckduckgo.remote.messaging.api.JsonActionType.DISMISS
import com.duckduckgo.remote.messaging.api.JsonActionType.NAVIGATION
import com.duckduckgo.remote.messaging.api.JsonActionType.PLAYSTORE
import com.duckduckgo.remote.messaging.api.JsonActionType.SHARE
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

    data class PromoSingleAction(
        val titleText: String,
        val descriptionText: String,
        val placeholder: Placeholder,
        val actionText: String,
        val action: Action,
    ) : Content(PROMO_SINGLE_ACTION)

    data class CardsList(
        val titleText: String,
        val descriptionText: String,
        val placeholder: Placeholder,
        val primaryActionText: String,
        val primaryAction: Action,
        val listItems: List<CardItem>,
    ) : Content(MessageType.CARDS_LIST)

    enum class MessageType {
        SMALL,
        MEDIUM,
        BIG_SINGLE_ACTION,
        BIG_TWO_ACTION,
        PROMO_SINGLE_ACTION,
        CARDS_LIST,
    }

    enum class Placeholder(val jsonValue: String) {
        ANNOUNCE("Announce"),
        DDG_ANNOUNCE("DDGAnnounce"),
        CRITICAL_UPDATE("CriticalUpdate"),
        APP_UPDATE("AppUpdate"),
        MAC_AND_WINDOWS("NewForMacAndWindows"),
        PRIVACY_SHIELD("PrivacyShield"),
        DUCK_AI_OLD("Duck.ai"),
        DUCK_AI("DuckAi"),
        VISUAL_DESIGN_UPDATE("VisualDesignUpdate"),
        IMAGE_AI("ImageAI"),
        RADAR("Radar"),
        KEY_IMPORT("KeyImport"),
        ;

        companion object {
            fun from(jsonValue: String): Placeholder {
                return values().first { it.jsonValue == jsonValue }
            }
        }
    }
}

sealed class Action(val actionType: String, open val value: String, open val additionalParameters: Map<String, String>?) {
    data class Url(override val value: String) : Action(URL.jsonValue, value, null)
    data class UrlInContext(override val value: String) : Action(JsonActionType.URL_IN_CONTEXT.jsonValue, value, null)
    data class PlayStore(override val value: String) : Action(PLAYSTORE.jsonValue, value, null)
    data object DefaultBrowser : Action(DEFAULT_BROWSER.jsonValue, "", null)
    data object Dismiss : Action(DISMISS.jsonValue, "", null)
    data object AppTpOnboarding : Action(APP_TP_ONBOARDING.jsonValue, "", null)
    data class Share(
        override val value: String,
        override val additionalParameters: Map<String, String>?,
    ) : Action(SHARE.jsonValue, value, additionalParameters) {
        val title: String
            get() = additionalParameters?.get(AdditionalParameter.TITLE.key).orEmpty()

        private enum class AdditionalParameter(val key: String) {
            TITLE("title"),
        }
    }
    data class Navigation(
        override val value: String,
        override val additionalParameters: Map<String, String>?,
    ) : Action(NAVIGATION.jsonValue, value, additionalParameters)

    data class Survey(
        override val value: String,
        override val additionalParameters: Map<String, String>?,
    ) : Action(JsonActionType.SURVEY.jsonValue, value, additionalParameters)
}

data class CardItem(
    val id: String,
    val type: CardItemType,
    val titleText: String,
    val descriptionText: String,
    val placeholder: Placeholder,
    val primaryAction: Action,
)

enum class CardItemType(val jsonValue: String) {
    TWO_LINE_LIST_ITEM("two_line_list_item"),
    ;

    companion object {
        fun from(jsonValue: String): CardItemType {
            return CardItemType.values().first { it.jsonValue == jsonValue }
        }
    }
}
