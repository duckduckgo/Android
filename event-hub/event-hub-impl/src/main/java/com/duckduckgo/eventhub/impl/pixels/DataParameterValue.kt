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

import org.json.JSONObject

/** The value a data parameter takes from [eventData]: `data[dataKey]` as compact JSON, or null when the key is absent. */
internal fun dataParameterValue(eventData: JSONObject?, dataKey: String): String? {
    if (eventData == null || !eventData.has(dataKey)) return null
    return canonicalJson(eventData.opt(dataKey))
}

/**
 * Percent-encodes [value] once, leaving only RFC 3986 unreserved characters as they are. URLEncoder isn't used because it
 * turns a space into `+`, which percent-decoding at the endpoint wouldn't turn back into a space.
 */
internal fun percentEncode(value: String): String = buildString {
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in "-_.~") append(char) else append("%%%02X".format(code))
    }
}
