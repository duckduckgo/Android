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

package com.duckduckgo.sync.impl.engine

import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.duckduckgo.sync.impl.BookmarksResponse
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.impl.SyncDataResponse
import com.duckduckgo.sync.impl.parser.SyncDataRequest
import com.duckduckgo.sync.impl.parser.SyncRequest
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import timber.log.Timber

interface SyncApiClient {
    fun patch(changes: List<SyncChanges>): Result<List<SyncChanges>>
    fun get(since: String): Result<List<SyncChanges>>
}

@ContributesBinding(AppScope::class)
class AppSyncApiClient @Inject constructor(
    private val syncStore: SyncStore,
    private val syncApi: SyncApi,
) : SyncApiClient {
    override fun patch(changes: List<SyncChanges>): Result<List<SyncChanges>> {
        val token =
            syncStore.token.takeUnless { it.isNullOrEmpty() }
                ?: return Result.Error(reason = "Token Empty")

        if (changes.isEmpty()) {
            return Result.Error(reason = "Changes Empty")
        }

        val localChanges = mapRequest(changes)
        val localChangesJSON = Adapters.requestAdapter.toJson(localChanges)
        Timber.d("Sync: patch data generated $localChangesJSON")
        return when (val result = syncApi.patch(token, localChanges)) {
            is Result.Error -> {
                result
            }

            is Result.Success -> {
                if (result.data == null) {
                    Result.Success(emptyList())
                } else {
                    val remoteChanges = mapResponse(result.data)
                    Result.Success(remoteChanges)
                }
            }
        }
    }

    override fun get(since: String): Result<List<SyncChanges>> {
        val token =
            syncStore.token.takeUnless { it.isNullOrEmpty() }
                ?: return Result.Error(reason = "Token Empty")

        return when (val result = syncApi.getBookmarks(token, since)) {
            is Result.Error -> {
                if (result.code == 304){
                    // 304 - not modified means no changes to parse
                    Result.Success(emptyList())
                } else {
                    result
                }
            }

            is Result.Success -> {
                val remoteChanges = mapResponse(result.data)
                Result.Success(remoteChanges)
            }
        }
    }

    @VisibleForTesting
    fun mapRequest(changes: List<SyncChanges>): SyncDataRequest {
        val bookmarksJSON = changes.first { it.type == BOOKMARKS }.updatesJSON
        val bookmarkUpdates = Adapters.bookmarksRequestAdapter.fromJson(bookmarksJSON)!!
        return SyncDataRequest(client_timestamp = DatabaseDateFormatter.iso8601(), bookmarkUpdates.bookmarks)
    }

    @VisibleForTesting
    fun mapResponse(response: SyncDataResponse): List<SyncChanges> {
        val bookmarksJSON = Adapters.bookmarksResponseAdapter.toJson(response.bookmarks)
        Timber.d("Sync: responses mapped to $bookmarksJSON")
        return listOf(SyncChanges(BOOKMARKS, bookmarksJSON))
    }

    private fun mapBookmarksResponse(response: BookmarksResponse): List<SyncChanges> {
        val bookmarksJSON = Adapters.bookmarksResponseAdapter.toJson(response)
        Timber.d("Sync: bookmarks responses mapped to $bookmarksJSON")
        return listOf(SyncChanges(BOOKMARKS, bookmarksJSON))
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val bookmarksRequestAdapter: JsonAdapter<SyncRequest> =
                moshi.adapter(SyncRequest::class.java)

            val requestAdapter: JsonAdapter<SyncDataRequest> =
                moshi.adapter(SyncDataRequest::class.java)

            val bookmarksResponseAdapter: JsonAdapter<BookmarksResponse> =
                moshi.adapter(BookmarksResponse::class.java)

            val responseAdapter: JsonAdapter<SyncDataResponse> =
                moshi.adapter(SyncDataResponse::class.java)
        }
    }
}
