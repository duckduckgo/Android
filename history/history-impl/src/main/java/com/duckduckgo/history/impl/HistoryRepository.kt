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
import com.duckduckgo.history.impl.store.HistoryDataStore
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

    suspend fun clearHistory()

    suspend fun removeHistoryEntryByUrl(url: String)

    suspend fun removeHistoryEntryByQuery(query: String)

    suspend fun isHistoryUserEnabled(default: Boolean): Boolean

    suspend fun setHistoryUserEnabled(value: Boolean)

    suspend fun clearEntriesOlderThan(dateTime: LocalDateTime)

    suspend fun hasHistory(): Boolean
}

class RealHistoryRepository(
    private val historyDao: HistoryDao,
    private val dispatcherProvider: DispatcherProvider,
    private val appCoroutineScope: CoroutineScope,
    private val historyDataStore: HistoryDataStore,
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

    override suspend fun clearHistory() {
        withContext(dispatcherProvider.io()) {
            cachedHistoryEntries = null
            historyDao.deleteAll()
            fetchAndCacheHistoryEntries()
        }
    }

    override suspend fun removeHistoryEntryByUrl(url: String) {
        withContext(dispatcherProvider.io()) {
            historyDao.getHistoryEntryByUrl(url)?.let {
                cachedHistoryEntries = null
                historyDao.delete(it)
                fetchAndCacheHistoryEntries()
            }
        }
    }

    override suspend fun removeHistoryEntryByQuery(query: String) {
        withContext(dispatcherProvider.io()) {
            historyDao.getHistoryEntryByQuery(query)?.let {
                cachedHistoryEntries = null
                historyDao.delete(it)
                fetchAndCacheHistoryEntries()
            }
        }
    }

    override suspend fun isHistoryUserEnabled(default: Boolean): Boolean {
        return withContext(dispatcherProvider.io()) {
            historyDataStore.isHistoryUserEnabled(default)
        }
    }

    override suspend fun setHistoryUserEnabled(value: Boolean) {
        withContext(dispatcherProvider.io()) {
            historyDataStore.setHistoryUserEnabled(value)
        }
    }

    private suspend fun fetchAndCacheHistoryEntries(): List<HistoryEntry> {
        return historyDao
            .getHistoryEntriesWithVisits()
            .mapNotNull { it.toHistoryEntry() }
            .also { cachedHistoryEntries = it }
    }

    override suspend fun clearEntriesOlderThan(dateTime: LocalDateTime) {
        cachedHistoryEntries = null
        historyDao.deleteEntriesOlderThan(dateTime)
        fetchAndCacheHistoryEntries()
    }

    override suspend fun hasHistory(): Boolean {
        return withContext(dispatcherProvider.io()) {
            (cachedHistoryEntries ?: fetchAndCacheHistoryEntries()).let {
                it.isNotEmpty()
            }
        }
    }
}
