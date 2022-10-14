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

import androidx.core.net.toUri
import com.duckduckgo.app.global.UriString.Companion.sameOrSubdomain
import com.duckduckgo.app.trackerdetection.model.Action.BLOCK
import com.duckduckgo.app.trackerdetection.model.Action.IGNORE
import com.duckduckgo.app.trackerdetection.model.RuleExceptions
import com.duckduckgo.app.trackerdetection.model.TdsTracker
import java.net.URI

class TdsClient(
    override val name: Client.ClientName,
    private val trackers: List<TdsTracker>
) : Client {

    override fun matches(
        url: String,
        documentUrl: String
    ): Client.Result {
        val cleanedUrl = removePortFromUrl(url)
        val tracker = trackers.firstOrNull { sameOrSubdomain(cleanedUrl, it.domain) } ?: return Client.Result(matches = false, isATracker = false)
        val matches = matchesTrackerEntry(tracker, cleanedUrl, documentUrl)
        return Client.Result(
            matches = matches.shouldBlock,
            entityName = tracker.ownerName,
            categories = tracker.categories,
            surrogate = matches.surrogate,
            isATracker = matches.isATracker
        )
    }

    private fun matchesTrackerEntry(
        tracker: TdsTracker,
        url: String,
        documentUrl: String
    ): MatchedResult {
        tracker.rules.forEach { rule ->
            val regex = ".*${rule.rule}.*".toRegex()
            if (url.matches(regex)) {
                if (matchedException(rule.exceptions, documentUrl)) {
                    return MatchedResult(shouldBlock = false, isATracker = true)
                }
                if (rule.action == IGNORE) {
                    return MatchedResult(shouldBlock = false, isATracker = true)
                }
                if (rule.surrogate?.isNotEmpty() == true) {
                    return MatchedResult(shouldBlock = true, surrogate = rule.surrogate, isATracker = true)
                }
                return MatchedResult(shouldBlock = true, isATracker = true)
            }
        }

        return MatchedResult(shouldBlock = (tracker.defaultAction == BLOCK), isATracker = true)
    }

    private fun matchedException(
        exceptions: RuleExceptions?,
        documentUrl: String
    ): Boolean {

        if (exceptions == null) return false

        val domains = exceptions.domains
        val types = exceptions.types

        // We don't support type filtering on android so if the types exist without a domain
        // we allow the exception through
        if (domains.isNullOrEmpty() && !types.isNullOrEmpty()) {
            return true
        }

        domains?.forEach {
            if (sameOrSubdomain(documentUrl, it)) {
                return true
            }
        }
        return false
    }

    private fun removePortFromUrl(url: String): String {
        return try {
            val uri = url.toUri()
            URI(uri.scheme, uri.host, uri.path, uri.fragment).toString()
        } catch (e: Exception) {
            url
        }
    }

    private data class MatchedResult(
        val shouldBlock: Boolean,
        val isATracker: Boolean,
        val surrogate: String? = null
    )
}
