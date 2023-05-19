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

package com.duckduckgo.sync.api.engine

import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution.TIMESTAMP

interface SyncMerger {
    /**
     * Output of the [SyncEngine]
     * This is called for each feature with the changes that come from the server
     */
    fun merge(
        changes: SyncChanges,
        conflictResolution: SyncConflictResolution = TIMESTAMP,
    ): SyncMergeResult<Boolean>
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

sealed class SyncDataValidationResult<out R> {

    data class Success<out T>(val data: T) : SyncDataValidationResult<T>()
    data class Error(
        val reason: String,
    ) : SyncDataValidationResult<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[reason= $reason]"
        }
    }
}
