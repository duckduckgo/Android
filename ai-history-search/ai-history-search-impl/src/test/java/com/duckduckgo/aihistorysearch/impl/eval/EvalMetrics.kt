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
 * Precision@K: fraction of the top-K results that are relevant.
 *
 * For negative queries (empty [relevant] set) the score is 1.0 iff the ranked list returns
 * nothing in the top K — i.e., the ranker correctly abstains rather than surfacing false positives.
 */
fun precisionAtK(ranked: List<HistoryEntry.VisitedPage>, relevant: Set<String>, k: Int): Double {
    if (relevant.isEmpty()) return if (ranked.take(k).isEmpty()) 1.0 else 0.0
    return ranked.take(k).count { it.url.toString() in relevant } / k.toDouble()
}

/**
 * Mean Reciprocal Rank: 1 / rank of the first relevant result (0 if none found).
 *
 * For negative queries (empty [relevant] set) the score is vacuously 1.0 — the ranker cannot
 * be penalised for failing to surface a result that does not exist.
 */
fun mrr(ranked: List<HistoryEntry.VisitedPage>, relevant: Set<String>): Double {
    if (relevant.isEmpty()) return 1.0
    val rank = ranked.indexOfFirst { it.url.toString() in relevant }
    return if (rank == -1) 0.0 else 1.0 / (rank + 1)
}
