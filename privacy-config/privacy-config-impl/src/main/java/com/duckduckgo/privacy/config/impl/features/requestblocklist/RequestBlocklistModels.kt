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

package com.duckduckgo.privacy.config.impl.features.requestblocklist

data class RequestBlocklistSettings(
    val blockedRequests: Map<String, BlockedRequestEntry>?,
)

data class BlockedRequestEntry(
    val rules: List<Map<String, @JvmSuppressWildcards Any>>?,
)

class BlocklistRuleEntity(
    val rule: String,
    val domains: List<String>,
    val reason: String?,
    val ruleRegex: Regex,
) {
    companion object {
        private const val PROPERTY_RULE = "rule"
        private const val PROPERTY_DOMAINS = "domains"
        private const val PROPERTY_REASON = "reason"

        private val KNOWN_PROPERTIES = setOf(PROPERTY_RULE, PROPERTY_DOMAINS, PROPERTY_REASON)

        @Suppress("UNCHECKED_CAST")
        fun fromJson(map: Map<String, Any>): BlocklistRuleEntity? {
            if (map.keys.any { it !in KNOWN_PROPERTIES }) return null
            val rule = map[PROPERTY_RULE] as? String ?: return null
            val domains = map[PROPERTY_DOMAINS] as? List<String> ?: return null
            val reason = map[PROPERTY_REASON] as? String
            return BlocklistRuleEntity(
                rule = rule,
                domains = domains,
                reason = reason,
                ruleRegex = compileRuleRegex(rule),
            )
        }

        private fun compileRuleRegex(rule: String): Regex = buildString {
            for (char in rule) {
                if (char == '*') {
                    append("[^/]*")
                } else {
                    append(Regex.escape(char.toString()))
                }
            }
        }.toRegex()
    }
}
