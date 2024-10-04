/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.autocomplete.api

import android.net.Uri
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutoCompleteScorer {
    fun score(
        title: String?,
        url: Uri,
        visitCount: Int,
        query: String,
        queryTokens: List<String>? = null,
    ): Int
}

@ContributesBinding(AppScope::class)
class RealAutoCompleteScorer @Inject constructor() : AutoCompleteScorer {
    override fun score(
        title: String?,
        url: Uri,
        visitCount: Int,
        query: String,
        queryTokens: List<String>?,
    ): Int {
        // To optimize, query tokens can be precomputed
        val tokens = queryTokens ?: query.tokensFrom()

        var score = DEFAULT_SCORE
        val lowercasedTitle = title?.lowercase() ?: ""
        val queryCount = query.count()
        val nakedUrl = url.naked()
        val domain = url.host?.removePrefix("www.") ?: ""

        // Full matches
        if (nakedUrl.startsWith(query)) {
            score += 300
            // Prioritize root URLs most
            if (url.isRoot()) score += 2000
        } else if (lowercasedTitle.startsWith(query, ignoreCase = true)) {
            score += 200
            if (url.isRoot()) score += 2000
        } else if (queryCount > 2 && domain.contains(query, ignoreCase = true)) {
            score += 150
            // Exact match from the beginning of the word within string.
        } else if (queryCount > 2 && lowercasedTitle.contains(" $query", ignoreCase = true)) {
            score += 100
        } else {
            // Tokenized matches
            if (tokens.size > 1) {
                var matchesAllTokens = true
                for (token in tokens) {
                    // Match only from the beginning of the word to avoid unintuitive matches.
                    if (
                        !lowercasedTitle.startsWith(token, ignoreCase = true) &&
                        !lowercasedTitle.contains(" $token", ignoreCase = true) &&
                        !nakedUrl.startsWith(token, ignoreCase = true)
                    ) {
                        matchesAllTokens = false
                        break
                    }
                }

                if (matchesAllTokens) {
                    // Score tokenized matches
                    score += 10

                    // Boost score if first token matches:
                    val firstToken = tokens.firstOrNull()
                    if (firstToken != null) { // nakedUrlString - high score boost
                        if (nakedUrl.startsWith(firstToken, ignoreCase = true)) {
                            score += 70
                        } else if (lowercasedTitle.startsWith(firstToken, ignoreCase = true)) { // beginning of the title - moderate score boost
                            score += 50
                        }
                    }
                }
            }
        }

        if (score > 0) {
            // Second sort based on visitCount
            score *= 1000
            score += visitCount
        }

        return score
    }

    private fun Uri.naked(): String {
        val host = host?.takeUnless { it.isEmpty() } ?: return toString().removePrefix("//")

        val builder = buildUpon()

        builder.scheme(null)
        builder.authority(host.removePrefix("www."))

        if (path?.lastOrNull() == '/') {
            builder.path(path!!.dropLast(1))
        }

        return builder.build().toString().removePrefix("//")
    }
}
