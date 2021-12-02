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

import org.json.JSONObject

data class JsonRemoteMessagingConfig(
    val version: Long,
    val messages: List<Message>,
    val matchingRules: List<MatchingRule>
)

data class Message(
    val id: String,
    val messageType: String,
    val content: JSONObject?,
    val exclusionRules: List<Int>?,
    val matchingRules: List<Int>?
)

data class MatchingRule(
    val id: Int,
    val attributes: Map<String, JSONObject?>
)

data class ActionJson(
    val type: String,
    val value: String
)
