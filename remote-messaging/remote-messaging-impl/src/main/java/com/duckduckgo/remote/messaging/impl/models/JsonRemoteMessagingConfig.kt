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

package com.duckduckgo.remote.messaging.impl.models

data class JsonRemoteMessagingConfig(
    val version: Long,
    val messages: List<JsonRemoteMessage>,
    val rules: List<JsonMatchingRule>
)

data class JsonRemoteMessage(
    val id: String,
    val content: JsonContent?,
    val exclusionRules: List<Int>?,
    val matchingRules: List<Int>?
)

data class JsonContent(
    val messageType: String = "",
    val titleText: String = "",
    val descriptionText: String = "",
    val placeholder: String = "",
    val primaryActionText: String = "",
    val primaryAction: JsonMessageAction? = null,
    val secondaryActionText: String = "",
    val secondaryAction: JsonMessageAction? = null
)

data class JsonMatchingRule(
    val id: Int,
    val attributes: Map<String, JsonMatchingAttribute>
)

data class JsonMatchingAttribute(
    val value: Any? = null,
    val min: Any? = null,
    val max: Any? = null,
    val since: Any? = null,
    val fallback: Boolean? = null
)

data class JsonMessageAction(
    val type: String,
    val value: String
)

sealed class JsonActionType(val jsonValue: String) {
    object URL : JsonActionType("url")
    object PLAYSTORE : JsonActionType("playstore")
    object DEFAULT_BROWSER : JsonActionType("defaultBrowser")
    object DISMISS : JsonActionType("dismiss")
}

sealed class JsonMessageType(val jsonValue: String) {
    object SMALL : JsonMessageType("small")
    object MEDIUM : JsonMessageType("medium")
    object BIG_SINGLE_ACTION : JsonMessageType("big_single_action")
    object BIG_TWO_ACTION : JsonMessageType("big_two_action")
}
