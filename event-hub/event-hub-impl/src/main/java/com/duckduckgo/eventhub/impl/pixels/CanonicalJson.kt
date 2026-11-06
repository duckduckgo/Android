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

package com.duckduckgo.eventhub.impl.pixels

import org.json.JSONArray
import org.json.JSONObject

/**
 * Encodes a value parsed by org.json so that values equal as JSON values produce the same string:
 * object keys are sorted recursively, array order is kept, and there is no insignificant whitespace.
 */
internal fun canonicalJson(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
        "${JSONObject.quote(key)}:${canonicalJson(value.opt(key))}"
    }
    is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]", separator = ",") { index ->
        canonicalJson(value.opt(index))
    }
    is Number -> JSONObject.numberToString(value)
    is Boolean -> value.toString()
    else -> JSONObject.quote(value.toString())
}
