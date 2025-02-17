/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.malicioussiteprotection.impl.models

import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.MALWARE
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection.Feed.PHISHING

data class Match(
    val hostname: String,
    val url: String,
    val regex: String,
    val hash: String,
    val feed: Feed,
)

data class Filter(
    val hash: String,
    val regex: String,
)

class FilterSet(
    val filters: List<Filter>,
    val feed: Feed,
)

sealed class FilterSetWithRevision(
    open val insert: Set<Filter>,
    open val delete: Set<Filter>,
    open val revision: Int,
    open val replace: Boolean,
    val feed: Feed,
    val type: Type = Type.FILTER_SET,
) {
    data class PhishingFilterSetWithRevision(
        override val insert: Set<Filter>,
        override val delete: Set<Filter>,
        override val revision: Int,
        override val replace: Boolean,
    ) : FilterSetWithRevision(insert, delete, revision, replace, PHISHING)

    data class MalwareFilterSetWithRevision(
        override val insert: Set<Filter>,
        override val delete: Set<Filter>,
        override val revision: Int,
        override val replace: Boolean,
    ) : FilterSetWithRevision(insert, delete, revision, replace, MALWARE)
}

sealed class HashPrefixesWithRevision(
    open val insert: Set<String>,
    open val delete: Set<String>,
    open val revision: Int,
    open val replace: Boolean,
    val feed: Feed,
    val type: Type = Type.HASH_PREFIXES,
) {
    data class PhishingHashPrefixesWithRevision(
        override val insert: Set<String>,
        override val delete: Set<String>,
        override val revision: Int,
        override val replace: Boolean,
    ) : HashPrefixesWithRevision(insert, delete, revision, replace, PHISHING)

    data class MalwareHashPrefixesWithRevision(
        override val insert: Set<String>,
        override val delete: Set<String>,
        override val revision: Int,
        override val replace: Boolean,
    ) : HashPrefixesWithRevision(insert, delete, revision, replace, MALWARE)
}

enum class Type {
    HASH_PREFIXES,
    FILTER_SET,
}
