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

import org.json.JSONObject

data class ExecuteClientToolRequest(
    val callId: String,
    val name: String,
    val version: Long,
    val input: JSONObject,
)

sealed interface ParsedExecuteClientToolRequest {
    val callId: String?

    data class Valid(val request: ExecuteClientToolRequest) : ParsedExecuteClientToolRequest {
        override val callId: String = request.callId
    }

    data class Malformed(override val callId: String?) : ParsedExecuteClientToolRequest

    companion object {
        private val CALL_ID_PATTERN = Regex("^[A-Za-z0-9_.:-]{1,128}$")
        private val CAPABILITY_NAME_PATTERN = Regex("^[A-Za-z0-9_.:-]{1,64}$")

        fun parse(params: JSONObject?): ParsedExecuteClientToolRequest {
            val rawCallId = params?.opt("callId") as? String
            val callId = rawCallId?.takeIf { CALL_ID_PATTERN.matches(it) } ?: return Malformed(rawCallId)
            val capability = params.opt("capability") as? JSONObject ?: return Malformed(callId)
            val name = (capability.opt("name") as? String)?.takeIf { CAPABILITY_NAME_PATTERN.matches(it) } ?: return Malformed(callId)
            val version = (capability.opt("version") as? Number)?.toWholeLong()?.takeIf { it > 0 } ?: return Malformed(callId)
            val input = params.opt("input") as? JSONObject ?: return Malformed(callId)
            return Valid(ExecuteClientToolRequest(callId, name, version, input))
        }
    }
}

/** The bridge can deliver JSON integers as Double, so a whole Double counts as an integer. */
internal fun Number.toWholeLong(): Long? = when (this) {
    is Int, is Long -> toLong()
    else -> toDouble().takeIf { it % 1.0 == 0.0 }?.toLong()
}
