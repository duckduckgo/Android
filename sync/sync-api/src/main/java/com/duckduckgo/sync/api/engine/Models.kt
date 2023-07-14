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

// TODO: document api
data class SyncChangesRequest(val type: SyncableType, val jsonString: String, val modifiedSince: String) {

    fun isEmpty(): Boolean {
        return this.jsonString.isEmpty()
    }
    companion object {
        fun empty(): SyncChangesRequest {
            return SyncChangesRequest(BOOKMARKS, "", "")
        }
    }
}

data class SyncChangesResponse(val type: SyncableType, val jsonString: String) {

    companion object {
        fun empty(): SyncChangesResponse {
            return SyncChangesResponse(BOOKMARKS, "")
        }
    }
}

// TODO: document api
enum class SyncableType(val field: String) {
    BOOKMARKS("bookmarks"),
    CREDENTIALS("credentials"),
}

// TODO: document api, when is it expected each case?
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
