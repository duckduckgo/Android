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

package com.duckduckgo.app.trackerdetection

import android.net.Uri
import com.duckduckgo.app.browser.Domain
import com.duckduckgo.app.browser.UriString.Companion.host
import com.duckduckgo.app.browser.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.trackerdetection.model.Action.BLOCK
import com.duckduckgo.app.trackerdetection.model.Action.IGNORE
import com.duckduckgo.app.trackerdetection.model.Rule
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import com.duckduckgo.common.utils.extensions.toTldPlusOne
import logcat.logcat

class TdsClient(
    override val name: Client.ClientName,
    trackers: List<TdsTracker>,
    private val urlToTypeMapper: UrlToTypeMapper,
    private val optimizeTrackerEvaluationV3: Boolean,
    precompileRegex: Boolean = false,
) : Client {

    private val precompileRegex: Boolean = precompileRegex && optimizeTrackerEvaluationV3

    private val compiledTrackers: List<CompiledTracker> = trackers.map { tracker ->
        CompiledTracker(
            tracker = tracker,
            rules = tracker.rules.map { rule ->
                val regex = if (precompileRegex) {
                    runCatching { ".*${rule.rule}.*".toRegex() }
                        .onFailure { logcat { "TDS rule failed to compile, skipping: ${rule.rule} (${it.message})" } }
                        .getOrNull()
                } else {
                    null
                }
                CompiledRule(rule = rule, regex = regex)
            },
        )
    }

    private val compiledTrackerByDomain: Map<String, CompiledTracker> by lazy {
        compiledTrackers.associateBy { it.tracker.domain.value }
    }

    override fun matches(
        url: String,
        documentUrl: Uri,
        requestHeaders: Map<String, String>,
    ): Client.Result {
        val compiled = findCompiledTracker(host(url)) ?: return Client.Result(matches = false, isATracker = false)
        val matches = matchesTrackerEntry(compiled, url, documentUrl, requestHeaders)
        return Client.Result(
            matches = matches.shouldBlock,
            entityName = compiled.tracker.ownerName,
            categories = compiled.tracker.categories,
            surrogate = matches.surrogate,
            isATracker = matches.isATracker,
        )
    }

    override fun matches(
        url: Uri,
        documentUrl: Uri,
        requestHeaders: Map<String, String>,
    ): Client.Result {
        val compiled = findCompiledTracker(url.host) ?: return Client.Result(matches = false, isATracker = false)
        val matches = matchesTrackerEntry(compiled, url.toString(), documentUrl, requestHeaders)
        return Client.Result(
            matches = matches.shouldBlock,
            entityName = compiled.tracker.ownerName,
            categories = compiled.tracker.categories,
            surrogate = matches.surrogate,
            isATracker = matches.isATracker,
        )
    }

    private fun findCompiledTracker(host: String?): CompiledTracker? {
        if (host.isNullOrEmpty()) return null
        return if (optimizeTrackerEvaluationV3) {
            findCompiledTrackerByLabelWalk(host)
        } else {
            val domain = Domain(host)
            compiledTrackers.firstOrNull { sameOrSubdomain(domain, it.tracker.domain) }
        }
    }

    private fun findCompiledTrackerByLabelWalk(host: String): CompiledTracker? {
        val eTldPlusOne = host.toTldPlusOne() ?: return null
        var candidate: String = host
        while (true) {
            compiledTrackerByDomain[candidate]?.let { return it }
            if (candidate == eTldPlusOne) return null
            val dot = candidate.indexOf('.')
            if (dot < 0) return null
            candidate = candidate.substring(dot + 1)
        }
    }

    private fun matchesTrackerEntry(
        compiled: CompiledTracker,
        url: String,
        documentUrl: Uri,
        requestHeaders: Map<String, String>,
    ): MatchedResult {
        compiled.rules.forEach { compiledRule ->
            val rule = compiledRule.rule
            val regex = if (precompileRegex) {
                compiledRule.regex ?: return@forEach
            } else {
                ".*${rule.rule}.*".toRegex()
            }
            if (url.matches(regex)) {
                val type = urlToTypeMapper.map(url, requestHeaders)

                if (rule.options != null) {
                    if (!matchedDomainAndTypes(rule.options.domains, rule.options.types, documentUrl, type)) {
                        // Continue to the next rule instead
                        return@forEach
                    }
                }

                if (rule.exceptions != null) {
                    if (matchedDomainAndTypes(rule.exceptions.domains, rule.exceptions.types, documentUrl, type)) {
                        return MatchedResult(shouldBlock = false, isATracker = true)
                    }
                }

                if (rule.action == IGNORE) {
                    return MatchedResult(shouldBlock = false, isATracker = true)
                }

                if (rule.surrogate?.isNotEmpty() == true) {
                    return MatchedResult(shouldBlock = true, surrogate = rule.surrogate, isATracker = true)
                }
                // Null means no action which we should default to block
                if (rule.action == BLOCK || rule.action == null) {
                    return MatchedResult(shouldBlock = true, isATracker = true)
                }
            }
        }

        return MatchedResult(shouldBlock = (compiled.tracker.defaultAction == BLOCK), isATracker = true)
    }

    private fun matchedDomainAndTypes(
        ruleDomains: List<String>?,
        ruleTypes: List<String>?,
        documentUrl: Uri,
        type: String?,
    ): Boolean {
        val matchesDomain = ruleDomains?.any { domain -> sameOrSubdomain(documentUrl, domain) }
        val matchesType = ruleTypes?.contains(type)

        return when {
            ruleTypes.isNullOrEmpty() && matchesDomain == true -> {
                true
            }
            ruleDomains.isNullOrEmpty() && matchesType == true -> {
                true
            }
            matchesDomain == true && matchesType == true -> {
                true
            }
            else -> false
        }
    }

    private data class CompiledRule(
        val rule: Rule,
        val regex: Regex?,
    )

    private data class CompiledTracker(
        val tracker: TdsTracker,
        val rules: List<CompiledRule>,
    )

    private data class MatchedResult(
        val shouldBlock: Boolean,
        val isATracker: Boolean,
        val surrogate: String? = null,
    )
}
