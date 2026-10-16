/*
 * Copyright (c) 2026 DuckDuckGo
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
import com.duckduckgo.app.trackerdetection.model.Action.UNSUPPORTED
import com.squareup.moshi.FromJson
import java.util.Locale

class ActionJsonAdapter {

    @FromJson
    fun fromJson(actionName: String): Action {
        // If action not null but not supported, return unsupported.
        // Unsupported actions are always ignored.
        return Action.values().firstOrNull { it.name == actionName.uppercase(Locale.ROOT) } ?: UNSUPPORTED
    }
}
