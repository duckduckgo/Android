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

import com.squareup.moshi.JsonClass
import org.json.JSONObject

data class JsonRemoteMessagingConfig(
    val version: Long,
    val messages: List<JsonRemoteMessage>,
    val matchingRules: List<JsonMatchingRule>
)

data class JsonRemoteMessage(
    val id: String,
    val messageType: String,
    val content: JsonContent,
    val exclusionRules: List<Int>?,
    val matchingRules: List<Int>?
)

sealed class JsonMessageContent {
    data class Small(val titleText: String, val descriptionText: String) : JsonMessageContent()
    data class Medium(val titleText: String, val descriptionText: String, val placeholder: String) : JsonMessageContent()
    data class BigSingleAction(val titleText: String, val descriptionText: String, val placeholder: String, val primaryActionText: String, val primaryAction: JsonMessageAction) : JsonMessageContent()
    data class BigTwoActions(val titleText: String, val descriptionText: String, val placeholder: String, val primaryActionText: String, val primaryAction: JsonMessageAction, val secondaryActionText: String, val secondaryAction: JsonMessageAction) : JsonMessageContent()
}

data class JsonContent(
    val titleText: String,
    val descriptionText: String,
    val placeholder: String,
    val primaryActionText: String,
    val primaryAction: JsonMessageAction,
    val secondaryActionText: String,
    val secondaryAction: JsonMessageAction
)

data class JsonMatchingRule(
    val id: Int,
    val attributes: Map<String, JsonMatchingAttribute>
)

data class JsonMatchingAttribute(
    val value: Any?,
    val min: Any?,
    val max: Any?,
    val since: Any?,
    val fallback: Boolean?
)

data class JsonMessageAction(
    val type: String,
    val value: String
)
