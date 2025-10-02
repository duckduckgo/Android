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

package com.duckduckgo.app.statistics.user_segments

import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.statistics.user_segments.RealUsageHistory.SegmentKey.KEY_APP_USE_DATES
import com.duckduckgo.app.statistics.user_segments.RealUsageHistory.SegmentKey.KEY_SEARCH_DATES
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.data.store.api.SharedPreferencesProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext

interface UsageHistory {
    /**
     * Call this method to add a new SEARCH ATB to the search usage history
     * @param atb the new set ATB to add to the SEARCH history
     */
    suspend fun addSearchUsage(atb: String)

    /**
     * Get the history of SEARCH set ATBs
     * @return the SEARCH ATB history
     */
    suspend fun getSearchUsageHistory(): List<String>

    /**
     * Call this method to add a new APP_USE ATB to the search usage history
     * @param atb the new set ATB to add to the APP_USE history
     */
    suspend fun addAppUsage(atb: String)

    /**
     * Get the history of APP_USE set ATBs
     * @return the APP_USE ATB history
     */
    suspend fun getAppUsageHistory(): List<String>
}

private class RealUsageHistory(
    private val sharedPreferencesProvider: SharedPreferencesProvider,
    private val dispatcherProvider: DispatcherProvider,
) : UsageHistory {

    private val preferences: SharedPreferences by lazy { sharedPreferencesProvider.getSharedPreferences(RETENTION_SEGMENTS_PREF_FILE) }

    private suspend fun addUsageInternal(atb: String, key: SegmentKey) = withContext(dispatcherProvider.io()) {
        val segments = LinkedHashSet(getOrderedUsageHistoryInternal(key)).run {
            add(atb)
            joinToString(",")
        }
        preferences.edit { putString(key.name, segments) }
    }

    private suspend fun getOrderedUsageHistoryInternal(key: SegmentKey): List<String> = withContext(dispatcherProvider.io()) {
        val sortedCommaSeparatedSegments = preferences.getString(key.name, "")
        return@withContext if (sortedCommaSeparatedSegments.isNullOrEmpty()) {
            emptyList()
        } else {
            sortedCommaSeparatedSegments.split(",")
        }
    }

    override suspend fun addSearchUsage(atb: String) {
        addUsageInternal(atb, KEY_SEARCH_DATES)
    }

    override suspend fun getSearchUsageHistory(): List<String> {
        return getOrderedUsageHistoryInternal(KEY_SEARCH_DATES)
    }

    override suspend fun addAppUsage(atb: String) {
        addUsageInternal(atb, KEY_APP_USE_DATES)
    }

    override suspend fun getAppUsageHistory(): List<String> {
        return getOrderedUsageHistoryInternal(KEY_APP_USE_DATES)
    }

    companion object {
        private const val RETENTION_SEGMENTS_PREF_FILE = "com.duckduckgo.mobile.android.retention.usage.history"
    }

    private enum class SegmentKey(name: String) {
        KEY_SEARCH_DATES("search.usage"),
        KEY_APP_USE_DATES("app_use.usage"),
    }
}

@ContributesTo(AppScope::class)
@Module
class SegmentStoreModule {
    @Provides
    @SingleInstanceIn(AppScope::class)
    fun provideSegmentStore(
        sharedPreferencesProvider: SharedPreferencesProvider,
        dispatcherProvider: DispatcherProvider,
    ): UsageHistory {
        return RealUsageHistory(sharedPreferencesProvider, dispatcherProvider)
    }
}
