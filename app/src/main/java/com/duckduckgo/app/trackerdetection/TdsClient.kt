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
import com.duckduckgo.app.trackerdetection.model.TdsTracker

class TdsClient(
    override val name: Client.ClientName,
    private val trackers: List<TdsTracker>,
    private val urlToTypeMapper: UrlToTypeMapper,
    private val optimizeTrackerEvaluation: Boolean,
) : Client {

    override fun matches(
        url: String,
        documentUrl: Uri,
        requestHeaders: Map<String, String>,
    ): Client.Result {
        val tracker = if (optimizeTrackerEvaluation) {
            val domain = host(url)?.let { Domain(it) }
            trackers.firstOrNull {
                domain?.let { domain -> sameOrSubdomain(domain, it.domain) } ?: false
            } ?: return Client.Result(matches = false, isATracker = false)
        } else {
            trackers.firstOrNull { sameOrSubdomain(url, it.domain.value) } ?: return Client.Result(matches = false, isATracker = false)
        }
        val matches = matchesTrackerEntry(tracker, url, documentUrl, requestHeaders)
        return Client.Result(
            matches = matches.shouldBlock,
            entityName = tracker.ownerName,
            categories = tracker.categories,
            surrogate = matches.surrogate,
            isATracker = matches.isATracker,
        )
    }

    override fun matches(
        url: Uri,
        documentUrl: Uri,
        requestHeaders: Map<String, String>,
    ): Client.Result {
        val tracker = if (optimizeTrackerEvaluation) {
            val domain = url.host?.let { Domain(it) }
            trackers.firstOrNull { sameOrSubdomain(domain, it.domain) } ?: return Client.Result(matches = false, isATracker = false)
        } else {
            trackers.firstOrNull { sameOrSubdomain(url, it.domain.value) } ?: return Client.Result(matches = false, isATracker = false)
        }
        val matches = matchesTrackerEntry(tracker, url.toString(), documentUrl, requestHeaders)
        return Client.Result(
            matches = matches.shouldBlock,
            entityName = tracker.ownerName,
            categories = tracker.categories,
            surrogate = matches.surrogate,
            isATracker = matches.isATracker,
        )
    }

    private fun matchesTrackerEntry(
        tracker: TdsTracker,
        url: String,
        documentUrl: Uri,
        requestHeaders: Map<String, String>,
    ): MatchedResult {
        tracker.rules.forEach { rule ->
            val regex = ".*${rule.rule}.*".toRegex()
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

        return MatchedResult(shouldBlock = (tracker.defaultAction == BLOCK), isATracker = true)
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

    private data class MatchedResult(
        val shouldBlock: Boolean,
        val isATracker: Boolean,
        val surrogate: String? = null,
    )
}
