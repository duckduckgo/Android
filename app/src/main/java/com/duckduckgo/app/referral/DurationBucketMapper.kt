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

package com.duckduckgo.app.referral

class DurationBucketMapper {

    fun mapDurationToBucket(durationMs: Long): String {
        for ((durationThreshold, bucketName) in durationBuckets) {
            if (durationMs < durationThreshold) {
                return bucketName
            }
        }
        return DURATION_EXCEEDED_BUCKET_NAME
    }

    companion object {

        private val durationBuckets = listOf(
            Pair(100L, "0"),
            Pair(200L, "1"),
            Pair(500L, "2"),
            Pair(1_000L, "3"),
            Pair(1_500L, "4"),
            Pair(2_000L, "5"),
            Pair(2_500L, "6")
        )

        private const val DURATION_EXCEEDED_BUCKET_NAME = "7"
    }
}