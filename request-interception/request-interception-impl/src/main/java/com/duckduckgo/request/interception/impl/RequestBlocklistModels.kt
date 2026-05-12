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

package com.duckduckgo.request.interception.impl

import com.duckduckgo.app.browser.Domain

data class BlockedRequestEntry(
    val rules: List<Map<String, @JvmSuppressWildcards Any>>?,
)

class BlocklistRuleEntity(
    val rule: Regex,
    val applyToAllDomains: Boolean,
    val domains: List<Domain>,
    val reason: String?,
) {
    companion object {
        private const val PROPERTY_RULE = "rule"
        private const val PROPERTY_DOMAINS = "domains"
        private const val PROPERTY_REASON = "reason"

        private val KNOWN_PROPERTIES = setOf(PROPERTY_RULE, PROPERTY_DOMAINS, PROPERTY_REASON)

        @Suppress("UNCHECKED_CAST")
        fun fromJson(map: Map<String, Any>): BlocklistRuleEntity? {
            if (map.keys.any { it !in KNOWN_PROPERTIES }) return null

            val ruleString = map[PROPERTY_RULE] as? String ?: return null
            val domainsString = map[PROPERTY_DOMAINS] as? List<String> ?: return null
            val reason = map[PROPERTY_REASON] as? String

            val rule = buildString {
                for (char in ruleString) {
                    if (char == '*') {
                        append("[^/]*")
                    } else {
                        append(Regex.escape(char.toString()))
                    }
                }
            }.toRegex()

            return BlocklistRuleEntity(
                rule = rule,
                applyToAllDomains = domainsString.contains(ALL_DOMAINS_RULE),
                domains = domainsString.filter { it != ALL_DOMAINS_RULE }.map { Domain(it) },
                reason = reason,
            )
        }
    }
}

private const val ALL_DOMAINS_RULE = "<all>"
