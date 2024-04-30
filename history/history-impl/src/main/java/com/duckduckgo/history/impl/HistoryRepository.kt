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

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.history.api.HistoryEntry
import com.duckduckgo.history.impl.store.HistoryDao
import io.reactivex.Single
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface HistoryRepository {
    fun getHistoryObservable(): Single<List<HistoryEntry>>

    suspend fun saveToHistory(
        url: String,
        title: String?,
        query: String?,
        isSerp: Boolean,
    )
}

class RealHistoryRepository(
    private val historyDao: HistoryDao,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope,
) : HistoryRepository {

    private var cachedHistoryEntries: List<HistoryEntry>? = null

    override fun getHistoryObservable(): Single<List<HistoryEntry>> {
        return if (cachedHistoryEntries != null) {
            Single.just(cachedHistoryEntries)
        } else {
            Single.create { emitter ->
                appCoroutineScope.launch(dispatcherProvider.io()) {
                    try {
                        emitter.onSuccess(fetchAndCacheHistoryEntries())
                    } catch (e: Exception) {
                        emitter.onError(e)
                    }
                }
            }
        }
    }

    override suspend fun saveToHistory(
        url: String,
        title: String?,
        query: String?,
        isSerp: Boolean,
    ) {
        withContext(dispatcherProvider.io()) {
            historyDao.updateOrInsertVisit(
                url,
                title ?: "",
                query,
                isSerp,
                LocalDateTime.now(),
            )
            fetchAndCacheHistoryEntries()
        }
    }

    private suspend fun fetchAndCacheHistoryEntries(): List<HistoryEntry> {
        return historyDao
            .getHistoryEntriesWithVisits()
            .mapNotNull { it.toHistoryEntry() }
            .also { cachedHistoryEntries = it }
    }
}
