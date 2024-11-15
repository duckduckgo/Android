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

package com.duckduckgo.app.tabs.store

import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.app.tabs.store.TabStatsBucketing.Companion.ACTIVE_TABS_DAYS_LIMIT
import com.duckduckgo.app.tabs.store.TabStatsBucketing.Companion.BUCKETS
import com.duckduckgo.app.tabs.store.TabStatsBucketing.Companion.EXACTLY_1_BUCKET
import com.duckduckgo.app.tabs.store.TabStatsBucketing.Companion.MORE_THAN_20_BUCKET
import com.duckduckgo.app.tabs.store.TabStatsBucketing.Companion.ONE_WEEK_INACTIVE_LIMIT
import com.duckduckgo.app.tabs.store.TabStatsBucketing.Companion.THREE_WEEKS_INACTIVE_LIMIT
import com.duckduckgo.app.tabs.store.TabStatsBucketing.Companion.TWO_WEEKS_INACTIVE_LIMIT
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface TabStatsBucketing {
    suspend fun getTabCountBucket(): String
    suspend fun get7DaysActiveTabBucket(): String
    suspend fun get1WeeksInactiveTabBucket(): String
    suspend fun get2WeeksInactiveTabBucket(): String
    suspend fun get3WeeksInactiveTabBucket(): String

    companion object {
        const val ACTIVE_TABS_DAYS_LIMIT = 7L
        val ONE_WEEK_INACTIVE_LIMIT = 8L..14
        val TWO_WEEKS_INACTIVE_LIMIT = 15L..21
        const val THREE_WEEKS_INACTIVE_LIMIT = 22L

        const val EXACTLY_1_BUCKET = "1"
        const val MORE_THAN_20_BUCKET = ">20"

        val BUCKETS = listOf(
            0..1,
            2..5,
            6..10,
            11..20,
            21..40,
            41..60,
            61..80,
            81..100,
            101..125,
            126..150,
            151..250,
            251..500,
            501..Int.MAX_VALUE
        )
    }
}

@ContributesBinding(AppScope::class)
class DefaultTabStatsBucketing @Inject constructor(
    private val tabRepository: TabRepository
) : TabStatsBucketing {
    override suspend fun getTabCountBucket(): String {
        val count = tabRepository.getOpenTabCount()
        val bucket = BUCKETS.first { bucket ->
            count in bucket
        }
        return when (bucket) {
            BUCKETS.first() -> {
                EXACTLY_1_BUCKET
            }
            BUCKETS.last() -> {
                "${bucket.first}+"
            }
            else -> {
                "${bucket.first}-${bucket.last}"
            }
        }
    }

    override suspend fun get7DaysActiveTabBucket(): String {
        val count = tabRepository.getActiveTabCount(ACTIVE_TABS_DAYS_LIMIT)
        return getTabActivityBucket(count)
    }

    override suspend fun get1WeeksInactiveTabBucket(): String {
        val count = tabRepository.getInactiveTabCount(ONE_WEEK_INACTIVE_LIMIT.first, ONE_WEEK_INACTIVE_LIMIT.last)
        return getTabActivityBucket(count)
    }

    override suspend fun get2WeeksInactiveTabBucket(): String {
        val count = tabRepository.getInactiveTabCount(TWO_WEEKS_INACTIVE_LIMIT.first, TWO_WEEKS_INACTIVE_LIMIT.last)
        return getTabActivityBucket(count)
    }

    override suspend fun get3WeeksInactiveTabBucket(): String {
        val count = tabRepository.getInactiveTabCount(THREE_WEEKS_INACTIVE_LIMIT)
        return getTabActivityBucket(count)
    }

    private fun getTabActivityBucket(count: Int): String {
        val bucket = BUCKETS.first { bucket ->
            count in bucket
        }
        return when {
            bucket.last > 20 -> {
                MORE_THAN_20_BUCKET
            }
            else -> {
                "${bucket.first}-${bucket.last}"
            }
        }
    }
}
