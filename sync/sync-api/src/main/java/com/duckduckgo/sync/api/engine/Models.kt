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

/**
 * Represents a request to delete a deletable type
 * @param type The type of deletable data to delete.
 * @param untilTimestamp An optional timestamp to indicate that only items modified before this timestamp should be deleted.
 */
data class SyncDeletionRequest(
    val type: DeletableType,
    val untilTimestamp: String? = null,
)

/**
 * Represents a response to a sync deletion request.
 * @param type The type of deletable data that was deleted.
 * @param untilTimestamp The timestamp provided in @[SyncDeletionRequest] indicating the last modified timestamp up until which deletions should be performed.
 */
data class SyncDeletionResponse(
    val type: DeletableType,
    val untilTimestamp: String? = null,
)

data class SyncErrorResponse(
    val type: SyncFeatureType,
    val featureSyncError: FeatureSyncError,
)

enum class FeatureSyncError {
    COLLECTION_LIMIT_REACHED,
    INVALID_REQUEST,
}

/**
 * Common interface for all sync feature types.
 * Allows shared handling of features in error recording, pixels, etc.
 */
interface SyncFeatureType {
    /**
     * A name which uniquely identifies the feature; a definition shared across clients and backend.
     */
    val field: String
}

/**
 * Features that support bidirectional sync (PATCH/GET operations).
 */
enum class SyncableType(override val field: String) : SyncFeatureType {
    BOOKMARKS("bookmarks"),
    CREDENTIALS("credentials"),
    SETTINGS("settings"),
}

/**
 * Features that only support deletion (DELETE operations).
 */
enum class DeletableType(override val field: String) : SyncFeatureType {
    DUCK_AI_CHATS("ai_chats"),
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
