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

package com.duckduckgo.app.remotemessage.impl.messages

import com.duckduckgo.app.remotemessage.impl.JsonMessageAction
import com.duckduckgo.app.remotemessage.impl.JsonMessageContent
import com.duckduckgo.app.remotemessage.impl.matchingattributes.MatchingAttribute

data class RemoteConfig(
    val messages: List<RemoteMessage>,
    val rules: Map<Int, List<MatchingAttribute?>>
)

data class RemoteMessage(
    val id: String,
    val messageType: String,
    val content: Content,
    val matchingRules: List<Int>,
    val exclusionRules: List<Int>
)

sealed class Content {
    data class Small(val titleText: String, val descriptionText: String) : Content()
    data class Medium(val titleText: String, val descriptionText: String, val placeholder: String) : Content()
    data class BigSingleAction(val titleText: String, val descriptionText: String, val placeholder: String, val primaryActionText: String, val primaryAction: JsonMessageAction) : Content()
    data class BigTwoActions(val titleText: String, val descriptionText: String, val placeholder: String, val primaryActionText: String, val primaryAction: JsonMessageAction, val secondaryActionText: String, val secondaryAction: JsonMessageAction) : Content()
}

sealed class Action {
    data class Url(val value: String) : Action()
    data class PlayStore(val value: String) : Action()
    object Dismiss : Action()
}
