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

package com.duckduckgo.webtelemetry.impl

/**
 * Maps a raw count into its appropriate bucket string.
 *
 * Bucket formats:
 * - "M" — exactly M
 * - "M-N" — at least M and at most N (inclusive)
 * - "M+" — M or more
 *
 * Returns the first matching bucket, or null if no bucket matches.
 */
object BucketCounter {

    private val EXACT_PATTERN = Regex("^(\\d+)$")
    private val RANGE_PATTERN = Regex("^(\\d+)-(\\d+)$")
    private val OPEN_ENDED_PATTERN = Regex("^(\\d+)\\+$")

    fun bucketCount(count: Int, buckets: List<String>): String? {
        for (bucket in buckets) {
            EXACT_PATTERN.matchEntire(bucket)?.let { match ->
                val value = match.groupValues[1].toInt()
                if (count == value) return bucket
            }
            RANGE_PATTERN.matchEntire(bucket)?.let { match ->
                val lower = match.groupValues[1].toInt()
                val upper = match.groupValues[2].toInt()
                if (count in lower..upper) return bucket
            }
            OPEN_ENDED_PATTERN.matchEntire(bucket)?.let { match ->
                val value = match.groupValues[1].toInt()
                if (count >= value) return bucket
            }
        }
        return null
    }
}
