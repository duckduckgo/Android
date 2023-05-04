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
import com.duckduckgo.remote.messaging.api.JsonMessageAction
import com.duckduckgo.remote.messaging.api.MessageActionMapperPlugin
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.lang.IllegalStateException

class ActionAdapter constructor(
    private val actionMappers: Set<MessageActionMapperPlugin>,
) {
    @ToJson fun userProfileToJson(model: Action): ActionJson {
        return ActionJson(model.actionType, model.value)
    }

    @FromJson fun userProfileFromJson(json: ActionJson): Action {
        val jsonAction = JsonMessageAction(json.actionType, json.value)
        actionMappers.forEach {
            val action = it.evaluate(jsonAction)
            if (action != null) return action
        }
        throw IllegalStateException("No mapper found for action type ${json.actionType}")
    }

    class ActionJson(val actionType: String, val value: String)
}

