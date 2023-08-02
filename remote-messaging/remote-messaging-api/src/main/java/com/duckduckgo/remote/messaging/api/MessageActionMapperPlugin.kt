/*
 * Copyright (c) 2023 DuckDuckGo
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

interface MessageActionMapperPlugin {
    fun evaluate(jsonMessageAction: JsonMessageAction): Action?
}

data class JsonMessageAction(
    val type: String,
    val value: String,
    val additionalParameters: Map<String, String>?,
)

sealed class JsonActionType(val jsonValue: String) {
    object URL : JsonActionType("url")
    object PLAYSTORE : JsonActionType("playstore")
    object DEFAULT_BROWSER : JsonActionType("defaultBrowser")
    object DISMISS : JsonActionType("dismiss")
    object APP_TP_ONBOARDING : JsonActionType("atpOnboarding")
    object SHARE : JsonActionType("share")
}
