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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

interface HistoryRepository {
    fun getHistory(): Flow<List<HistoryEntry>>

    suspend fun saveToHistory(
        url: String,
        title: String?,
        query: String?,
        isSerp: Boolean,
        tabId: String,
    )

    suspend fun removeHistoryForTab(tabId: String)

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
    private val historyDataStore: HistoryDataStore,
) : HistoryRepository {

    override fun getHistory(): Flow<List<HistoryEntry>> =
        historyDao.getHistoryEntriesWithVisitsFlow()
            .map { entries -> entries.mapNotNull { it.toHistoryEntry() } }
            .distinctUntilChanged()

    override suspend fun saveToHistory(
        url: String,
        title: String?,
        query: String?,
        isSerp: Boolean,
        tabId: String,
    ) {
        withContext(dispatcherProvider.io()) {
            historyDao.updateOrInsertVisit(
                url,
                title ?: "",
                query,
                isSerp,
                LocalDateTime.now(),
                tabId,
            )
        }
    }

    override suspend fun removeHistoryForTab(tabId: String) {
        withContext(dispatcherProvider.io()) {
            historyDao.deleteHistoryForTab(tabId)
        }
    }

    override suspend fun clearHistory() {
        withContext(dispatcherProvider.io()) {
            historyDao.deleteAll()
        }
    }

    override suspend fun removeHistoryEntryByUrl(url: String) {
        withContext(dispatcherProvider.io()) {
            historyDao.deleteEntriesByUrl(url)
        }
    }

    override suspend fun removeHistoryEntryByQuery(query: String) {
        withContext(dispatcherProvider.io()) {
            historyDao.deleteEntriesByQuery(query)
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

    override suspend fun clearEntriesOlderThan(dateTime: LocalDateTime) {
        withContext(dispatcherProvider.io()) {
            historyDao.deleteEntriesOlderThan(dateTime)
        }
    }

    override suspend fun hasHistory(): Boolean {
        return withContext(dispatcherProvider.io()) {
            historyDao.getHistoryEntriesWithVisits()
                .any { it.toHistoryEntry() != null }
        }
    }
}
