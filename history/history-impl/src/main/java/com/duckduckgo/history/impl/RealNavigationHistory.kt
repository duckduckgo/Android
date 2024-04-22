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

package com.duckduckgo.history.impl

import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.NavigationHistory
import com.squareup.anvil.annotations.ContributesBinding
import io.reactivex.Single
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class RealNavigationHistory @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
) : NavigationHistory {
    override suspend fun saveToHistory(
        url: String,
        title: String?,
    ) {
        val ddgUrl = duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)
        val query = if (ddgUrl) duckDuckGoUrlDetector.extractQuery(url) else null

        historyRepository.saveToHistory(url, title, query, query != null)
    }

    override fun getHistorySingle(): Single<List<HistoryEntry>> {
        return historyRepository.getHistoryObservable()
    }

    override suspend fun clearHistory() {
        historyRepository.clearHistory()
    }
}
