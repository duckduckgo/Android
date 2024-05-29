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

import android.net.Uri
import com.duckduckgo.app.browser.DuckDuckGoUrlDetector
import com.duckduckgo.common.utils.AppUrl
import com.duckduckgo.common.utils.AppUrl.Url
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.UrlScheme
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.history.impl.remoteconfig.HistoryFeature
import com.squareup.anvil.annotations.ContributesBinding
import io.reactivex.Single
import javax.inject.Inject

interface InternalNavigationHistory : NavigationHistory {
    suspend fun clearOldEntries()
}

@ContributesBinding(AppScope::class, boundType = NavigationHistory::class)
@ContributesBinding(AppScope::class, boundType = InternalNavigationHistory::class)
class RealNavigationHistory @Inject constructor(
    private val historyRepository: HistoryRepository,
    private val duckDuckGoUrlDetector: DuckDuckGoUrlDetector,
    private val currentTimeProvider: CurrentTimeProvider,
    private val historyFeature: HistoryFeature,
) : InternalNavigationHistory {
    override suspend fun saveToHistory(
        url: String,
        title: String?,
    ) {
        if (!historyFeature.shouldStoreHistory || !isHistoryUserEnabled()) {
            return
        }
        val ddgUrl = duckDuckGoUrlDetector.isDuckDuckGoQueryUrl(url)
        val query = if (ddgUrl) duckDuckGoUrlDetector.extractQuery(url) else null
        val sanitizedUrl = if (ddgUrl) {
            Uri.Builder()
                .scheme(UrlScheme.https)
                .appendQueryParameter(AppUrl.ParamKey.QUERY, query)
                .authority(Url.HOST).toString()
        } else {
            url
        }

        historyRepository.saveToHistory(sanitizedUrl, title, query, query != null)
    }

    override fun getHistorySingle(): Single<List<HistoryEntry>> {
        return if (isHistoryFeatureAvailable() && isHistoryUserEnabled()) historyRepository.getHistoryObservable() else Single.just(emptyList())
    }

    override suspend fun clearHistory() {
        historyRepository.clearHistory()
    }

    override suspend fun clearOldEntries() {
        historyRepository.clearEntriesOlderThan(currentTimeProvider.localDateTimeNow().minusDays(30))
    }

    override fun isHistoryUserEnabled(): Boolean {
        return historyRepository.isHistoryUserEnabled(historyFeature.shouldStoreHistory)
    }

    override fun setHistoryUserEnabled(value: Boolean) {
        historyRepository.setHistoryUserEnabled(value)
    }

    override fun isHistoryFeatureAvailable(): Boolean {
        return historyFeature.shouldStoreHistory
    }

    override suspend fun hasHistory(): Boolean {
        return historyRepository.hasHistory()
    }
}
