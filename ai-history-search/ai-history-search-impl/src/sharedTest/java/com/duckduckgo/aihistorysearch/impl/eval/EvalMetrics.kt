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

package com.duckduckgo.aihistorysearch.impl.eval

import com.duckduckgo.history.api.HistoryEntry

/**
 * Retrieval quality metrics shared across all eval test variants (JVM, androidTest).
 *
 * Precision@K: fraction of the top-K results that are relevant.
 *   - For negative queries (empty relevant set): 1.0 if no results surface, 0.0 otherwise.
 *
 * MRR (Mean Reciprocal Rank): 1 / rank of the first relevant result.
 *   - 1.0 at rank 1, 0.5 at rank 2, 0.0 if no relevant result found.
 *   - For negative queries: always 1.0 (nothing relevant should surface).
 */

fun precisionAtK(ranked: List<HistoryEntry.VisitedPage>, relevant: Set<String>, k: Int): Double {
    if (relevant.isEmpty()) return if (ranked.take(k).isEmpty()) 1.0 else 0.0
    return ranked.take(k).count { it.url.toString() in relevant } / k.toDouble()
}

fun mrr(ranked: List<HistoryEntry.VisitedPage>, relevant: Set<String>): Double {
    if (relevant.isEmpty()) return 1.0
    val rank = ranked.indexOfFirst { it.url.toString() in relevant }
    return if (rank == -1) 0.0 else 1.0 / (rank + 1)
}
