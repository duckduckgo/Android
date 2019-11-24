/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.trackerdetection.model.Action
import com.duckduckgo.app.trackerdetection.model.Rule
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

class TdsJson {
    lateinit var trackers: Map<String, TdsJsonTracker>
}

fun Map<String, TdsJsonTracker>.toTdsTrackers(): Map<String, TdsTracker> {
    return mapNotNull { (key, value) ->
        value.toTdsTracker()?.let {
            return@let key to it
        }
    }.toMap()
}

data class TdsJsonTracker(
    val domain: String?,
    val default: Action?,
    val owner: TdsJsonOwner?,
    val rules: List<Rule>?
) {

    fun toTdsTracker(): TdsTracker? {
        if (domain == null || default == null || owner == null) return null
        return TdsTracker(domain, default, owner.name, rules ?: emptyList())
    }
}

data class TdsJsonOwner(
    val name: String
)

class ActionJsonAdapter {

    @ToJson
    fun toJson(action: Action): String {
        return action.name.toLowerCase()
    }

    @FromJson
    fun fromJson(actionName: String): Action? {
        return Action.values().firstOrNull { it.name == actionName.toUpperCase() }
    }
}