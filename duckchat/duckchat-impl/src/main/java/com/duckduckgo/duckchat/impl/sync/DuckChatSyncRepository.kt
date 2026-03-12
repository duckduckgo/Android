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

package com.duckduckgo.duckchat.impl.sync

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

/**
 * Repository interface for managing Duck AI chat sync metadata.
 */
interface DuckChatSyncRepository {

    /**
     * Records that Duck AI chats were deleted at the current timestamp.
     * @param timestampMillis Effective timestamp when deletion happened.
     */
    suspend fun recordDuckAiChatsDeleted(timestampMillis: Long)

    /**
     * Returns the timestamp of the last local Duck AI chat deletion, or null if none recorded.
     */
    suspend fun getLastDuckAiChatDeletionTimestamp(): String?

    /**
     * Clears the deletion timestamp only if it matches the provided timestamp.
     */
    suspend fun clearDeletionTimestampIfMatches(timestamp: String)
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckChatSyncRepository @Inject constructor(
    private val duckChatSyncMetadataStore: DuckChatSyncMetadataStore,
    private val dispatchers: DispatcherProvider,
) : DuckChatSyncRepository {

    override suspend fun recordDuckAiChatsDeleted(timestampMillis: Long) {
        withContext(dispatchers.io()) {
            val isoTimestamp = DatabaseDateFormatter.parseMillisIso8601(timestampMillis)
            duckChatSyncMetadataStore.deletionTimestamp = isoTimestamp
            logcat { "DuckChat-Sync: recorded deletion timestamp $isoTimestamp which was ${System.currentTimeMillis() - timestampMillis}ms ago" }
        }
    }

    override suspend fun getLastDuckAiChatDeletionTimestamp(): String? {
        return withContext(dispatchers.io()) {
            duckChatSyncMetadataStore.deletionTimestamp
        }
    }

    // Get current timestamp and only clear if it matches given param
    override suspend fun clearDeletionTimestampIfMatches(timestamp: String) {
        withContext(dispatchers.io()) {
            val currentTimestamp = getLastDuckAiChatDeletionTimestamp()
            if (currentTimestamp == timestamp) {
                logcat { "DuckChat-Sync: cleared local deletion timestamp $timestamp" }
                duckChatSyncMetadataStore.deletionTimestamp = null
            }
        }
    }
}
