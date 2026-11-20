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

/** The `requestAccess` pseudo-capability: asks for several tools in one prompt and never runs one. */
object RequestAccess {
    const val NAME = "requestAccess"
    const val VERSION = 1

    private const val CAPABILITIES = "capabilities"
    private const val MAX_CAPABILITIES = 12
    private const val MAX_NAME_LENGTH = 64
    private val NOT_REQUESTABLE = setOf(NAME, "loadUserSkill")

    /** The requested capability names in order, or null when [input] is not a valid `requestAccess` input. */
    fun parseCapabilities(input: JSONObject): List<String>? {
        if (input.length() != 1) return null
        val array = input.opt(CAPABILITIES) as? JSONArray ?: return null
        if (array.length() !in 1..MAX_CAPABILITIES) return null
        val names = (0 until array.length()).map { array.opt(it) as? String ?: return null }
        if (names.any { it.length !in 1..MAX_NAME_LENGTH || it in NOT_REQUESTABLE }) return null
        if (names.toSet().size != names.size) return null
        return names
    }
}
