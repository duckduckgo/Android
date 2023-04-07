/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.sync.api

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.di.scopes.AppScope

@ContributesPluginPoint(AppScope::class)
interface SyncablePlugin {

    /**
     * Used by the SyncClient to get all the updates from each syncable feature
     * since a specific time
     * This data that will be sent to the Sync API
     */
    fun getChanges(since: String): SyncChanges

    /**
     * Changes from Sync Client have been received
     * Each feature is responsible for merging and solving conflicts
     */
    fun syncChanges(
        changes: List<SyncChanges>,
        timestamp: String
    )
}

interface SyncEngine {

    /**
     * Entry point to the Sync Engine
     * This will be used by Background Sync and App Triggered workers
     */
    fun syncNow()

    /**
     * Entry point to the Sync Engine
     * This will be triggered by Observers when data has changed
     * Add / Update / Delete operations of [SyncableType]
     *
     */
    fun notifyDataChanged()
}

interface SyncMerger {
    fun merge(changes: SyncChanges): SyncMergeResult<Boolean>
}

sealed class SyncMergeResult<out R> {

    data class Success<out T>(val data: T) : SyncMergeResult<T>()
    data class Error(
        val code: Int = -1,
        val reason: String,
    ) : SyncMergeResult<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[exception=$code, $reason]"
        }
    }
}

interface SyncParser {
    fun parseChanges(since: String): SyncChanges
}

data class SyncChanges(
    val type: SyncableType,
    val updatesJSON: String
)

enum class SyncableType {
    BOOKMARKS
}
