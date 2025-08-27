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

import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS

sealed class ModifiedSince(open val value: String) {
    data object FirstSync : ModifiedSince(value = "0")
    data class Timestamp(override val value: String) : ModifiedSince(value)
}

// TODO: https://app.asana.com/0/0/1204958251694095/f
data class SyncChangesRequest(
    val type: SyncableType,
    val jsonString: String,
    val modifiedSince: ModifiedSince,
) {

    fun isEmpty(): Boolean {
        return this.jsonString.length == 0
    }

    fun isFirstSync(): Boolean {
        return this.modifiedSince is ModifiedSince.FirstSync
    }

    companion object {
        fun empty(): SyncChangesRequest {
            return SyncChangesRequest(BOOKMARKS, "", ModifiedSince.FirstSync)
        }
    }
}

data class SyncChangesResponse(
    val type: SyncableType,
    val jsonString: String,
) {
    fun isEmpty(): Boolean {
        return this.jsonString.length == 0
    }

    companion object {
        fun empty(type: SyncableType): SyncChangesResponse {
            return SyncChangesResponse(type, "")
        }
    }
}

data class SyncErrorResponse(
    val type: SyncableType,
    val featureSyncError: FeatureSyncError,
)

enum class FeatureSyncError {
    COLLECTION_LIMIT_REACHED,
    INVALID_REQUEST,
}

enum class SyncableType(val field: String) {
    BOOKMARKS("bookmarks"),
    CREDENTIALS("credentials"),
    SETTINGS("settings"),
}

// TODO: document api, when is it expected each case? https://app.asana.com/0/0/1204958251694095/f
sealed class SyncMergeResult {

    data class Success(
        val orphans: Boolean = false,
        val timestampConflict: Boolean = false,
    ) : SyncMergeResult()

    data class Error(
        val code: Int = -1,
        val reason: String,
    ) : SyncMergeResult()

    override fun toString(): String {
        return when (this) {
            is Success -> "Success[orphans=$orphans]"
            is Error -> "Error[exception=$code, $reason]"
        }
    }
}

sealed class SyncDataValidationResult<out R> {

    data class Success<out T>(val data: T) : SyncDataValidationResult<T>()

    data object NoChanges : SyncDataValidationResult<Nothing>()

    data class Error(
        val reason: String,
    ) : SyncDataValidationResult<Nothing>()

    override fun toString(): String {
        return when (this) {
            is Success<*> -> "Success[data=$data]"
            is Error -> "Error[reason= $reason]"
            is NoChanges -> "No Changes"
        }
    }
}
