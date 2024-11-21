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

package com.duckduckgo.brokensite.impl

import android.net.Uri
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.time.LocalDateTime
import javax.inject.Inject

interface BrokenSitePromptInMemoryStore {
    fun resetRefreshCount()
    fun addRefresh(url: Uri, localDateTime: LocalDateTime)
    fun getAndUpdateUserRefreshesBetween(
        t1: LocalDateTime,
        t2: LocalDateTime,
    ): Int
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrokenSitePromptInMemoryStore @Inject constructor() : BrokenSitePromptInMemoryStore {
    private var refreshes: LastRefreshedUrl? = null

    override fun resetRefreshCount() {
        this.refreshes = null
    }

    override fun addRefresh(
        url: Uri,
        localDateTime: LocalDateTime,
    ) {
        refreshes.let {
            refreshes = if (it == null || it.url != url) {
                LastRefreshedUrl(url, mutableListOf(localDateTime))
            } else {
                it.copy(time = it.time.plus(localDateTime))
            }
        }
    }

    override fun getAndUpdateUserRefreshesBetween(
        t1: LocalDateTime,
        t2: LocalDateTime,
    ): Int {
        return refreshes?.let {
            val time = it.time.filter { time -> time.isAfter(t1) && time.isBefore(t2) }
            refreshes = it.copy(time = time)
            time.size
        } ?: 0
    }
}

data class LastRefreshedUrl(
    val url: Uri,
    val time: List<LocalDateTime>,
)
