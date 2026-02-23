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

package com.duckduckgo.webevents.impl

object BucketCounter {

    /**
     * Find the first bucket matching the given count.
     * Returns the bucket's [BucketConfig.name], or null if no bucket matches.
     */
    fun bucketCount(count: Int, buckets: List<BucketConfig>): String? {
        for (bucket in buckets) {
            if (count >= bucket.minInclusive) {
                if (bucket.maxExclusive == null || count < bucket.maxExclusive) {
                    return bucket.name
                }
            }
        }
        return null
    }

    /**
     * Returns true if no future counting can change the bucket outcome
     * (i.e., no bucket has a minInclusive greater than the current count).
     */
    fun shouldStopCounting(count: Int, buckets: List<BucketConfig>): Boolean {
        return !buckets.any { count < it.minInclusive }
    }
}
