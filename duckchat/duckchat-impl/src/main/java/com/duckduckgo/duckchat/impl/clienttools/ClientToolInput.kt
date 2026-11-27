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

package com.duckduckgo.duckchat.impl.clienttools

import org.json.JSONArray
import org.json.JSONObject

/** Reads tool input per the contract schemas. Each reader gives null when the value breaks the schema. Lengths count code points. */
internal object ClientToolInput {

    fun hasOnlyKeys(
        input: JSONObject,
        vararg allowed: String,
    ): Boolean = input.keys().asSequence().all { it in allowed }

    fun string(
        input: JSONObject,
        key: String,
        lengths: IntRange = 0..Int.MAX_VALUE,
    ): String? = (input.opt(key) as? String)?.takeIf { it.codePointLength() in lengths }

    fun stringList(
        input: JSONObject,
        key: String,
        items: IntRange,
        itemLengths: IntRange,
    ): List<String>? {
        val array = input.opt(key) as? JSONArray ?: return null
        if (array.length() !in items) return null
        return (0 until array.length()).map { index ->
            (array.opt(index) as? String)?.takeIf { it.codePointLength() in itemLengths } ?: return null
        }
    }

    /** The integer at [key] within 1..[max], or [default] when it is absent. */
    fun limit(
        input: JSONObject,
        key: String,
        default: Int,
        max: Int,
    ): Int? {
        val value = input.opt(key) ?: return default
        return (value as? Number)?.toWholeLong()?.takeIf { it in 1..max }?.toInt()
    }

    private fun String.codePointLength(): Int = codePointCount(0, length)
}
