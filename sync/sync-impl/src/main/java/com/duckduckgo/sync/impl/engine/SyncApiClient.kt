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
import com.duckduckgo.sync.impl.API_CODE
import com.duckduckgo.sync.impl.Result
import com.duckduckgo.sync.impl.SyncApi
import com.duckduckgo.sync.store.SyncStore
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import org.json.JSONObject
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

        // this should be a Flow when more data types come in
        val bookmarkChanges = changes.first()
        val updates = JSONObject(bookmarkChanges.updatesJSON)
        Timber.d("Sync-Feature: patch data generated $updates")
        return when (val result = syncApi.patch(token, updates)) {
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
                if (result.code == API_CODE.NOT_MODIFIED.code) {
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
    fun mapRequest(changes: List<SyncChanges>): JSONObject {
        val request = JSONObject()
        changes.forEach { feature ->
            request.put(feature.type.field, feature.updatesJSON)
        }
        request.put("client_timestamp", DatabaseDateFormatter.iso8601())
        return request
    }

    // TODO: this will need to be refactored once we receive more than one type
    @VisibleForTesting
    fun mapResponse(response: JSONObject): List<SyncChanges> {
        val bookmarksJSON = response.toString()
        Timber.d("Sync-Feature: responses mapped to $bookmarksJSON")
        return listOf(SyncChanges(BOOKMARKS, bookmarksJSON))
    }
}
