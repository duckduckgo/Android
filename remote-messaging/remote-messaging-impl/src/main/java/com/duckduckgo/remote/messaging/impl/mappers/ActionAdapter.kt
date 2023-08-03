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

package com.duckduckgo.remote.messaging.impl.mappers

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.JsonActionType
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

class ActionAdapter constructor(
    private val actionMappers: Set<MessageActionMapperPlugin>,
) {
    @ToJson fun actionToJson(model: Action): ActionJson {
        return ActionJson(model.actionType, model.value, model.additionalParameters)
    }

    @FromJson fun actionFromJson(json: ActionJson): Action {
        val type = mapTypeBackwardsCompatible(json.actionType)
        val jsonAction = JsonMessageAction(type, json.value, json.additionalParameters)
        actionMappers.forEach {
            val action = it.evaluate(jsonAction)
            if (action != null) return action
        }
        throw IllegalStateException("No mapper found for action type ${json.actionType}")
    }

    // We will remove this method when it's safe (e.g: no current or incoming messages)
    private fun mapTypeBackwardsCompatible(actionType: String): String {
        return when (actionType) {
            OldActionType.DEFAULT_BROWSER.toString() -> JsonActionType.DEFAULT_BROWSER.jsonValue
            OldActionType.DISMISS.toString() -> JsonActionType.DISMISS.jsonValue
            OldActionType.APP_TP_ONBOARDING.toString() -> JsonActionType.APP_TP_ONBOARDING.jsonValue
            OldActionType.PLAYSTORE.toString() -> JsonActionType.PLAYSTORE.jsonValue
            OldActionType.URL.toString() -> JsonActionType.URL.jsonValue
            else -> actionType
        }
    }

    class ActionJson(
        val actionType: String,
        val value: String,
        val additionalParameters: Map<String, String>? = null,
    )

    private enum class OldActionType {
        URL,
        PLAYSTORE,
        DEFAULT_BROWSER,
        DISMISS,
        APP_TP_ONBOARDING,
    }
}
