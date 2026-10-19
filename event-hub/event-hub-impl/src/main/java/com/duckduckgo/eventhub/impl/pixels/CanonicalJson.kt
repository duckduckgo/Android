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
 * object keys are sorted recursively, array order is kept, and there is no insignificant whitespace. Strings are escaped as
 * JavaScript's `JSON.stringify` escapes them.
 */
internal fun canonicalJson(value: Any?): String = when (value) {
    null, JSONObject.NULL -> "null"
    is JSONObject -> value.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
        "${quote(key)}:${canonicalJson(value.opt(key))}"
    }
    is JSONArray -> (0 until value.length()).joinToString(prefix = "[", postfix = "]", separator = ",") { index ->
        canonicalJson(value.opt(index))
    }
    is Number -> JSONObject.numberToString(value)
    is Boolean -> value.toString()
    else -> quote(value.toString())
}

/**
 * [JSONObject.quote] isn't used because its escaping differs between Android, which escapes `/`, and the org.json that unit
 * tests run against, which escapes some non-ASCII characters instead, so tests couldn't pin what the device sends.
 */
private fun quote(string: String): String = buildString {
    append('"')
    string.forEachIndexed { index, char ->
        when {
            char == '"' -> append("\\\"")
            char == '\\' -> append("\\\\")
            char == '\b' -> append("\\b")
            char == '\u000C' -> append("\\f")
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            char == '\t' -> append("\\t")
            char < ' ' || char.isLoneSurrogate(string, index) -> append("\\u%04x".format(char.code))
            else -> append(char)
        }
    }
    append('"')
}

private fun Char.isLoneSurrogate(string: String, index: Int): Boolean = when {
    isHighSurrogate() -> string.getOrNull(index + 1)?.isLowSurrogate() != true
    isLowSurrogate() -> string.getOrNull(index - 1)?.isHighSurrogate() != true
    else -> false
}
