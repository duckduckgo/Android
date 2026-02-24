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
     * Returns the bucket name (map key), or null if no bucket matches.
     */
    fun bucketCount(count: Int, buckets: Map<String, BucketConfig>): String? {
        for ((name, bucket) in buckets) {
            if (count < bucket.gte) continue
            if (bucket.lt != null && count >= bucket.lt) continue
            return name
        }
        return null
    }

    /**
     * Returns true if no future counting can change the bucket outcome
     * (i.e., no bucket has a gte greater than the current count).
     */
    fun shouldStopCounting(count: Int, buckets: Map<String, BucketConfig>): Boolean {
        return !buckets.values.any { count < it.gte }
    }
}
